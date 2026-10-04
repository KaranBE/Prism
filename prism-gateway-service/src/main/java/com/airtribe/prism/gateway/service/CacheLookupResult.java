package com.airtribe.prism.gateway.service;

import com.airtribe.prism.common.dto.Usage;

public record CacheLookupResult(boolean hit, String content, Usage usage, double similarity) {
    public static final CacheLookupResult MISS = new CacheLookupResult(false, null, null, 0.0);
}
