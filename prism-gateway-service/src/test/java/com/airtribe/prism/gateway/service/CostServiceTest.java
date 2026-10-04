package com.airtribe.prism.gateway.service;

import com.airtribe.prism.common.dto.Usage;
import com.airtribe.prism.gateway.config.PricingRegistry;
import com.airtribe.prism.gateway.domain.ModelPricing;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class CostServiceTest {

    private CostService costService;

    @BeforeEach
    void setUp() {
        PricingRegistry registry = new PricingRegistry(new ObjectMapper());
        registry.register(new ModelPricing("mock-fast-v1", "mock-provider-a", 0.0002, 0.0006));
        costService = new CostService(registry);
    }

    @Test
    void computesCostFromInputAndOutputPricePer1kTokens() {
        // 1000 prompt tokens @ 0.0002/1k + 500 completion tokens @ 0.0006/1k
        BigDecimal cost = costService.cost("mock-fast-v1", Usage.of(1000, 500));
        BigDecimal expected = BigDecimal.valueOf(0.0002).add(BigDecimal.valueOf(0.0003)).setScale(6, java.math.RoundingMode.HALF_UP);
        assertThat(cost).isEqualByComparingTo(expected);
    }

    @Test
    void zeroTokensCostsZero() {
        assertThat(costService.cost("mock-fast-v1", Usage.of(0, 0))).isEqualByComparingTo(BigDecimal.ZERO);
    }
}
