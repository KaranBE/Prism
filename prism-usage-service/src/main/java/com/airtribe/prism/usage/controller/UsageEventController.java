package com.airtribe.prism.usage.controller;

import com.airtribe.prism.usage.dto.UsageEventDto;
import com.airtribe.prism.usage.service.UsageAggregationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Ingestion endpoint the gateway's UsageLoggingService forwards its flushed batches to. */
@RestController
@RequiredArgsConstructor
public class UsageEventController {

    private final UsageAggregationService aggregationService;

    @PostMapping("/usage/events")
    public ResponseEntity<Void> ingest(@Valid @RequestBody List<UsageEventDto> events) {
        aggregationService.ingest(events);
        return ResponseEntity.accepted().build();
    }
}
