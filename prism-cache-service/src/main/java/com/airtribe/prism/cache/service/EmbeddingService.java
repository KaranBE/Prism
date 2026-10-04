package com.airtribe.prism.cache.service;

/**
 * Strategy-pattern seam for turning prompt text into a fixed-dimension embedding vector.
 * Swapping FeatureHashingEmbeddingService for a call to a real embedding model/API later is
 * a one-class change - nothing in SemanticCacheService or LshVectorIndex needs to know.
 */
public interface EmbeddingService {
    int dimensions();
    double[] embed(String text);
}
