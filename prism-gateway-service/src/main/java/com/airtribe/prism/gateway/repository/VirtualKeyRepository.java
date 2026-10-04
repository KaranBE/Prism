package com.airtribe.prism.gateway.repository;

import com.airtribe.prism.gateway.domain.VirtualKey;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Optional;

public interface VirtualKeyRepository extends JpaRepository<VirtualKey, Long> {

    Optional<VirtualKey> findByKeyValueAndActiveTrue(String keyValue);

    /**
     * Atomic "check-and-charge" in a single UPDATE statement instead of
     * read-modify-write. Under concurrent requests on the same key this avoids both
     * the lost-update race a naive read/increment/save would have, and the retry storm
     * that optimistic-locking (@Version) would cause under high contention - the database's
     * own row lock during the UPDATE is the only synchronization we need.
     * Returns 1 if the charge was applied (i.e. spend + cost stayed within budget), else 0.
     */
    @Modifying
    @Transactional
    @Query("""
            UPDATE VirtualKey v
               SET v.monthlySpendUsd = v.monthlySpendUsd + :cost
             WHERE v.id = :id
               AND v.monthlySpendUsd + :cost <= v.monthlyBudgetUsd
            """)
    int chargeIfWithinBudget(@Param("id") Long id, @Param("cost") BigDecimal cost);

    /**
     * Applies the post-provider reconciliation delta. Admission has already been protected by
     * {@link #chargeIfWithinBudget}; this update must not repeat that budget predicate or a
     * positive delta would be silently discarded and the ledger would become inaccurate.
     */
    @Modifying
    @Transactional
    @Query("""
            UPDATE VirtualKey v
               SET v.monthlySpendUsd = v.monthlySpendUsd + :delta
             WHERE v.id = :id
            """)
    int adjustSpend(@Param("id") Long id, @Param("delta") BigDecimal delta);
}
