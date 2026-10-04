package com.airtribe.prism.gateway.service;

import com.airtribe.prism.common.exception.InvalidVirtualKeyException;
import com.airtribe.prism.gateway.config.CaffeineCacheConfig;
import com.airtribe.prism.gateway.domain.VirtualKey;
import com.airtribe.prism.gateway.repository.VirtualKeyRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

/**
 * Resolves and validates the raw secret a caller sends into a VirtualKey. Wrapped with a
 * short-TTL local cache (see CaffeineCacheConfig) because this runs on literally every request.
 */
@Service
@RequiredArgsConstructor
public class VirtualKeyService {

    private final VirtualKeyRepository virtualKeyRepository;

    @Cacheable(cacheNames = CaffeineCacheConfig.VIRTUAL_KEY_CACHE, key = "#rawKey", unless = "#result == null")
    public VirtualKey lookup(String rawKey) {
        return virtualKeyRepository.findByKeyValueAndActiveTrue(rawKey).orElse(null);
    }

    public VirtualKey authenticate(String rawKey) {
        if (rawKey == null || rawKey.isBlank()) {
            throw new InvalidVirtualKeyException("Missing virtual key. Send 'Authorization: Bearer <key>'.");
        }
        VirtualKey key = lookup(rawKey);
        if (key == null) {
            throw new InvalidVirtualKeyException("Virtual key is unknown, revoked, or inactive.");
        }
        return key;
    }
}
