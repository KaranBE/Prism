package com.airtribe.prism.usage.dto;

import java.math.BigDecimal;

/** JPQL constructor-expression projection - avoids pulling full entities back for a report query. */
public record ProviderBreakdown(String provider, long requestCount, BigDecimal totalCostUsd, double avgLatencyMs) {
}
