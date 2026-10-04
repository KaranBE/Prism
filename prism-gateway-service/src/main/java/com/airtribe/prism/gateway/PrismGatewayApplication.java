package com.airtribe.prism.gateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Entry point for the Prism Gateway microservice - the AI-decisioning hot path
 * described in the case study: auth -> rate-limit/budget -> semantic-cache lookup ->
 * smart routing -> provider dispatch (retry/failover) -> streaming -> usage metering.
 */
@SpringBootApplication
@EnableCaching
@EnableAsync
@EnableScheduling
public class PrismGatewayApplication {
    public static void main(String[] args) {
        SpringApplication.run(PrismGatewayApplication.class, args);
    }
}
