package com.airtribe.prism.gateway.config;

import com.airtribe.prism.common.exception.UnknownModelAliasException;
import com.airtribe.prism.gateway.domain.ModelPricing;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory, read-mostly price table keyed by model id, loaded from model_pricing.json
 * during bean initialisation (@PostConstruct runs while the application context is still
 * refreshing, i.e. strictly before the embedded server starts accepting traffic - so there is
 * no window where a request could race the price table load).
 *
 * A ConcurrentHashMap gives O(1) lock-free reads on the hottest path in the gateway (cost is
 * computed on every single request); routing this through a database call instead would be a
 * needless scalability tax for data that changes on the order of "a few times a day" at most.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PricingRegistry {

    private final ObjectMapper objectMapper;
    private final Map<String, ModelPricing> byModel = new ConcurrentHashMap<>();

    @PostConstruct
    void load() throws Exception {
        try (InputStream in = new ClassPathResource("data/model_pricing.json").getInputStream()) {
            JsonNode root = objectMapper.readTree(in);
            for (JsonNode node : root) {
                register(new ModelPricing(
                        node.get("model").asText(),
                        node.get("provider").asText(),
                        node.get("inputPricePer1k").asDouble(),
                        node.get("outputPricePer1k").asDouble()));
            }
        }
        log.info("Loaded model price table from model_pricing.json: {} models", byModel.size());
    }

    public void register(ModelPricing pricing) {
        byModel.put(pricing.model(), pricing);
    }

    public ModelPricing get(String model) {
        ModelPricing pricing = byModel.get(model);
        if (pricing == null) {
            throw new UnknownModelAliasException(model);
        }
        return pricing;
    }

    public boolean contains(String model) {
        return byModel.containsKey(model);
    }
}
