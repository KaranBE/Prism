package com.airtribe.prism.gateway.exception;

import com.airtribe.prism.common.dto.ErrorResponse;
import com.airtribe.prism.common.exception.ErrorCode;
import com.airtribe.prism.common.exception.PrismException;
import com.airtribe.prism.common.exception.RateLimitExceededException;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.stream.Collectors;

/**
 * Single place every Prism exception - custom (PrismException subtypes) or framework-level
 * (bean validation) - is translated into the documented ErrorResponse envelope and correct
 * HTTP status, so every controller stays free of try/catch boilerplate and every rejection
 * type in the spec ("invalid key, model not allowed, rate limited, budget exhausted") gets a
 * distinct, stable error code a client can branch on.
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(PrismException.class)
    public ResponseEntity<ErrorResponse> handlePrismException(PrismException ex) {
        HttpStatus status = ex.errorCode().httpStatus();
        ResponseEntity.BodyBuilder builder = ResponseEntity.status(status);
        if (ex instanceof RateLimitExceededException rle) {
            builder.header(HttpHeaders.RETRY_AFTER, String.valueOf(rle.retryAfterMillis() / 1000));
            return builder.body(ErrorResponse.of(ex.errorCode(), ex.getMessage(), rle.retryAfterMillis()));
        }
        return builder.body(ErrorResponse.of(ex.errorCode(), ex.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException ex) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                .map(fe -> fe.getField() + ": " + fe.getDefaultMessage())
                .collect(Collectors.joining("; "));
        return ResponseEntity.status(ErrorCode.VALIDATION_FAILED.httpStatus())
                .body(ErrorResponse.of(ErrorCode.VALIDATION_FAILED, message.isBlank() ? "Invalid request body" : message));
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ErrorResponse> handleConstraintViolation(ConstraintViolationException ex) {
        return ResponseEntity.status(ErrorCode.VALIDATION_FAILED.httpStatus())
                .body(ErrorResponse.of(ErrorCode.VALIDATION_FAILED, ex.getMessage()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpected(Exception ex) {
        log.error("Unhandled exception reached GlobalExceptionHandler", ex);
        return ResponseEntity.status(ErrorCode.INTERNAL_ERROR.httpStatus())
                .body(ErrorResponse.of(ErrorCode.INTERNAL_ERROR, "An unexpected error occurred."));
    }
}
