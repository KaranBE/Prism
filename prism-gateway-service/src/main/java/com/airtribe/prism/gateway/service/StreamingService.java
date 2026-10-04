package com.airtribe.prism.gateway.service;

import com.airtribe.prism.common.dto.ChatCompletionChunk;
import com.airtribe.prism.common.dto.ChatCompletionRequest;
import com.airtribe.prism.common.dto.Usage;
import com.airtribe.prism.gateway.domain.RequestLog;
import com.airtribe.prism.gateway.domain.VirtualKey;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Bridges the reactive Flux<StreamEvent> produced by ProviderDispatchService onto a Spring MVC
 * SseEmitter: tokens flow from provider -> gateway -> client as they arrive (no buffering the
 * full completion before responding), and the stream terminates with a standard
 * "data: [DONE]" event once every token has been forwarded, per the header/stream contract.
 * A final "prism-meta" event (JSON) carries the authoritative provider/fallback/cache/cost
 * values, since with SSE those can only be known for certain once the stream completes - see
 * README "Known limitations" for why this is in-band rather than an HTTP trailer.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class StreamingService {

    private final ProviderDispatchService dispatchService;
    private final RequestPipelineService pipelineService;
    private final UsageLoggingService usageLoggingService;
    private final CacheClientService cacheClientService;
    private final ObjectMapper objectMapper;

    public SseEmitter stream(VirtualKey key, ChatCompletionRequest request, RoutingPlan plan,
                              BigDecimal estimatedCost, String promptText, long startNanos) {
        SseEmitter emitter = new SseEmitter(60_000L);
        String id = "chatcmpl-" + java.util.UUID.randomUUID();
        StringBuilder contentBuilder = new StringBuilder();
        AtomicReference<String> lastModel = new AtomicReference<>(plan.primaryModel());

        dispatchService.dispatchStreaming(plan, request).subscribe(
                event -> {
                    try {
                        if (!event.last()) {
                            contentBuilder.append(event.token());
                            ChatCompletionChunk chunk = ChatCompletionChunk.token(id, lastModel.get(), event.token());
                            emitter.send(SseEmitter.event().data(objectMapper.writeValueAsString(chunk)));
                        } else {
                            var result = event.finalResultIfLast();
                            lastModel.set(result.resolvedModel());
                            Usage usage = result.usage();
                            BigDecimal actualCost = pipelineService.trueUpToActual(key, result.resolvedModel(), estimatedCost, usage);

                            Map<String, Object> meta = Map.of(
                                    "provider", result.provider(),
                                    "cache", "MISS",
                                    "fallback", result.fallbackUsed(),
                                    "costUsd", actualCost.toPlainString());
                            emitter.send(SseEmitter.event().name("prism-meta").data(objectMapper.writeValueAsString(meta)));
                            emitter.send(SseEmitter.event().data(objectMapper.writeValueAsString(
                                    ChatCompletionChunk.done(id, result.resolvedModel()))));
                            emitter.send(SseEmitter.event().data("[DONE]"));
                            emitter.complete();

                            cacheClientService.storeAsync(key.getAlias(), result.resolvedModel(), promptText,
                                    contentBuilder.toString(), usage);
                            usageLoggingService.enqueue(RequestLog.builder()
                                    .virtualKeyId(key.getId())
                                    .requestedModel(request.model())
                                    .resolvedModel(result.resolvedModel())
                                    .provider(result.provider())
                                    .status("SUCCESS")
                                    .promptTokens(usage.promptTokens())
                                    .completionTokens(usage.completionTokens())
                                    .costUsd(actualCost)
                                    .cacheHit(false)
                                    .fallbackUsed(result.fallbackUsed())
                                    .latencyMs((System.nanoTime() - startNanos) / 1_000_000)
                                    .createdAt(Instant.now())
                                    .build());
                        }
                    } catch (Exception e) {
                        log.error("Error writing SSE event", e);
                        emitter.completeWithError(e);
                    }
                },
                error -> {
                    log.warn("Streaming dispatch failed: {}", error.toString());
                    pipelineService.refund(key, estimatedCost);
                    usageLoggingService.enqueue(pipelineService.logBuilder(key, request, plan)
                            .status("ERROR:" + error.getClass().getSimpleName())
                            .cacheHit(false).fallbackUsed(true)
                            .latencyMs((System.nanoTime() - startNanos) / 1_000_000)
                            .build());
                    emitter.completeWithError(error);
                }
        );
        return emitter;
    }
}
