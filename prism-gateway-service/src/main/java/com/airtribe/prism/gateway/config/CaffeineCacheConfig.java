package com.airtribe.prism.gateway.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.cache.CacheManager;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.TimeUnit;

/**
 * Local, in-process L1 cache in front of the virtual-key lookup (a DB read on every single
 * request otherwise). Short TTL keeps revocation/allowlist edits visible within seconds while
 * still absorbing the overwhelming majority of read traffic - a classic read-through cache
 * for hot, rarely-changing rows, layered underneath the distributed semantic cache which
 * caches whole *responses* rather than key lookups.
 */
@Configuration
public class CaffeineCacheConfig {

    public static final String VIRTUAL_KEY_CACHE = "virtualKeyByValue";

    @Bean
    public CacheManager cacheManager() {
        CaffeineCacheManager manager = new CaffeineCacheManager(VIRTUAL_KEY_CACHE);
        manager.setCaffeine(Caffeine.newBuilder()
                .maximumSize(10_000)
                .expireAfterWrite(5, TimeUnit.SECONDS)
                .recordStats());
        return manager;
    }
}
