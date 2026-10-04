package com.airtribe.prism.usage.repository;

import com.airtribe.prism.usage.domain.UsageRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public interface UsageRecordRepository extends JpaRepository<UsageRecord, Long> {

    @Query("""
            SELECT new com.airtribe.prism.usage.dto.ProviderBreakdown(
                       u.provider, COUNT(u), COALESCE(SUM(u.costUsd), 0), COALESCE(AVG(u.latencyMs), 0))
              FROM UsageRecord u
             WHERE u.createdAt >= :since AND u.provider IS NOT NULL
          GROUP BY u.provider
          ORDER BY COUNT(u) DESC
            """)
    List<com.airtribe.prism.usage.dto.ProviderBreakdown> breakdownByProvider(@Param("since") Instant since);

    @Query("SELECT COALESCE(SUM(u.costUsd), 0) FROM UsageRecord u WHERE u.createdAt >= :since")
    BigDecimal totalSpendSince(@Param("since") Instant since);

    @Query("SELECT COUNT(u) FROM UsageRecord u WHERE u.createdAt >= :since AND u.fallbackUsed = true")
    long countFallbacksSince(@Param("since") Instant since);
}
