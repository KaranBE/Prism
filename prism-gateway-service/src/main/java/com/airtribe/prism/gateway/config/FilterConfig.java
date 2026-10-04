package com.airtribe.prism.gateway.config;

import com.airtribe.prism.gateway.security.VirtualKeyAuthFilter;
import com.airtribe.prism.gateway.service.RateLimiterService;
import com.airtribe.prism.gateway.service.VirtualKeyService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@RequiredArgsConstructor
public class FilterConfig {

    private final VirtualKeyService virtualKeyService;
    private final RateLimiterService rateLimiterService;
    private final ObjectMapper objectMapper;

    @Bean
    public FilterRegistrationBean<VirtualKeyAuthFilter> virtualKeyAuthFilter() {
        FilterRegistrationBean<VirtualKeyAuthFilter> registration = new FilterRegistrationBean<>();
        registration.setFilter(new VirtualKeyAuthFilter(virtualKeyService, rateLimiterService, objectMapper));
        registration.addUrlPatterns("/v1/*");
        registration.setOrder(1);
        return registration;
    }
}
