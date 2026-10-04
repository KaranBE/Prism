package com.airtribe.prism.gateway.controller;

import com.airtribe.prism.gateway.domain.RequestLog;
import com.airtribe.prism.gateway.domain.VirtualKey;
import com.airtribe.prism.gateway.repository.RequestLogRepository;
import com.airtribe.prism.gateway.security.RequestAttributes;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;

/** Usage summary + recent request log API for the authenticated virtual key, backing the ops console. */
@RestController
@RequiredArgsConstructor
public class UsageController {

    private final RequestLogRepository requestLogRepository;

    @GetMapping("/v1/usage/summary")
    public Map<String, Object> summary(HttpServletRequest httpRequest,
                                        @RequestParam(name = "days", defaultValue = "30") int days) {
        VirtualKey key = (VirtualKey) httpRequest.getAttribute(RequestAttributes.VIRTUAL_KEY);
        Instant since = Instant.now().minus(days, ChronoUnit.DAYS);

        BigDecimal totalCost = requestLogRepository.sumCostSince(key.getId(), since);
        long totalRequests = requestLogRepository.countByVirtualKeyIdAndCreatedAtGreaterThanEqual(key.getId(), since);
        long cacheHits = requestLogRepository.countCacheHitsSince(key.getId(), since);

        return Map.of(
                "keyAlias", key.getAlias(),
                "windowDays", days,
                "totalRequests", totalRequests,
                "totalCostUsd", totalCost,
                "monthlyBudgetUsd", key.getMonthlyBudgetUsd(),
                "monthlySpendUsd", key.getMonthlySpendUsd(),
                "cacheHits", cacheHits,
                "cacheHitRate", totalRequests == 0 ? 0.0 : (double) cacheHits / totalRequests
        );
    }

    @GetMapping("/v1/usage/requests")
    public Page<RequestLog> recentRequests(HttpServletRequest httpRequest,
                                            @RequestParam(name = "page", defaultValue = "0") int page,
                                            @RequestParam(name = "size", defaultValue = "25") int size) {
        VirtualKey key = (VirtualKey) httpRequest.getAttribute(RequestAttributes.VIRTUAL_KEY);
        return requestLogRepository.findByVirtualKeyIdOrderByCreatedAtDesc(
                key.getId(), PageRequest.of(page, Math.min(size, 100), Sort.by("createdAt").descending()));
    }
}
