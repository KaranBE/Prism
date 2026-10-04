package com.airtribe.prism.usage.dto;

import jakarta.validation.constraints.NotNull;

public record UsageEventDto(
        @NotNull Long virtualKeyId,
        String requestedModel,
        String resolvedModel,
        String provider,
        String status,
        Integer promptTokens,
        Integer completionTokens,
        String costUsd,
        boolean cacheHit,
        boolean fallbackUsed,
        long latencyMs,
        String createdAt
) {}
