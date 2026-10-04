package com.airtribe.prism.gateway.security;

import com.airtribe.prism.common.dto.ErrorResponse;
import com.airtribe.prism.common.exception.PrismException;
import com.airtribe.prism.common.exception.RateLimitExceededException;
import com.airtribe.prism.gateway.domain.VirtualKey;
import com.airtribe.prism.gateway.service.RateLimiterService;
import com.airtribe.prism.gateway.service.VirtualKeyService;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Cross-cutting gate every /v1/** request passes through before it reaches a controller:
 * resolve + validate the virtual key, then check its requests-per-minute budget. Both checks
 * are cheap and model-agnostic, so they belong here rather than duplicated inside every
 * controller method. Model-allowlist and cost-budget checks depend on the parsed request body
 * and happen downstream in RoutingService/BudgetService.
 *
 * On success the resolved VirtualKey is stashed as a request attribute; controllers read it
 * once, synchronously, at the top of the method (safe to then close over in a reactive/async
 * streaming pipeline, unlike a ThreadLocal which would not reliably survive onto whatever
 * thread the streaming publisher executes on).
 */
@RequiredArgsConstructor
public class VirtualKeyAuthFilter extends OncePerRequestFilter {

    private final VirtualKeyService virtualKeyService;
    private final RateLimiterService rateLimiterService;
    private final ObjectMapper objectMapper;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        try {
            String rawKey = extractKey(request);
            VirtualKey key = virtualKeyService.authenticate(rawKey);
            rateLimiterService.checkAndConsume(key);
            request.setAttribute(RequestAttributes.VIRTUAL_KEY, key);
            chain.doFilter(request, response);
        } catch (PrismException ex) {
            writeError(response, ex);
        }
    }

    private String extractKey(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        if (header != null && header.regionMatches(true, 0, "Bearer ", 0, 7)) {
            return header.substring(7).trim();
        }
        String apiKeyHeader = request.getHeader("x-prism-key");
        return apiKeyHeader == null ? "" : apiKeyHeader.trim();
    }

    private void writeError(HttpServletResponse response, PrismException ex) throws IOException {
        response.setStatus(ex.errorCode().httpStatus().value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        ErrorResponse body = ex instanceof RateLimitExceededException rle
                ? ErrorResponse.of(ex.errorCode(), ex.getMessage(), rle.retryAfterMillis())
                : ErrorResponse.of(ex.errorCode(), ex.getMessage());
        if (ex instanceof RateLimitExceededException rle) {
            response.setHeader("Retry-After", String.valueOf(rle.retryAfterMillis() / 1000));
        }
        objectMapper.writeValue(response.getWriter(), body);
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        // health/docs/ops endpoints stay open; everything under /v1/** requires a virtual key
        return !path.startsWith("/v1/");
    }
}
