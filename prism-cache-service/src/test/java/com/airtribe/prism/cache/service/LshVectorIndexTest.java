package com.airtribe.prism.cache.service;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class LshVectorIndexTest {

    private final EmbeddingService embeddingService = new FeatureHashingEmbeddingService();
    private final LshVectorIndex index = new LshVectorIndex(embeddingService);

    @Test
    void paraphrasedPromptHitsTheCache() {
        double[] original = embeddingService.embed("What is the capital city of France?");
        index.add(1L, "team-alpha", "mock-fast-v1", original, "Paris is the capital of France.",
                10, 8, Instant.now().plusSeconds(3600));

        double[] paraphrase = embeddingService.embed("Can you tell me the capital city of France");
        Optional<LshVectorIndex.MatchResult> match =
                index.findBestMatch("team-alpha", "mock-fast-v1", paraphrase, 0.6);

        assertThat(match).isPresent();
        assertThat(match.get().entry().content()).contains("Paris");
    }

    @Test
    void unrelatedPromptMisses() {
        double[] original = embeddingService.embed("What is the capital city of France?");
        index.add(1L, "team-alpha", "mock-fast-v1", original, "Paris is the capital of France.",
                10, 8, Instant.now().plusSeconds(3600));

        double[] unrelated = embeddingService.embed("Write a Python function to reverse a linked list");
        Optional<LshVectorIndex.MatchResult> match =
                index.findBestMatch("team-alpha", "mock-fast-v1", unrelated, 0.92);

        assertThat(match).isEmpty();
    }

    @Test
    void cacheEntriesAreIsolatedPerTenant() {
        double[] vector = embeddingService.embed("What is the capital city of France?");
        index.add(1L, "team-alpha", "mock-fast-v1", vector, "Paris is the capital of France.",
                10, 8, Instant.now().plusSeconds(3600));

        double[] sameQuestion = embeddingService.embed("What is the capital city of France?");
        // team-beta never stored anything -> must be a miss even though the vector is identical,
        // proving cache scoping never leaks a response across tenants.
        Optional<LshVectorIndex.MatchResult> match =
                index.findBestMatch("team-beta", "mock-fast-v1", sameQuestion, 0.5);

        assertThat(match).isEmpty();
    }

    @Test
    void expiredEntryIsNotReturned() {
        double[] vector = embeddingService.embed("What is the capital city of France?");
        index.add(1L, "team-alpha", "mock-fast-v1", vector, "Paris is the capital of France.",
                10, 8, Instant.now().minusSeconds(1)); // already expired

        Optional<LshVectorIndex.MatchResult> match =
                index.findBestMatch("team-alpha", "mock-fast-v1", vector, 0.5);

        assertThat(match).isEmpty();
    }
}
