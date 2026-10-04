package com.airtribe.prism.gateway.domain;

/**
 * Price-per-token for one model, loaded from model_pricing.json at boot into an in-memory
 * registry (PricingRegistry) rather than a DB table - the price sheet is small, read-only at
 * runtime, and on the hottest path (cost is computed on every single request), so paying a
 * DB round trip for it would be a pure scalability tax.
 */
public record ModelPricing(String model, String provider, double inputPricePer1k, double outputPricePer1k) {
}
