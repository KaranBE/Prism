package com.airtribe.prism.gateway.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;

import java.io.InputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Produces the GatewayRuntimeConfig singleton by parsing gateway_config.sample.json.
 * Implemented as a @Bean factory method (not an ApplicationRunner) so the config is
 * guaranteed populated before any other singleton - including the controllers - can be
 * instantiated and start serving traffic.
 */
@Configuration
public class GatewayConfigLoader {

    @Bean
    public GatewayRuntimeConfig gatewayRuntimeConfig(ObjectMapper objectMapper) throws Exception {
        try (InputStream in = new ClassPathResource("data/gateway_config.sample.json").getInputStream()) {
            JsonNode root = objectMapper.readTree(in);
            Map<String, AliasDefinition> aliases = new HashMap<>();
            root.get("aliases").fields().forEachRemaining(entry -> {
                JsonNode v = entry.getValue();
                List<String> chain = objectMapper.convertValue(v.get("fallbackChain"), List.class);
                aliases.put(entry.getKey(), new AliasDefinition(v.get("primary").asText(), chain));
            });
            JsonNode provider = root.get("provider");
            JsonNode cache = root.get("cache");
            return new GatewayRuntimeConfig(
                    aliases,
                    provider.get("timeoutMillis").asLong(),
                    provider.get("maxRetries").asInt(),
                    provider.get("initialBackoffMillis").asLong(),
                    provider.get("backoffMultiplier").asDouble(),
                    cache.get("similarityThreshold").asDouble(),
                    cache.get("ttlSeconds").asLong());
        }
    }
}
