package com.airtribe.prism.gateway.adapter;

import com.airtribe.prism.common.exception.UnknownModelAliasException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Factory-pattern lookup from a concrete model id to the adapter that serves it. Built once
 * from whatever ProviderAdapter beans Spring finds (open/closed: adding a new adapter bean is
 * enough, nothing here needs to change), then cached in a plain HashMap for O(1) resolution
 * on every request instead of a linear scan.
 */
@Component
@RequiredArgsConstructor
public class ProviderAdapterFactory {

    private final List<MockProviderAdapter> adapters;
    private final Map<String, MockProviderAdapter> byModel = new ConcurrentHashMap<>();

    public MockProviderAdapter forModel(String model) {
        return byModel.computeIfAbsent(model, m -> adapters.stream()
                .filter(a -> a.supports(m))
                .findFirst()
                .orElseThrow(() -> new UnknownModelAliasException(m)));
    }

    public List<MockProviderAdapter> all() {
        return adapters;
    }
}
