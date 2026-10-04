package com.airtribe.prism.common.exception;

/** Thrown when a single upstream provider call exceeds its configured timeout budget. */
public class ProviderTimeoutException extends PrismException {
    public ProviderTimeoutException(String provider, long timeoutMillis) {
        super(ErrorCode.PROVIDER_TIMEOUT,
                "Provider '%s' did not respond within %d ms".formatted(provider, timeoutMillis));
    }
}
