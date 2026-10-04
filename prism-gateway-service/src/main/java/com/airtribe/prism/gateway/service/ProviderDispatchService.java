package com.airtribe.prism.gateway.service;

import com.airtribe.prism.common.dto.ChatCompletionRequest;
import com.airtribe.prism.common.dto.Usage;
import com.airtribe.prism.common.exception.AllProvidersExhaustedException;
import com.airtribe.prism.common.exception.PrismException;
import com.airtribe.prism.common.exception.ProviderUnavailableException;
import com.airtribe.prism.gateway.adapter.MockProviderAdapter;
import com.airtribe.prism.gateway.adapter.ProviderAdapter;
import com.airtribe.prism.gateway.adapter.ProviderAdapterFactory;
import com.airtribe.prism.gateway.config.GatewayRuntimeConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Chain-of-Responsibility dispatch: walks a RoutingPlan's full model chain (primary, then
 * fallbacks in order), retrying each individual model with exponential backoff before moving
 * to the next. A per-provider circuit breaker short-circuits models that are already known to
 * be down so a live outage doesn't cost every request a full timeout before failing over.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProviderDispatchService {

    private final ProviderAdapterFactory adapterFactory;
    private final GatewayRuntimeConfig config;

    private final Map<String, SimpleCircuitBreaker> breakersByProvider = new ConcurrentHashMap<>();

    public record DispatchResult(String resolvedModel, String provider, String content, Usage usage, boolean fallbackUsed) {}
    public record StreamEvent(String token, boolean last, DispatchResult finalResultIfLast) {}

    public Mono<DispatchResult> dispatch(RoutingPlan plan, ChatCompletionRequest request) {
        return tryChain(plan.fullChain(), 0, request, false);
    }

    private Mono<DispatchResult> tryChain(List<String> chain, int index, ChatCompletionRequest request, boolean fallbackUsed) {
        if (index >= chain.size()) {
            return Mono.error(new AllProvidersExhaustedException(chain.get(0), chain));
        }
        String model = chain.get(index);
        MockProviderAdapter adapter = adapterFactory.forModel(model);
        SimpleCircuitBreaker breaker = breakerFor(adapter.providerName());

        Mono<DispatchResult> attempt;
        if (!breaker.allowRequest()) {
            log.warn("Circuit breaker OPEN for provider {} - skipping straight to next model in chain", adapter.providerName());
            attempt = Mono.error(new ProviderUnavailableException(adapter.providerName(), "circuit breaker open"));
        } else {
            attempt = callWithRetry(adapter, model, request, 0)
                    .doOnSuccess(r -> breaker.recordSuccess())
                    .doOnError(ProviderUnavailableException.class, e -> breaker.recordFailure())
                    .map(r -> new DispatchResult(model, adapter.providerName(), r.content(), r.usage(), fallbackUsed));
        }

        return attempt.onErrorResume(PrismException.class,
                ex -> tryChain(chain, index + 1, request, true));
    }

    private Mono<ProviderAdapter.CompletionResult> callWithRetry(MockProviderAdapter adapter, String model,
                                                                   ChatCompletionRequest request, int attempt) {
        return adapter.complete(model, request)
                .onErrorResume(ProviderUnavailableException.class, ex -> {
                    if (attempt >= config.maxRetries()) {
                        return Mono.error(ex);
                    }
                    long backoffMillis = (long) (config.initialBackoffMillis() * Math.pow(config.backoffMultiplier(), attempt));
                    log.info("Retrying {} on {} after {} ms (attempt {}/{})", model, adapter.providerName(),
                            backoffMillis, attempt + 1, config.maxRetries());
                    return Mono.delay(Duration.ofMillis(backoffMillis)).then(callWithRetry(adapter, model, request, attempt + 1));
                });
    }

    /** Streaming variant: only fails over to the next model if the failure happens before any
     *  token has reached the client - once bytes are flowing we cannot un-send them, so a
     *  mid-stream failure is surfaced to the caller instead of silently retried. */
    public Flux<StreamEvent> dispatchStreaming(RoutingPlan plan, ChatCompletionRequest request) {
        return streamChain(plan.fullChain(), 0, request, false);
    }

    private Flux<StreamEvent> streamChain(List<String> chain, int index, ChatCompletionRequest request, boolean fallbackUsed) {
        if (index >= chain.size()) {
            return Flux.error(new AllProvidersExhaustedException(chain.get(0), chain));
        }
        String model = chain.get(index);
        MockProviderAdapter adapter = adapterFactory.forModel(model);
        SimpleCircuitBreaker breaker = breakerFor(adapter.providerName());
        AtomicBoolean emittedAny = new AtomicBoolean(false);
        boolean thisIsFallback = fallbackUsed;

        if (!breaker.allowRequest()) {
            return streamChain(chain, index + 1, request, true);
        }

        return adapter.streamComplete(model, request)
                .doOnNext(t -> emittedAny.set(true))
                .map(t -> t.last()
                        ? new StreamEvent(null, true, new DispatchResult(model, adapter.providerName(), null, t.finalUsageIfLast(), thisIsFallback))
                        : new StreamEvent(t.token(), false, null))
                .doOnComplete(breaker::recordSuccess)
                .onErrorResume(PrismException.class, ex -> {
                    breaker.recordFailure();
                    if (emittedAny.get()) {
                        return Flux.error(ex); // already streamed to the client -> cannot fail over silently
                    }
                    return streamChain(chain, index + 1, request, true);
                });
    }

    private SimpleCircuitBreaker breakerFor(String provider) {
        return breakersByProvider.computeIfAbsent(provider, p -> new SimpleCircuitBreaker(3, 10_000));
    }
}
