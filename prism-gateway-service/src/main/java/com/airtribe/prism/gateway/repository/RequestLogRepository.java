package com.airtribe.prism.gateway.repository;

import com.airtribe.prism.gateway.domain.RequestLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.Instant;

public interface RequestLogRepository extends JpaRepository<RequestLog, Long> {

    // Keyset-style paging by created_at (via Pageable) hits the composite index defined on
    // the entity directly - no filesort, no offset-scan cost blowing up on deep pages.
    Page<RequestLog> findByVirtualKeyIdOrderByCreatedAtDesc(Long virtualKeyId, Pageable pageable);

    @Query("""
            SELECT COALESCE(SUM(r.costUsd), 0) FROM RequestLog r
             WHERE r.virtualKeyId = :keyId AND r.createdAt >= :since
            """)
    BigDecimal sumCostSince(@Param("keyId") Long keyId, @Param("since") Instant since);

    @Query("""
            SELECT COUNT(r) FROM RequestLog r
             WHERE r.virtualKeyId = :keyId AND r.cacheHit = true AND r.createdAt >= :since
            """)
    long countCacheHitsSince(@Param("keyId") Long keyId, @Param("since") Instant since);

    long countByVirtualKeyIdAndCreatedAtGreaterThanEqual(Long virtualKeyId, Instant since);
}
