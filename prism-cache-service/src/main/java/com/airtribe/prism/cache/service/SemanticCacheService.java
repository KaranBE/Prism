package com.airtribe.prism.cache.service;

import com.airtribe.prism.cache.domain.CacheEntry;
import com.airtribe.prism.cache.dto.CacheDtos;
import com.airtribe.prism.cache.repository.CacheEntryRepository;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

/**
 * Orchestrates the semantic cache: embed -> LSH candidate lookup -> cosine-similarity
 * confirmation -> (on miss) persist + index. Postgres is the durable source of truth (survives
 * restarts, "reliable persistent store" per the spec); the LSH index in {@link LshVectorIndex}
 * is an in-memory acceleration structure rebuilt from Postgres at boot and kept in sync on
 * every write, so a lookup never has to hit the database on the hot path.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SemanticCacheService {

    private final EmbeddingService embeddingService;
    private final LshVectorIndex index;
    private final CacheEntryRepository repository;

    @Value("${prism.cache.similarity-threshold:0.70}")
    private double similarityThreshold;

    @Value("${prism.cache.ttl-seconds:3600}")
    private long ttlSeconds;

    @PostConstruct
    void rebuildIndexFromDatabase() {
        Instant now = Instant.now();
        List<CacheEntry> live = repository.findByExpiresAtAfter(now);
        for (CacheEntry entry : live) {
            index.add(entry.getId(), entry.getTenantKey(), entry.getModel(), parseCsv(entry.getEmbeddingCsv()),
                    entry.getContent(), entry.getPromptTokens(), entry.getCompletionTokens(), entry.getExpiresAt());
        }
        log.info("Rebuilt semantic cache index from database: {} live entries", live.size());
    }

    public CacheDtos.LookupResponse lookup(CacheDtos.LookupRequest request) {
        double[] queryVector = embeddingService.embed(request.prompt());
        return index.findBestMatch(request.tenantKey(), request.model(), queryVector, similarityThreshold)
                .map(match -> new CacheDtos.LookupResponse(true, match.entry().content(),
                        new CacheDtos.UsageDto(match.entry().promptTokens(), match.entry().completionTokens()),
                        match.similarity()))
                .orElse(CacheDtos.LookupResponse.MISS);
    }

    @Transactional
    public void store(CacheDtos.StoreRequest request) {
        double[] vector = embeddingService.embed(request.prompt());
        Instant expiresAt = Instant.now().plusSeconds(ttlSeconds);

        CacheEntry entry = new CacheEntry();
        entry.setTenantKey(request.tenantKey());
        entry.setModel(request.model());
        entry.setPromptText(request.prompt());
        entry.setEmbeddingCsv(toCsv(vector));
        entry.setContent(request.content());
        entry.setPromptTokens(request.promptTokens());
        entry.setCompletionTokens(request.completionTokens());
        entry.setExpiresAt(expiresAt);
        entry = repository.save(entry);

        index.add(entry.getId(), entry.getTenantKey(), entry.getModel(), vector,
                entry.getContent(), entry.getPromptTokens(), entry.getCompletionTokens(), expiresAt);
    }

    /** Periodic sweep: purge expired rows from Postgres and drop them from the in-memory index,
     *  so a long-running instance's index doesn't grow unbounded with dead entries. */
    @Scheduled(fixedDelay = 60_000)
    @Transactional
    public void evictExpired() {
        Instant now = Instant.now();
        List<CacheEntry> expiring = repository.findByExpiresAtLessThanEqual(now);
        expiring.forEach(e -> index.evict(e.getId()));
        int deleted = repository.deleteExpired(now);
        if (deleted > 0) {
            log.info("Evicted {} expired cache entries", deleted);
        }
    }

    private String toCsv(double[] vector) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < vector.length; i++) {
            if (i > 0) sb.append(',');
            sb.append(vector[i]);
        }
        return sb.toString();
    }

    private double[] parseCsv(String csv) {
        String[] parts = csv.split(",");
        double[] vector = new double[parts.length];
        for (int i = 0; i < parts.length; i++) vector[i] = Double.parseDouble(parts[i]);
        return vector;
    }
}
