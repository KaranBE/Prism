package com.airtribe.prism.gateway.service;

import com.airtribe.prism.common.dto.Usage;
import com.airtribe.prism.gateway.config.PricingRegistry;
import com.airtribe.prism.gateway.domain.ModelPricing;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;

/** Computes USD cost for a request from the price table and provider-reported token usage. */
@Service
@RequiredArgsConstructor
public class CostService {

    private final PricingRegistry pricingRegistry;

    public BigDecimal cost(String model, Usage usage) {
        ModelPricing pricing = pricingRegistry.get(model);
        BigDecimal inputCost = BigDecimal.valueOf(usage.promptTokens())
                .multiply(BigDecimal.valueOf(pricing.inputPricePer1k()))
                .divide(BigDecimal.valueOf(1000), MathContext.DECIMAL64);
        BigDecimal outputCost = BigDecimal.valueOf(usage.completionTokens())
                .multiply(BigDecimal.valueOf(pricing.outputPricePer1k()))
                .divide(BigDecimal.valueOf(1000), MathContext.DECIMAL64);
        return inputCost.add(outputCost).setScale(6, RoundingMode.HALF_UP);
    }

    /** Rough pre-flight cost estimate used only to reserve budget before the real token count is known. */
    public BigDecimal estimate(String model, int estimatedPromptTokens, int estimatedCompletionTokens) {
        return cost(model, Usage.of(estimatedPromptTokens, estimatedCompletionTokens));
    }
}
