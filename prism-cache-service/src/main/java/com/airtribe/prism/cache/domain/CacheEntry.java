package com.airtribe.prism.cache.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/**
 * A durable semantic-cache row. Cache scoping is per tenant ("scoped per key" in the spec) via
 * tenant_key - every lookup and every index bucket is filtered/partitioned by this column so
 * one tenant can never see another tenant's cached responses.
 */
@Entity
@Table(name = "cache_entries", indexes = {
        // Every lookup and every LSH-bucket rebuild scans "this tenant's entries for this
        // model" - a composite index keeps both index-load-on-boot and eviction queries fast.
        @Index(name = "idx_cache_entries_tenant_model", columnList = "tenant_key, model")
})
@Getter
@Setter
@NoArgsConstructor
public class CacheEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_key", nullable = false, length = 64)
    private String tenantKey;

    @Column(name = "model", nullable = false, length = 64)
    private String model;

    @Column(name = "prompt_text", nullable = false, columnDefinition = "TEXT")
    private String promptText;

    /** Serialized embedding vector, comma-separated doubles - kept simple/portable rather than
     *  a vector-DB-specific column type, since the LSH index is rebuilt from this at boot. */
    @Column(name = "embedding", nullable = false, columnDefinition = "TEXT")
    private String embeddingCsv;

    @Column(name = "content", nullable = false, columnDefinition = "TEXT")
    private String content;

    @Column(name = "prompt_tokens", nullable = false)
    private int promptTokens;

    @Column(name = "completion_tokens", nullable = false)
    private int completionTokens;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;
}
