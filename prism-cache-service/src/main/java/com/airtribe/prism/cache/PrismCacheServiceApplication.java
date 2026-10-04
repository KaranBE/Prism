package com.airtribe.prism.cache;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Entry point for the Prism Cache microservice: an independently deployable and scalable
 * service that owns semantic-cache embedding, indexing and storage, decoupled from the
 * gateway's request-handling hot path.
 */
@SpringBootApplication
@EnableScheduling
public class PrismCacheServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(PrismCacheServiceApplication.class, args);
    }
}
