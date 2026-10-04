package com.airtribe.prism.common.exception;

/** Thrown when a provider adapter returns a transient error (5xx, connection refused, rate-limited upstream). */
public class ProviderUnavailableException extends PrismException {
    public ProviderUnavailableException(String provider, String reason) {
        super(ErrorCode.PROVIDER_UNAVAILABLE, "Provider '%s' unavailable: %s".formatted(provider, reason));
    }

    public ProviderUnavailableException(String provider, String reason, Throwable cause) {
        super(ErrorCode.PROVIDER_UNAVAILABLE, "Provider '%s' unavailable: %s".formatted(provider, reason), cause);
    }
}
