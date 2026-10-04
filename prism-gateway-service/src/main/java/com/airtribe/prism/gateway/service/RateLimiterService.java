package com.airtribe.prism.gateway.service;

import com.airtribe.prism.common.exception.RateLimitExceededException;
import com.airtribe.prism.gateway.domain.VirtualKey;
import org.springframework.stereotype.Service;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Per-key token-bucket rate limiter, entirely lock-free.
 *
 * DSA note: each bucket is an immutable {@link BucketState} swapped via a
 * compare-and-swap retry loop on an {@link AtomicReference}, rather than behind a
 * {@code synchronized} block or a {@code ReentrantLock}. Under the concurrent load the
 * spec's load test drives, a CAS loop scales far better than lock contention because
 * losing threads simply recompute and retry instead of blocking the JVM's monitor -
 * the classic non-blocking bucket-of-tokens algorithm. Capacity == requestsPerMinute
 * (bursts up to the full per-minute allowance are allowed); refill is continuous
 * (tokens/ms), not a hard per-minute reset, which avoids the thundering-herd
 * re-admission spike a naive "reset every 60s" counter would produce.
 *
 * This is process-local, which is the right trade-off for a single gateway instance
 * (as run in this project's docker-compose). Scaling the gateway horizontally would move
 * this same algorithm behind Redis (INCR + PEXPIRE or a Lua token-bucket script) so every
 * instance shares one counter per key - noted in the README as the scaling path.
 */
@Service
public class RateLimiterService {

    private record BucketState(double tokens, long lastRefillNanos) {}

    private final ConcurrentHashMap<Long, AtomicReference<BucketState>> buckets = new ConcurrentHashMap<>();

    public void checkAndConsume(VirtualKey key) {
        double capacity = key.getRequestsPerMinute();
        double refillPerNano = capacity / 60_000_000_000.0; // tokens per nanosecond

        AtomicReference<BucketState> ref = buckets.computeIfAbsent(key.getId(),
                id -> new AtomicReference<>(new BucketState(capacity, System.nanoTime())));

        while (true) {
            BucketState current = ref.get();
            long now = System.nanoTime();
            double refilled = Math.min(capacity, current.tokens() + (now - current.lastRefillNanos()) * refillPerNano);

            if (refilled < 1.0) {
                long tokensNeeded = 1;
                long nanosUntilNextToken = (long) ((tokensNeeded - refilled) / refillPerNano);
                throw new RateLimitExceededException(key.getAlias(), nanosUntilNextToken / 1_000_000);
            }

            BucketState next = new BucketState(refilled - 1.0, now);
            if (ref.compareAndSet(current, next)) {
                return; // token consumed successfully
            }
            // else: lost the race with another thread on the same key -> retry with fresh state
        }
    }
}
