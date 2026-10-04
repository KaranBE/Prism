package com.airtribe.prism.gateway.controller;

import com.airtribe.prism.common.dto.ChatCompletionRequest;
import com.airtribe.prism.common.dto.ChatCompletionResponse;
import com.airtribe.prism.common.dto.Usage;
import com.airtribe.prism.gateway.domain.RequestLog;
import com.airtribe.prism.gateway.domain.VirtualKey;
import com.airtribe.prism.gateway.adapter.ProviderAdapterFactory;
import com.airtribe.prism.gateway.security.RequestAttributes;
import com.airtribe.prism.gateway.service.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * The single OpenAI-compatible data-plane endpoint. Request flow:
 * auth (done by VirtualKeyAuthFilter before this method is even entered) -> validate body
 * -> resolve route (allowlist + alias/auto) -> semantic-cache lookup -> [cache hit: return
 * immediately] / [miss: reserve budget, dispatch to provider with retry+failover, true-up
 * budget, cache the result] -> log usage -> respond with the x-prism-* header contract.
 */
@RestController
@RequiredArgsConstructor
public class ChatCompletionController {

    private final RequestPipelineService pipelineService;
    private final CacheClientService cacheClientService;
    private final ProviderDispatchService dispatchService;
    private final UsageLoggingService usageLoggingService;
    private final StreamingService streamingService;
    private final ProviderAdapterFactory providerAdapterFactory;

    @PostMapping(value = "/v1/chat/completions")
    public ResponseEntity<?> completions(@Valid @RequestBody ChatCompletionRequest request, HttpServletRequest httpRequest) {
        VirtualKey key = (VirtualKey) httpRequest.getAttribute(RequestAttributes.VIRTUAL_KEY);
        long startNanos = System.nanoTime();

        RoutingPlan plan = pipelineService.resolveRoute(key, request);
        String promptText = pipelineService.promptText(request);

        if (request.isStreaming()) {
            BigDecimal estimate = pipelineService.reserveEstimatedBudget(key, plan.primaryModel(), request);
            // HTTP headers are committed before the first SSE byte.  These values describe the
            // selected initial route; prism-meta carries the authoritative final values if a
            // pre-stream failure causes failover.
            return ResponseEntity.ok()
                    .contentType(MediaType.TEXT_EVENT_STREAM)
                    .header("x-prism-provider", providerAdapterFactory.forModel(plan.primaryModel()).providerName())
                    .header("x-prism-cache", "MISS")
                    .header("x-prism-fallback", "false")
                    .header("x-prism-cost-usd", "pending")
                    .body(streamingService.stream(key, request, plan, estimate, promptText, startNanos));
        }

        return handleNonStreaming(key, request, plan, promptText, startNanos);
    }

    private ResponseEntity<ChatCompletionResponse> handleNonStreaming(VirtualKey key, ChatCompletionRequest request,
                                                                        RoutingPlan plan, String promptText, long startNanos) {
        CacheLookupResult cacheResult = cacheClientService.lookup(key.getAlias(), plan.primaryModel(), promptText)
                .blockOptional(java.time.Duration.ofMillis(1500))
                .orElse(CacheLookupResult.MISS);

        if (cacheResult.hit()) {
            usageLoggingService.enqueue(pipelineService.logBuilder(key, request, plan)
                    .provider("cache")
                    .status("SUCCESS")
                    .promptTokens(cacheResult.usage().promptTokens())
                    .completionTokens(cacheResult.usage().completionTokens())
                    .costUsd(BigDecimal.ZERO)
                    .cacheHit(true)
                    .fallbackUsed(false)
                    .latencyMs((System.nanoTime() - startNanos) / 1_000_000)
                    .build());

            ChatCompletionResponse body = ChatCompletionResponse.of(
                    "chatcmpl-" + java.util.UUID.randomUUID(), plan.primaryModel(), cacheResult.content(), cacheResult.usage());
            return ResponseEntity.ok()
                    .header("x-prism-provider", "cache")
                    .header("x-prism-cache", "HIT")
                    .header("x-prism-fallback", "false")
                    .header("x-prism-cost-usd", "0.000000")
                    .body(body);
        }

        BigDecimal estimatedCost = pipelineService.reserveEstimatedBudget(key, plan.primaryModel(), request);
        try {
            ProviderDispatchService.DispatchResult result = dispatchService.dispatch(plan, request)
                    .block(java.time.Duration.ofSeconds(30));

            BigDecimal actualCost = pipelineService.trueUpToActual(key, result.resolvedModel(), estimatedCost, result.usage());
            cacheClientService.storeAsync(key.getAlias(), result.resolvedModel(), promptText, result.content(), result.usage());

            usageLoggingService.enqueue(pipelineService.logBuilder(key, request, plan)
                    .resolvedModel(result.resolvedModel())
                    .provider(result.provider())
                    .status("SUCCESS")
                    .promptTokens(result.usage().promptTokens())
                    .completionTokens(result.usage().completionTokens())
                    .costUsd(actualCost)
                    .cacheHit(false)
                    .fallbackUsed(result.fallbackUsed())
                    .latencyMs((System.nanoTime() - startNanos) / 1_000_000)
                    .build());

            ChatCompletionResponse body = ChatCompletionResponse.of(
                    "chatcmpl-" + java.util.UUID.randomUUID(), result.resolvedModel(), result.content(), result.usage());
            return ResponseEntity.ok()
                    .header("x-prism-provider", result.provider())
                    .header("x-prism-cache", "MISS")
                    .header("x-prism-fallback", String.valueOf(result.fallbackUsed()))
                    .header("x-prism-cost-usd", actualCost.toPlainString())
                    .body(body);
        } catch (RuntimeException ex) {
            pipelineService.refund(key, estimatedCost);
            usageLoggingService.enqueue(pipelineService.logBuilder(key, request, plan)
                    .status("ERROR:" + rootCauseName(ex))
                    .cacheHit(false).fallbackUsed(true)
                    .latencyMs((System.nanoTime() - startNanos) / 1_000_000)
                    .build());
            throw ex; // translated to the documented error envelope by GlobalExceptionHandler
        }
    }

    private String rootCauseName(Throwable ex) {
        Throwable t = ex;
        while (t.getCause() != null && t.getCause() != t) {
            t = t.getCause();
        }
        return t.getClass().getSimpleName();
    }
}
