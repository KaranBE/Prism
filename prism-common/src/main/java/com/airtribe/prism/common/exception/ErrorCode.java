package com.airtribe.prism.common.exception;

import org.springframework.http.HttpStatus;

/**
 * Every distinct failure mode Prism can produce, each bound to one HTTP status.
 * Keeping this as a closed enum (rather than free-form strings scattered across
 * the codebase) means the API contract for error handling is enumerable and testable -
 * a client can switch on {@code error.code} instead of parsing messages.
 */
public enum ErrorCode {

    INVALID_VIRTUAL_KEY(HttpStatus.UNAUTHORIZED, "The virtual key is missing, unknown, or revoked."),
    MODEL_NOT_ALLOWED(HttpStatus.FORBIDDEN, "This virtual key is not permitted to call the requested model/alias."),
    RATE_LIMITED(HttpStatus.TOO_MANY_REQUESTS, "Requests-per-minute limit exceeded for this virtual key."),
    BUDGET_EXHAUSTED(HttpStatus.PAYMENT_REQUIRED, "Monthly cost budget exhausted for this virtual key."),
    VALIDATION_FAILED(HttpStatus.BAD_REQUEST, "The request body failed validation."),
    PROVIDER_TIMEOUT(HttpStatus.GATEWAY_TIMEOUT, "The upstream provider did not respond within the configured timeout."),
    PROVIDER_UNAVAILABLE(HttpStatus.BAD_GATEWAY, "The upstream provider returned an error or is degraded."),
    ALL_PROVIDERS_EXHAUSTED(HttpStatus.SERVICE_UNAVAILABLE, "Primary and every fallback provider in the chain failed."),
    UNKNOWN_MODEL_ALIAS(HttpStatus.BAD_REQUEST, "The requested model/alias is not configured on this gateway."),
    CACHE_SERVICE_UNAVAILABLE(HttpStatus.OK, "Semantic cache lookup failed open; request proceeded to provider."),
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "An unexpected error occurred inside Prism.");

    private final HttpStatus httpStatus;
    private final String defaultMessage;

    ErrorCode(HttpStatus httpStatus, String defaultMessage) {
        this.httpStatus = httpStatus;
        this.defaultMessage = defaultMessage;
    }

    public HttpStatus httpStatus() {
        return httpStatus;
    }

    public String defaultMessage() {
        return defaultMessage;
    }
}
