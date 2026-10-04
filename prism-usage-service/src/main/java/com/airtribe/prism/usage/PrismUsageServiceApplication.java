package com.airtribe.prism.usage;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/** Entry point for the Prism Usage microservice - cross-key metering and analytics. */
@SpringBootApplication
public class PrismUsageServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(PrismUsageServiceApplication.class, args);
    }
}
