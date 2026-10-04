package com.airtribe.prism.gateway.domain;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * One row per request: the audit trail the spec requires ("Log every request - key, model,
 * provider, status, tokens, cost, cache result, fallback flag, latency"). Written asynchronously
 * (see UsageLoggingService) so persistence never sits on the client-facing latency path.
 */
@Entity
@Table(name = "request_logs", indexes = {
        // Usage-summary queries are always "give me this key's activity in a time range" ->
        // a composite index on (virtual_key_id, created_at) turns that into an index range scan
        // instead of a full table scan, which matters once this table has millions of rows.
        @Index(name = "idx_request_logs_key_created", columnList = "virtual_key_id, created_at")
})
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RequestLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "virtual_key_id", nullable = false)
    private Long virtualKeyId;

    @Column(name = "requested_model", nullable = false, length = 64)
    private String requestedModel;

    @Column(name = "resolved_model", length = 64)
    private String resolvedModel;

    @Column(name = "provider", length = 64)
    private String provider;

    @Column(name = "status", nullable = false, length = 32)
    private String status; // SUCCESS | ERROR:<ERROR_CODE>

    @Column(name = "prompt_tokens")
    private Integer promptTokens;

    @Column(name = "completion_tokens")
    private Integer completionTokens;

    @Column(name = "cost_usd", precision = 12, scale = 6)
    private BigDecimal costUsd;

    @Column(name = "cache_hit", nullable = false)
    private boolean cacheHit;

    @Column(name = "fallback_used", nullable = false)
    private boolean fallbackUsed;

    @Column(name = "latency_ms")
    private long latencyMs;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
}
