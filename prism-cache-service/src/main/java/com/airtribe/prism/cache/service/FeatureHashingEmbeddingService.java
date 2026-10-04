package com.airtribe.prism.cache.service;

import org.springframework.stereotype.Service;

import java.util.regex.Pattern;

/**
 * A dependency-free, deterministic text embedding via the hashing trick: every token is
 * hashed into one of D dimensions and accumulated, then the vector is L2-normalized. This is
 * a bag-of-words-style embedding - it captures shared vocabulary well (which is exactly what
 * makes a paraphrased prompt land close to the original in cosine-similarity terms) without
 * capturing deep semantics the way a trained embedding model would. It needs no network call
 * and no external model, which keeps cache lookups fast and the whole service self-contained;
 * the README documents this as a known limitation and the natural upgrade path (swap in a
 * real embeddings API behind this same interface).
 */
@Service
public class FeatureHashingEmbeddingService implements EmbeddingService {

    private static final int DIMENSIONS = 128;
    private static final Pattern TOKEN_PATTERN = Pattern.compile("[a-zA-Z0-9]+");

    @Override
    public int dimensions() {
        return DIMENSIONS;
    }

    @Override
    public double[] embed(String text) {
        double[] vector = new double[DIMENSIONS];
        var matcher = TOKEN_PATTERN.matcher(text.toLowerCase());
        while (matcher.find()) {
            String token = matcher.group();
            int index = Math.floorMod(token.hashCode(), DIMENSIONS);
            vector[index] += 1.0;
            // also hash bigram-ish signal (token length bucket) into a second slot - cheap
            // extra signal that helps distinguish "explain X" from "X" without a real model.
            int secondary = Math.floorMod(token.hashCode() * 31 + token.length(), DIMENSIONS);
            vector[secondary] += 0.25;
        }
        normalize(vector);
        return vector;
    }

    private void normalize(double[] vector) {
        double normSq = 0;
        for (double v : vector) normSq += v * v;
        if (normSq == 0) return;
        double norm = Math.sqrt(normSq);
        for (int i = 0; i < vector.length; i++) vector[i] /= norm;
    }
}
