package com.airtribe.prism.gateway.repository;

import com.airtribe.prism.gateway.domain.VirtualKey;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Proves the atomic conditional-UPDATE budget charge cannot be over-admitted: fire many
 * concurrent charges of $1 each against a key with a $10 budget and assert that exactly 10
 * succeed, however the requests interleave - the property "Gateway Correctness Under
 * Pressure" explicitly asks for.
 *
 * Propagation.NOT_SUPPORTED disables @DataJpaTest's default single-transaction-per-test
 * wrapping: with it left on, every worker thread below would share (and block on) the main
 * test thread's uncommitted transaction instead of issuing genuinely concurrent, separately
 * committed updates, defeating the point of the test.
 */
@DataJpaTest
@ActiveProfiles("test")
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class VirtualKeyRepositoryBudgetTest {

    @Autowired
    private VirtualKeyRepository virtualKeyRepository;

    @Test
    void concurrentChargesNeverExceedBudget() throws InterruptedException {
        VirtualKey key = new VirtualKey();
        key.setKeyValue("test-budget-key");
        key.setAlias("budget-test");
        key.setRequestsPerMinute(1000);
        key.setMonthlyBudgetUsd(new BigDecimal("10.00"));
        key = virtualKeyRepository.saveAndFlush(key);
        Long id = key.getId();

        int attempts = 50;
        ExecutorService pool = Executors.newFixedThreadPool(16);
        CountDownLatch done = new CountDownLatch(attempts);
        AtomicInteger successCount = new AtomicInteger();

        for (int i = 0; i < attempts; i++) {
            pool.submit(() -> {
                try {
                    int rows = virtualKeyRepository.chargeIfWithinBudget(id, new BigDecimal("1.00"));
                    if (rows > 0) {
                        successCount.incrementAndGet();
                    }
                } finally {
                    done.countDown();
                }
            });
        }
        done.await();
        pool.shutdown();

        assertThat(successCount.get()).isEqualTo(10);
        VirtualKey finalState = virtualKeyRepository.findById(id).orElseThrow();
        assertThat(finalState.getMonthlySpendUsd()).isEqualByComparingTo("10.00");
    }
}
