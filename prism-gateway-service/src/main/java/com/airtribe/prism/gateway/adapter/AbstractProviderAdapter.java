package com.airtribe.prism.gateway.adapter;

import com.airtribe.prism.common.exception.ProviderTimeoutException;
import com.airtribe.prism.common.exception.ProviderUnavailableException;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.concurrent.TimeoutException;

/**
 * Template Method base class: every concrete adapter gets uniform timeout handling and
 * error translation (raw provider/network failures -> Prism's own exception hierarchy) for
 * free, so individual adapters only implement the actual call. "Every upstream provider call
 * must have a timeout" is enforced structurally here rather than left to each adapter to
 * remember.
 */
public abstract class AbstractProviderAdapter implements ProviderAdapter {

    protected <T> Mono<T> withTimeout(Mono<T> call, Duration timeout) {
        return call
                .timeout(timeout)
                .onErrorMap(TimeoutException.class,
                        e -> new ProviderTimeoutException(providerName(), timeout.toMillis()))
                .onErrorMap(ex -> !(ex instanceof ProviderTimeoutException) && !(ex instanceof ProviderUnavailableException),
                        e -> new ProviderUnavailableException(providerName(), e.getMessage(), e));
    }
}
