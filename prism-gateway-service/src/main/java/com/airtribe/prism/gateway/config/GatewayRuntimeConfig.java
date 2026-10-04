package com.airtribe.prism.gateway.config;

import java.util.Map;

/** In-memory snapshot of gateway_config.sample.json, populated once at boot. */
public record GatewayRuntimeConfig(
        Map<String, AliasDefinition> aliases,
        long providerTimeoutMillis,
        int maxRetries,
        long initialBackoffMillis,
        double backoffMultiplier,
        double cacheSimilarityThreshold,
        long cacheTtlSeconds
) {
}
