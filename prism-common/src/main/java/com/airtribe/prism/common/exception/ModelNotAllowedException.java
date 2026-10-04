package com.airtribe.prism.common.exception;

/** Thrown when a virtual key requests a model/alias outside its configured allowlist. */
public class ModelNotAllowedException extends PrismException {
    public ModelNotAllowedException(String keyAlias, String model) {
        super(ErrorCode.MODEL_NOT_ALLOWED,
                "Virtual key '%s' is not allowed to call model '%s'".formatted(keyAlias, model));
    }
}
