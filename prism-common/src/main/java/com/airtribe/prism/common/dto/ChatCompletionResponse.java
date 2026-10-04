package com.airtribe.prism.common.dto;

import java.util.List;

/** OpenAI-compatible non-streaming response envelope. */
public record ChatCompletionResponse(
        String id,
        String object,
        long created,
        String model,
        List<ChatChoice> choices,
        Usage usage
) {
    public static ChatCompletionResponse of(String id, String model, String content, Usage usage) {
        ChatMessage message = new ChatMessage("assistant", content);
        return new ChatCompletionResponse(
                id, "chat.completion", System.currentTimeMillis() / 1000, model,
                List.of(new ChatChoice(0, message, "stop")), usage);
    }
}
