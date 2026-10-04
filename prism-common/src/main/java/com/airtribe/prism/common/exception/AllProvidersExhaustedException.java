package com.airtribe.prism.common.exception;

import java.util.List;

/** Thrown when the primary model and every model in its fallback chain have failed. */
public class AllProvidersExhaustedException extends PrismException {
    public AllProvidersExhaustedException(String alias, List<String> attemptedChain) {
        super(ErrorCode.ALL_PROVIDERS_EXHAUSTED,
                "Alias '%s': every model in fallback chain %s failed".formatted(alias, attemptedChain));
    }
}
