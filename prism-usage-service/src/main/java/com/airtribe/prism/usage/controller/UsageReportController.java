package com.airtribe.prism.usage.controller;

import com.airtribe.prism.usage.service.UsageAggregationService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/** Cross-tenant reporting for the ops console: spend and fallback rate by provider, platform-wide. */
@RestController
@CrossOrigin(origins = "*")
@RequiredArgsConstructor
public class UsageReportController {

    private final UsageAggregationService aggregationService;

    @GetMapping("/usage/report")
    public Map<String, Object> report(@RequestParam(name = "days", defaultValue = "7") int days) {
        return aggregationService.report(days);
    }
}
