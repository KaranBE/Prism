package com.airtribe.prism.cache.repository;

import com.airtribe.prism.cache.domain.CacheEntry;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

public interface CacheEntryRepository extends JpaRepository<CacheEntry, Long> {

    List<CacheEntry> findByExpiresAtAfter(Instant now);

    List<CacheEntry> findByExpiresAtLessThanEqual(Instant now);

    @Modifying
    @Query("DELETE FROM CacheEntry c WHERE c.expiresAt <= :now")
    int deleteExpired(@Param("now") Instant now);
}
