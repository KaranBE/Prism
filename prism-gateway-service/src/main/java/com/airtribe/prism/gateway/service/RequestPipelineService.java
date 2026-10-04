package com.airtribe.prism.gateway.service;

import com.airtribe.prism.common.dto.ChatCompletionRequest;
import com.airtribe.prism.common.dto.ChatMessage;
import com.airtribe.prism.common.dto.Usage;
import com.airtribe.prism.gateway.domain.RequestLog;
import com.airtribe.prism.gateway.domain.VirtualKey;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Shared orchestration steps used by both the streaming and non-streaming completion paths:
 * routing, budget reservation/true-up, and building the RequestLog row. Kept in one place so
 * the two controller paths cannot drift into inconsistent behaviour.
 */
@Service
@RequiredArgsConstructor
public class RequestPipelineService {

    private final RoutingService routingService;
    private final BudgetService budgetService;
    private final CostService costService;

    public RoutingPlan resolveRoute(VirtualKey key, ChatCompletionRequest request) {
        return routingService.resolve(key, request);
    }

    /** Rough pre-flight token estimate: ~4 chars/token for the prompt, capped completion guess. */
    public BigDecimal reserveEstimatedBudget(VirtualKey key, String model, ChatCompletionRequest request) {
        int promptTokens = request.messages().stream().mapToInt(m -> Math.max(1, m.content().length() / 4)).sum();
        int assumedCompletionTokens = request.maxTokens() != null ? request.maxTokens() : 256;
        BigDecimal estimate = costService.estimate(model, promptTokens, assumedCompletionTokens);
        budgetService.reserve(key, estimate);
        return estimate;
    }

    public void refund(VirtualKey key, BigDecimal estimatedCost) {
        budgetService.trueUp(key, estimatedCost, BigDecimal.ZERO);
    }

    public BigDecimal trueUpToActual(VirtualKey key, String model, BigDecimal estimatedCost, Usage actualUsage) {
        BigDecimal actualCost = costService.cost(model, actualUsage);
        budgetService.trueUp(key, estimatedCost, actualCost);
        return actualCost;
    }

    public String promptText(ChatCompletionRequest request) {
        StringBuilder sb = new StringBuilder();
        for (ChatMessage m : request.messages()) {
            sb.append(m.role()).append(": ").append(m.content()).append('\n');
        }
        return sb.toString();
    }

    public RequestLog.RequestLogBuilder logBuilder(VirtualKey key, ChatCompletionRequest request, RoutingPlan plan) {
        return RequestLog.builder()
                .virtualKeyId(key.getId())
                .requestedModel(request.model())
                .resolvedModel(plan.primaryModel())
                .createdAt(Instant.now());
    }
}
