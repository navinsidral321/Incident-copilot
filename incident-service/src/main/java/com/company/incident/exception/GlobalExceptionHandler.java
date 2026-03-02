package com.company.incident.exception;

import com.company.common.dto.ApiError;
import com.company.common.exception.BaseException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;
import java.util.stream.Collectors;

/**
 * Central exception handler for incident-service.
 * Converts exceptions into consistent ApiError JSON responses.
 */
@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    @ExceptionHandler(BaseException.class)
    public ResponseEntity<ApiError> handleBase(BaseException ex) {
        log.warn("Business exception: {}", ex.getMessage());
        return ResponseEntity.status(ex.getStatus())
                .body(ApiError.builder()
                        .status(ex.getStatus().value())
                        .error(ex.getStatus().getReasonPhrase())
                        .message(ex.getMessage())
                        .timestamp(Instant.now())
                        .build());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> handleValidation(MethodArgumentNotValidException ex) {
        String details = ex.getBindingResult().getFieldErrors().stream()
                .map(fe -> fe.getField() + ": " + fe.getDefaultMessage())
                .collect(Collectors.joining("; "));

        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiError.builder()
                        .status(400)
                        .error("Validation Failed")
                        .message(details)
                        .timestamp(Instant.now())
                        .build());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleGeneric(Exception ex) {
        log.error("Unexpected error", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiError.builder()
                        .status(500)
                        .error("Internal Server Error")
                        .message("An unexpected error occurred")
                        .timestamp(Instant.now())
                        .build());
    }
}
