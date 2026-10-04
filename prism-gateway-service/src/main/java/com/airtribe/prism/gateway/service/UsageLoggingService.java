package com.airtribe.prism.gateway.service;

import com.airtribe.prism.gateway.domain.RequestLog;
import com.airtribe.prism.gateway.repository.RequestLogRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * Producer/consumer usage-logging pipeline: request-handling threads never touch the database
 * directly. They call {@link #enqueue}, an O(1), non-blocking queue offer, and return
 * immediately - persistence happens on a separate scheduled consumer that drains the queue in
 * batches and issues one {@code saveAll} per tick instead of one INSERT per request. This is
 * the standard fix for "usage logging" becoming the bottleneck under load: batching amortises
 * transaction/round-trip overhead across many rows, and the bounded queue (5,000 capacity)
 * gives back-pressure - if the consumer ever falls behind, {@code offer} starts returning
 * false and is logged rather than blocking the hot request path or growing without limit.
 */
@Slf4j
@Service
public class UsageLoggingService {

    private static final int MAX_BATCH_SIZE = 500;

    private final RequestLogRepository requestLogRepository;
    private final WebClient usageServiceWebClient;
    private final BlockingQueue<RequestLog> queue = new LinkedBlockingQueue<>(5_000);

    public UsageLoggingService(RequestLogRepository requestLogRepository,
                               @Qualifier("usageServiceWebClient") WebClient usageServiceWebClient) {
        this.requestLogRepository = requestLogRepository;
        this.usageServiceWebClient = usageServiceWebClient;
    }

    public void enqueue(RequestLog requestLog) {
        boolean accepted = queue.offer(requestLog);
        if (!accepted) {
            log.warn("Usage log queue full - dropping one request log (key={}, model={})",
                    requestLog.getVirtualKeyId(), requestLog.getRequestedModel());
        }
    }

    @Scheduled(fixedDelay = 200)
    public void flush() {
        List<RequestLog> batch = new ArrayList<>(MAX_BATCH_SIZE);
        queue.drainTo(batch, MAX_BATCH_SIZE);
        if (batch.isEmpty()) {
            return;
        }
        try {
            requestLogRepository.saveAll(batch);
        } catch (Exception ex) {
            log.error("Failed to persist a batch of {} request logs", batch.size(), ex);
        }
        forwardToUsageService(batch);
    }

    private void forwardToUsageService(List<RequestLog> batch) {
        List<UsageEvent> events = batch.stream().map(UsageEvent::from).toList();
        usageServiceWebClient.post()
                .uri("/usage/events")
                .bodyValue(events)
                .retrieve()
                .bodyToMono(Void.class)
                .onErrorResume(ex -> {
                    log.warn("Failed to forward {} usage events to prism-usage-service (non-fatal): {}",
                            events.size(), ex.toString());
                    return reactor.core.publisher.Mono.empty();
                })
                .subscribe();
    }

    public record UsageEvent(Long virtualKeyId, String requestedModel, String resolvedModel, String provider,
                              String status, Integer promptTokens, Integer completionTokens, String costUsd,
                              boolean cacheHit, boolean fallbackUsed, long latencyMs, String createdAt) {
        static UsageEvent from(RequestLog r) {
            return new UsageEvent(r.getVirtualKeyId(), r.getRequestedModel(), r.getResolvedModel(), r.getProvider(),
                    r.getStatus(), r.getPromptTokens(), r.getCompletionTokens(),
                    r.getCostUsd() == null ? "0" : r.getCostUsd().toPlainString(),
                    r.isCacheHit(), r.isFallbackUsed(), r.getLatencyMs(), r.getCreatedAt().toString());
        }
    }
}
