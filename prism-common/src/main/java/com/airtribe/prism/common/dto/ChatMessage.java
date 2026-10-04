package com.airtribe.prism.common.dto;

import jakarta.validation.constraints.NotBlank;

/** One OpenAI-compatible chat message. Immutable record -> thread-safe by construction. */
public record ChatMessage(
        @NotBlank(message = "role is required (system|user|assistant)") String role,
        @NotBlank(message = "content must not be blank") String content
) {
}
