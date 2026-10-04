package com.airtribe.prism.gateway.service;

import com.airtribe.prism.common.dto.Usage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.Map;

/**
 * Client for prism-cache-service, the separate microservice that owns semantic-cache lookups
 * (embedding + nearest-neighbour search) so that logic - and its own scaling/storage profile -
 * is decoupled from the gateway's request-handling hot path.
 *
 * Deliberately fails OPEN: any error or timeout talking to the cache service is treated as a
 * cache miss rather than a failed request, because a working answer served without a cache hit
 * is far preferable to the whole gateway going down because a side-car cache service hiccuped.
 */
@Slf4j
@Service
public class CacheClientService {

    private final WebClient cacheServiceWebClient;

    public CacheClientService(@Qualifier("cacheServiceWebClient") WebClient cacheServiceWebClient) {
        this.cacheServiceWebClient = cacheServiceWebClient;
    }

    public record LookupRequestBody(String tenantKey, String model, String prompt) {}
    public record StoreRequestBody(String tenantKey, String model, String prompt, String content, int promptTokens, int completionTokens) {}

    public Mono<CacheLookupResult> lookup(String tenantKey, String model, String prompt) {
        return cacheServiceWebClient.post()
                .uri("/cache/lookup")
                .bodyValue(new LookupRequestBody(tenantKey, model, prompt))
                .retrieve()
                .bodyToMono(Map.class)
                .map(this::toResult)
                .onErrorResume(ex -> {
                    log.warn("Semantic cache lookup failed open (treated as miss): {}", ex.toString());
                    return Mono.just(CacheLookupResult.MISS);
                });
    }

    public void storeAsync(String tenantKey, String model, String prompt, String content, Usage usage) {
        cacheServiceWebClient.post()
                .uri("/cache/store")
                .bodyValue(new StoreRequestBody(tenantKey, model, prompt, content, usage.promptTokens(), usage.completionTokens()))
                .retrieve()
                .bodyToMono(Void.class)
                .onErrorResume(ex -> {
                    log.warn("Semantic cache store failed (non-fatal): {}", ex.toString());
                    return Mono.empty();
                })
                .subscribe();
    }

    @SuppressWarnings("unchecked")
    private CacheLookupResult toResult(Map<String, Object> body) {
        boolean hit = Boolean.TRUE.equals(body.get("hit"));
        if (!hit) {
            return CacheLookupResult.MISS;
        }
        Map<String, Object> usageMap = (Map<String, Object>) body.get("usage");
        Usage usage = Usage.of(((Number) usageMap.get("promptTokens")).intValue(),
                ((Number) usageMap.get("completionTokens")).intValue());
        double similarity = ((Number) body.getOrDefault("similarity", 0.0)).doubleValue();
        return new CacheLookupResult(true, (String) body.get("content"), usage, similarity);
    }
}
