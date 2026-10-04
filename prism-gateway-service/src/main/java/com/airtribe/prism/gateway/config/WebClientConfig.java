package com.airtribe.prism.gateway.config;

import io.netty.channel.ChannelOption;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;

import java.time.Duration;

/**
 * Reactive, non-blocking HTTP clients for two purposes: (1) a ready-made client for real,
 * HTTP-backed provider adapters to use (the bundled MockProviderAdapter simulates locally and
 * doesn't need it, but any real adapter - OpenAI, Anthropic, a self-hosted endpoint - would),
 * and (2) low-latency calls to prism-cache-service and prism-usage-service. Every client is
 * built with an explicit connect timeout so a dead peer never hangs a gateway thread
 * indefinitely - a requirement called out explicitly in the spec.
 */
@Configuration
public class WebClientConfig {

    @Bean
    public WebClient providerWebClient(@Value("${prism.provider.connect-timeout-ms:2000}") int connectTimeoutMs) {
        HttpClient httpClient = HttpClient.create()
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, connectTimeoutMs)
                .responseTimeout(Duration.ofMillis(15_000));
        return WebClient.builder()
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .build();
    }

    @Bean
    public WebClient cacheServiceWebClient(@Value("${prism.cache-service.base-url}") String baseUrl) {
        HttpClient httpClient = HttpClient.create()
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 2000)
                .responseTimeout(Duration.ofMillis(3000));
        return WebClient.builder()
                .baseUrl(baseUrl)
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .build();
    }

    @Bean
    public WebClient usageServiceWebClient(@Value("${prism.usage-service.base-url}") String baseUrl) {
        HttpClient httpClient = HttpClient.create()
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 2000)
                .responseTimeout(Duration.ofMillis(3000));
        return WebClient.builder()
                .baseUrl(baseUrl)
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .build();
    }
}
