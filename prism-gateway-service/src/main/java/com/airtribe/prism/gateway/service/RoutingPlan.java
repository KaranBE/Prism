package com.airtribe.prism.gateway.service;

import java.util.List;

/** The resolved outcome of routing: which model to try first, and the ordered fallback chain. */
public record RoutingPlan(String requestedAlias, String primaryModel, List<String> fallbackChain, boolean wasAutoRouted) {
    public List<String> fullChain() {
        List<String> chain = new java.util.ArrayList<>();
        chain.add(primaryModel);
        chain.addAll(fallbackChain);
        return chain;
    }
}
