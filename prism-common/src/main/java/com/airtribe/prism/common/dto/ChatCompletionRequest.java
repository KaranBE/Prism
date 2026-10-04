package com.airtribe.prism.common.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.DecimalMax;
import java.util.List;

/**
 * The OpenAI-compatible request body for POST /v1/chat/completions.
 * "model" accepts either a concrete provider model id or a Prism alias
 * ("fast", "smart", "auto").
 */
public record ChatCompletionRequest(
        @NotBlank(message = "model is required") String model,
        @NotEmpty(message = "messages must contain at least one entry") @Valid List<ChatMessage> messages,
        Boolean stream,
        @DecimalMin(value = "0.0", message = "temperature must be >= 0.0")
        @DecimalMax(value = "2.0", message = "temperature must be <= 2.0")
        Double temperature,
        @JsonProperty("max_tokens")
        @JsonAlias({"maxTokens", "max_tokens"})
        Integer maxTokens
) {
    public boolean isStreaming() {
        return Boolean.TRUE.equals(stream);
    }

    public double temperatureOrDefault() {
        return temperature == null ? 0.7 : temperature;
    }
}
