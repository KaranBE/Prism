package com.airtribe.prism.gateway.config;

import com.airtribe.prism.gateway.domain.VirtualKey;
import com.airtribe.prism.gateway.repository.VirtualKeyRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.math.BigDecimal;
import java.util.HashSet;
import java.util.Set;

/**
 * Seeds virtual keys from seed_keys.json on startup, preserving the exact key values and
 * alias names the demo flow and verification scripts reference. Idempotent - safe to restart
 * the service repeatedly without duplicating or resetting existing keys/spend.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DataBootstrapConfig implements ApplicationRunner {

    private final VirtualKeyRepository virtualKeyRepository;
    private final ObjectMapper objectMapper;

    @Override
    public void run(ApplicationArguments args) throws Exception {
        try (InputStream in = new ClassPathResource("data/seed_keys.json").getInputStream()) {
            JsonNode root = objectMapper.readTree(in);
            int created = 0;
            for (JsonNode node : root) {
                String keyValue = node.get("keyValue").asText();
                if (virtualKeyRepository.findByKeyValueAndActiveTrue(keyValue).isPresent()) {
                    continue;
                }
                VirtualKey key = new VirtualKey();
                key.setKeyValue(keyValue);
                key.setAlias(node.get("alias").asText());
                Set<String> allowed = new HashSet<>();
                node.get("allowedModels").forEach(m -> allowed.add(m.asText()));
                key.setAllowedModels(allowed);
                key.setRequestsPerMinute(node.get("requestsPerMinute").asInt());
                key.setMonthlyBudgetUsd(new BigDecimal(node.get("monthlyBudgetUsd").asText()));
                virtualKeyRepository.save(key);
                created++;
            }
            log.info("Seed key bootstrap complete: {} new key(s) created", created);
        }
    }
}
