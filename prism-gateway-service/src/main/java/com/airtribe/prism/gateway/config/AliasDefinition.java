package com.airtribe.prism.gateway.config;

import java.util.List;

/** A resolved routing target: a primary model plus an ordered fallback chain. */
public record AliasDefinition(String primary, List<String> fallbackChain) {
}
