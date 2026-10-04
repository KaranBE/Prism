package com.airtribe.prism.usage.service;

import com.airtribe.prism.usage.domain.UsageRecord;
import com.airtribe.prism.usage.dto.ProviderBreakdown;
import com.airtribe.prism.usage.dto.UsageEventDto;
import com.airtribe.prism.usage.repository.UsageRecordRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class UsageAggregationService {

    private final UsageRecordRepository repository;

    @Transactional
    public void ingest(List<UsageEventDto> events) {
        // Batch-mapped and saved in one round trip (saveAll) rather than one INSERT per event -
        // this endpoint receives whole batches from the gateway's own flush cycle already, so
        // preserving that batching all the way to the DB write matters just as much here.
        List<UsageRecord> records = events.stream().map(this::toEntity).toList();
        repository.saveAll(records);
    }

    public Map<String, Object> report(int days) {
        Instant since = Instant.now().minusSeconds(days * 86_400L);
        BigDecimal totalSpend = repository.totalSpendSince(since);
        long fallbacks = repository.countFallbacksSince(since);
        List<ProviderBreakdown> byProvider = repository.breakdownByProvider(since);
        return Map.of(
                "windowDays", days,
                "totalSpendUsd", totalSpend,
                "fallbackCount", fallbacks,
                "byProvider", byProvider
        );
    }

    private UsageRecord toEntity(UsageEventDto dto) {
        UsageRecord record = new UsageRecord();
        record.setVirtualKeyId(dto.virtualKeyId());
        record.setRequestedModel(dto.requestedModel());
        record.setResolvedModel(dto.resolvedModel());
        record.setProvider(dto.provider());
        record.setStatus(dto.status());
        record.setPromptTokens(dto.promptTokens());
        record.setCompletionTokens(dto.completionTokens());
        record.setCostUsd(dto.costUsd() == null ? BigDecimal.ZERO : new BigDecimal(dto.costUsd()));
        record.setCacheHit(dto.cacheHit());
        record.setFallbackUsed(dto.fallbackUsed());
        record.setLatencyMs(dto.latencyMs());
        record.setCreatedAt(dto.createdAt() == null ? Instant.now() : Instant.parse(dto.createdAt()));
        return record;
    }
}
