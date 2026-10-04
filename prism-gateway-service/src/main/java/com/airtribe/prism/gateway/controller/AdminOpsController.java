package com.airtribe.prism.gateway.controller;

import com.airtribe.prism.gateway.adapter.MockProviderAdapter;
import com.airtribe.prism.gateway.adapter.ProviderAdapterFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Operator endpoints used by the demo/verification flow to simulate an outage
 * ("take a provider down, show failover"), independent of the authenticated data-plane -
 * intentionally NOT behind the virtual-key filter since it manages infrastructure state,
 * not tenant traffic. In a real deployment this would sit behind its own operator auth.
 */
@RestController
@RequestMapping("/admin/providers")
@RequiredArgsConstructor
public class AdminOpsController {

    private final ProviderAdapterFactory adapterFactory;

    @GetMapping
    public List<Map<String, Object>> listProviders() {
        return adapterFactory.all().stream()
                .map(a -> Map.<String, Object>of("provider", a.providerName(), "healthy", a.isHealthy()))
                .toList();
    }

    @PostMapping("/{provider}/toggle")
    public Map<String, Object> toggle(@PathVariable("provider") String provider, @RequestParam("healthy") boolean healthy) {
        MockProviderAdapter adapter = adapterFactory.all().stream()
                .filter(a -> a.providerName().equals(provider))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown provider: " + provider));
        adapter.setHealthy(healthy);
        return Map.of("provider", provider, "healthy", adapter.isHealthy());
    }
}
