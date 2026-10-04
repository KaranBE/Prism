package com.airtribe.prism.cache.dto;

import jakarta.validation.constraints.NotBlank;

public class CacheDtos {

    public record LookupRequest(@NotBlank String tenantKey, @NotBlank String model, @NotBlank String prompt) {}

    public record UsageDto(int promptTokens, int completionTokens) {}

    public record LookupResponse(boolean hit, String content, UsageDto usage, double similarity) {
        public static final LookupResponse MISS = new LookupResponse(false, null, null, 0.0);
    }

    public record StoreRequest(@NotBlank String tenantKey, @NotBlank String model, @NotBlank String prompt,
                                @NotBlank String content, int promptTokens, int completionTokens) {}
}
