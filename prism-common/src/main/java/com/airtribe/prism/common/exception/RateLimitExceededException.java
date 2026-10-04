package com.airtribe.prism.common.exception;

/** Thrown by the token-bucket limiter when a key exceeds its requests-per-minute quota. */
public class RateLimitExceededException extends PrismException {
    private final long retryAfterMillis;

    public RateLimitExceededException(String keyAlias, long retryAfterMillis) {
        super(ErrorCode.RATE_LIMITED,
                "Virtual key '%s' exceeded its requests-per-minute limit".formatted(keyAlias));
        this.retryAfterMillis = retryAfterMillis;
    }

    public long retryAfterMillis() {
        return retryAfterMillis;
    }
}
