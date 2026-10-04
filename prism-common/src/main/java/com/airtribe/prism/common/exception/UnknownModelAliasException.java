package com.airtribe.prism.common.exception;

/** Thrown when the requested "model" value does not resolve to any configured alias or provider model. */
public class UnknownModelAliasException extends PrismException {
    public UnknownModelAliasException(String model) {
        super(ErrorCode.UNKNOWN_MODEL_ALIAS, "Unknown model or alias: '%s'".formatted(model));
    }
}
