package com.airtribe.prism.common.dto;

/** Provider-reported token accounting for a single request; the sole input to cost computation. */
public record Usage(int promptTokens, int completionTokens, int totalTokens) {
    public static Usage of(int promptTokens, int completionTokens) {
        return new Usage(promptTokens, completionTokens, promptTokens + completionTokens);
    }
}
