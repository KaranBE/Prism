package com.airtribe.prism.common.dto;

import com.airtribe.prism.common.exception.ErrorCode;

/** The single, documented error envelope every Prism error response uses. */
public record ErrorResponse(ErrorDetail error) {

    public record ErrorDetail(String code, String message, String type, Long retryAfterMillis) {
    }

    public static ErrorResponse of(ErrorCode code, String message) {
        return new ErrorResponse(new ErrorDetail(code.name(), message, code.httpStatus().name(), null));
    }

    public static ErrorResponse of(ErrorCode code, String message, long retryAfterMillis) {
        return new ErrorResponse(new ErrorDetail(code.name(), message, code.httpStatus().name(), retryAfterMillis));
    }
}
