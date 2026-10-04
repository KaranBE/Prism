package com.airtribe.prism.gateway.adapter;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;
import java.util.Set;

/**
 * Wires up the two mock providers named in model_pricing.json. Swapping one of these for a
 * real HTTP-backed adapter (e.g. an OpenAiProviderAdapter using WebClient) is the entire
 * integration surface - routing, retry, streaming and metering code is untouched.
 */
@Configuration
public class ProviderAdapterConfig {

    @Bean
    public MockProviderAdapter mockProviderA() {
        return new MockProviderAdapter(
                "mock-provider-a",
                Set.of("mock-fast-v1", "mock-smart-v1"),
                Duration.ofMillis(400),
                0.05, // 5% simulated transient failure rate -> exercises the retry path
                Duration.ofSeconds(8));
    }

    @Bean
    public MockProviderAdapter mockProviderB() {
        return new MockProviderAdapter(
                "mock-provider-b",
                Set.of("mock-fast-v2", "mock-smart-v2"),
                Duration.ofMillis(550),
                0.05,
                Duration.ofSeconds(8));
    }
}
