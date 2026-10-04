package com.airtribe.prism.gateway.service;

import com.airtribe.prism.common.exception.BudgetExhaustedException;
import com.airtribe.prism.gateway.domain.VirtualKey;
import com.airtribe.prism.gateway.repository.VirtualKeyRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

/**
 * Reservation-based budget enforcement:
 *  1. reserve() atomically charges an *estimated* cost before we ever call a provider,
 *     via VirtualKeyRepository#chargeIfWithinBudget - a single conditional UPDATE, so two
 *     concurrent requests on the same key can never both be admitted past the budget
 *     (the property the spec's load test checks: "no over-admission").
 *  2. trueUp() applies the (small) difference between the estimate and the actual
 *     provider-reported cost once it is known, so long-run accounting stays exact even
 *     though admission itself was decided on an estimate.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BudgetService {

    private final VirtualKeyRepository virtualKeyRepository;

    public void reserve(VirtualKey key, BigDecimal estimatedCost) {
        int rowsUpdated = virtualKeyRepository.chargeIfWithinBudget(key.getId(), estimatedCost);
        if (rowsUpdated == 0) {
            throw new BudgetExhaustedException(key.getAlias(), key.getMonthlySpendUsd(), key.getMonthlyBudgetUsd());
        }
    }

    public void trueUp(VirtualKey key, BigDecimal estimatedCost, BigDecimal actualCost) {
        BigDecimal delta = actualCost.subtract(estimatedCost);
        if (delta.signum() == 0) {
            return;
        }
        // The reservation already made admission atomic. Re-applying the budget predicate here
        // would drop a positive delta when actual usage is higher than the estimate, producing
        // an incorrect ledger. Reconciliation therefore always adjusts the recorded spend.
        virtualKeyRepository.adjustSpend(key.getId(), delta);
        if (delta.signum() > 0) {
            log.warn("Budget true-up for key {} exceeded its original reservation by {}", key.getAlias(), delta);
        }
    }
}
