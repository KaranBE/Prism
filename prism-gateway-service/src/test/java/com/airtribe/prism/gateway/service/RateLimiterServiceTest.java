package com.airtribe.prism.gateway.service;

import com.airtribe.prism.common.exception.RateLimitExceededException;
import com.airtribe.prism.gateway.domain.VirtualKey;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Verifies the lock-free token bucket behaves correctly under genuine concurrency, not just
 * sequentially - i.e. the "no over-admission" property the spec's load test checks.
 */
class RateLimiterServiceTest {

    private VirtualKey keyWithLimit(int rpm) {
        VirtualKey key = new VirtualKey();
        key.setId(1L);
        key.setAlias("test-key");
        key.setRequestsPerMinute(rpm);
        return key;
    }

    @Test
    void allowsExactlyCapacityRequestsThenRejects() {
        RateLimiterService limiter = new RateLimiterService();
        VirtualKey key = keyWithLimit(10);

        for (int i = 0; i < 10; i++) {
            limiter.checkAndConsume(key); // should not throw
        }
        assertThatThrownBy(() -> limiter.checkAndConsume(key))
                .isInstanceOf(RateLimitExceededException.class);
    }

    @Test
    void neverAdmitsMoreThanCapacityUnderConcurrentLoad() throws InterruptedException {
        RateLimiterService limiter = new RateLimiterService();
        VirtualKey key = keyWithLimit(50);
        int threads = 200; // far more concurrent attempts than the capacity

        ExecutorService pool = Executors.newFixedThreadPool(32);
        CountDownLatch ready = new CountDownLatch(threads);
        CountDownLatch go = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threads);
        AtomicInteger admitted = new AtomicInteger();
        AtomicInteger rejected = new AtomicInteger();

        for (int i = 0; i < threads; i++) {
            pool.submit(() -> {
                ready.countDown();
                try {
                    go.await();
                    limiter.checkAndConsume(key);
                    admitted.incrementAndGet();
                } catch (RateLimitExceededException e) {
                    rejected.incrementAndGet();
                } catch (InterruptedException ignored) {
                } finally {
                    done.countDown();
                }
            });
        }

        ready.await();
        go.countDown();
        done.await();
        pool.shutdown();

        // Some tiny refill can occur during the race window, so allow a small tolerance,
        // but the core invariant - admitted count never wildly exceeds capacity - must hold.
        assertThat(admitted.get()).isLessThanOrEqualTo(52);
        assertThat(admitted.get() + rejected.get()).isEqualTo(threads);
    }
}
