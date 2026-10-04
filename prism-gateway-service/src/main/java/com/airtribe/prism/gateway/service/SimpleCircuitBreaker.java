package com.airtribe.prism.gateway.service;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/**
 * A minimal, dependency-free circuit breaker (CLOSED -> OPEN -> HALF_OPEN -> CLOSED state
 * machine) placed in front of each provider adapter. When a provider is failing repeatedly,
 * tripping the breaker means Prism stops *wasting a timeout* on it and fails over to the next
 * model in the chain immediately, instead of paying the full provider timeout on every request
 * while it is down - directly improving perceived failover latency during an outage.
 *
 * All counters are atomic so this is safe to share across the many concurrent request threads
 * hitting the same provider.
 */
public class SimpleCircuitBreaker {

    public enum State { CLOSED, OPEN, HALF_OPEN }

    private final int failureThreshold;
    private final long openDurationMillis;

    private final AtomicReference<State> state = new AtomicReference<>(State.CLOSED);
    private final AtomicInteger consecutiveFailures = new AtomicInteger(0);
    private final AtomicLong openedAtMillis = new AtomicLong(0);

    public SimpleCircuitBreaker(int failureThreshold, long openDurationMillis) {
        this.failureThreshold = failureThreshold;
        this.openDurationMillis = openDurationMillis;
    }

    /** Whether a call should even be attempted right now. */
    public boolean allowRequest() {
        State current = state.get();
        if (current == State.CLOSED) {
            return true;
        }
        if (current == State.OPEN) {
            if (System.currentTimeMillis() - openedAtMillis.get() >= openDurationMillis) {
                // cool-down elapsed -> allow exactly one trial request through
                state.compareAndSet(State.OPEN, State.HALF_OPEN);
                return true;
            }
            return false;
        }
        return true; // HALF_OPEN: allow the trial request
    }

    public void recordSuccess() {
        consecutiveFailures.set(0);
        state.set(State.CLOSED);
    }

    public void recordFailure() {
        int failures = consecutiveFailures.incrementAndGet();
        if (state.get() == State.HALF_OPEN || failures >= failureThreshold) {
            state.set(State.OPEN);
            openedAtMillis.set(System.currentTimeMillis());
        }
    }

    public State state() {
        return state.get();
    }
}
