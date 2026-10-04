package com.airtribe.prism.common.exception;

/**
 * Root of Prism's custom exception hierarchy. Every business-rule rejection
 * (bad key, rate limit, budget, provider failure, ...) extends this rather than
 * throwing raw RuntimeExceptions, so {@code GlobalExceptionHandler} can translate
 * every one of them into the documented error-envelope + correct HTTP status
 * without a giant if/else chain of instanceof checks.
 */
public abstract class PrismException extends RuntimeException {

    private final ErrorCode errorCode;

    protected PrismException(ErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    protected PrismException(ErrorCode errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
    }

    public ErrorCode errorCode() {
        return errorCode;
    }
}
