package com.airtribe.prism.common.exception;

/** Thrown when the caller's virtual key is missing, unknown, or has been revoked. */
public class InvalidVirtualKeyException extends PrismException {
    public InvalidVirtualKeyException(String message) {
        super(ErrorCode.INVALID_VIRTUAL_KEY, message);
    }
}
