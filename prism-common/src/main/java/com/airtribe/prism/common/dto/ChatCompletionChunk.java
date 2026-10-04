package com.airtribe.prism.common.dto;

/** One SSE "delta" chunk for streaming responses, mirroring OpenAI's chat.completion.chunk shape. */
public record ChatCompletionChunk(
        String id,
        String object,
        long created,
        String model,
        int choiceIndex,
        String deltaRole,
        String deltaContent,
        String finishReason
) {
    public static ChatCompletionChunk token(String id, String model, String token) {
        return new ChatCompletionChunk(id, "chat.completion.chunk", System.currentTimeMillis() / 1000,
                model, 0, null, token, null);
    }

    public static ChatCompletionChunk done(String id, String model) {
        return new ChatCompletionChunk(id, "chat.completion.chunk", System.currentTimeMillis() / 1000,
                model, 0, null, null, "stop");
    }
}
