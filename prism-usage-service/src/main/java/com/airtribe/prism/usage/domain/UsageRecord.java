package com.airtribe.prism.usage.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "usage_records", indexes = {
        @Index(name = "idx_usage_records_key_created", columnList = "virtual_key_id, created_at"),
        @Index(name = "idx_usage_records_provider_created", columnList = "provider, created_at")
})
@Getter
@Setter
@NoArgsConstructor
public class UsageRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "virtual_key_id", nullable = false)
    private Long virtualKeyId;

    @Column(name = "requested_model", length = 64)
    private String requestedModel;

    @Column(name = "resolved_model", length = 64)
    private String resolvedModel;

    @Column(name = "provider", length = 64)
    private String provider;

    @Column(name = "status", length = 32)
    private String status;

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
    private Long latencyMs;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
}
