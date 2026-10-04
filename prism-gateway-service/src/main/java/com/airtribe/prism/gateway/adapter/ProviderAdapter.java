package com.airtribe.prism.gateway.adapter;

import com.airtribe.prism.common.dto.ChatCompletionRequest;
import com.airtribe.prism.common.dto.Usage;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Adapter-pattern seam between Prism's internal, OpenAI-shaped request/response model and
 * whatever wire format a specific upstream actually speaks. New providers (a real OpenAI
 * client, Anthropic, a self-hosted vLLM endpoint, ...) are added by implementing this
 * interface - nothing in the routing, retry, streaming or metering layers needs to change,
 * which is exactly the requirement: "keep provider integration behind an adapter so mock
 * providers or real providers can be used without changing the core system."
 */
public interface ProviderAdapter {

    /** The provider name this adapter speaks for, as recorded in x-prism-provider / request logs. */
    String providerName();

    /** Non-streaming completion. Emits exactly one (content, usage) result or an error. */
    Mono<CompletionResult> complete(String model, ChatCompletionRequest request);

    /** Token-by-token streaming completion. The final element carries the aggregate Usage. */
    Flux<StreamToken> streamComplete(String model, ChatCompletionRequest request);

    record CompletionResult(String content, Usage usage) {}

    record StreamToken(String token, boolean last, Usage finalUsageIfLast) {}
}
