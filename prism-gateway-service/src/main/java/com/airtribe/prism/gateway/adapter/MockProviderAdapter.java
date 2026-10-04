package com.airtribe.prism.gateway.adapter;

import com.airtribe.prism.common.dto.ChatCompletionRequest;
import com.airtribe.prism.common.dto.ChatMessage;
import com.airtribe.prism.common.dto.Usage;
import com.airtribe.prism.common.exception.ProviderUnavailableException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * A configurable mock provider adapter: simulates realistic latency and token-by-token
 * streaming without calling out to a real LLM API, so the whole gateway (routing, retries,
 * failover, streaming, metering) can be demoed and load-tested deterministically.
 *
 * The {@code healthy} flag is toggled live via AdminOpsController to demonstrate provider
 * failover ("take a provider down, show the gateway routes around it") without restarting
 * the service.
 */
public class MockProviderAdapter extends AbstractProviderAdapter {

    private final String providerName;
    private final Set<String> supportedModels;
    private final Duration simulatedLatency;
    private final double transientFailureRate;
    private final Duration timeout;
    private final AtomicBoolean healthy = new AtomicBoolean(true);

    public MockProviderAdapter(String providerName, Set<String> supportedModels, Duration simulatedLatency,
                                double transientFailureRate, Duration timeout) {
        this.providerName = providerName;
        this.supportedModels = supportedModels;
        this.simulatedLatency = simulatedLatency;
        this.transientFailureRate = transientFailureRate;
        this.timeout = timeout;
    }

    public boolean supports(String model) {
        return supportedModels.contains(model);
    }

    public void setHealthy(boolean value) {
        healthy.set(value);
    }

    public boolean isHealthy() {
        return healthy.get();
    }

    @Override
    public String providerName() {
        return providerName;
    }

    @Override
    public Mono<CompletionResult> complete(String model, ChatCompletionRequest request) {
        Mono<CompletionResult> call = Mono.defer(() -> {
                    maybeFail();
                    String content = mockAnswer(model, request);
                    Usage usage = estimateUsage(request, content);
                    return Mono.just(new CompletionResult(content, usage));
                })
                .delayElement(simulatedLatency);
        return withTimeout(call, timeout);
    }

    @Override
    public Flux<StreamToken> streamComplete(String model, ChatCompletionRequest request) {
        return Flux.defer(() -> {
            maybeFail();
            String content = mockAnswer(model, request);
            String[] words = content.split(" ");
            Usage usage = estimateUsage(request, content);
            return Flux.range(0, words.length)
                    .delayElements(Duration.ofMillis(Math.max(10, simulatedLatency.toMillis() / Math.max(1, words.length))))
                    .map(i -> new StreamToken(words[i] + (i < words.length - 1 ? " " : ""), false, null))
                    .concatWith(Flux.just(new StreamToken("", true, usage)));
        }).timeout(timeout)
          .onErrorMap(ex -> !(ex instanceof com.airtribe.prism.common.exception.PrismException),
                  e -> new ProviderUnavailableException(providerName, e.getMessage(), e));
    }

    private void maybeFail() {
        if (!healthy.get()) {
            throw new ProviderUnavailableException(providerName, "provider marked unhealthy (simulated outage)");
        }
        if (ThreadLocalRandom.current().nextDouble() < transientFailureRate) {
            throw new ProviderUnavailableException(providerName, "simulated transient 5xx");
        }
    }

    private String mockAnswer(String model, ChatCompletionRequest request) {
        ChatMessage last = request.messages().get(request.messages().size() - 1);
        boolean smart = model.contains("smart");
        String tone = smart
                ? "Here is a carefully reasoned answer from the smart tier: "
                : "Quick answer from the fast tier: ";
        String echo = last.content().length() > 80 ? last.content().substring(0, 80) + "..." : last.content();
        return tone + "regarding \"" + echo + "\", model " + model + " on " + providerName + " responds with a mock completion.";
    }

    private Usage estimateUsage(ChatCompletionRequest request, String content) {
        int promptTokens = request.messages().stream()
                .mapToInt(m -> Math.max(1, m.content().length() / 4))
                .sum();
        int completionTokens = Math.max(1, content.length() / 4);
        return Usage.of(promptTokens, completionTokens);
    }

    List<String> debugSupportedModels() {
        return List.copyOf(supportedModels);
    }
}
