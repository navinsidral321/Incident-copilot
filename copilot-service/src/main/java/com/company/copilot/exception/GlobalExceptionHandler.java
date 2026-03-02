package com.company.copilot.exception;

import com.company.common.dto.ApiError;
import com.company.common.exception.BaseException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;

@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    @ExceptionHandler(BaseException.class)
    public ResponseEntity<ApiError> handleBase(BaseException ex) {
        log.warn("Copilot exception: {}", ex.getMessage());
        return ResponseEntity.status(ex.getStatus())
                .body(ApiError.builder()
                        .status(ex.getStatus().value())
                        .error(ex.getStatus().getReasonPhrase())
                        .message(ex.getMessage())
                        .timestamp(Instant.now())
                        .build());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleGeneric(Exception ex) {
        log.error("Unexpected copilot error", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiError.builder()
                        .status(500).error("Internal Server Error")
                        .message("AI analysis service encountered an error")
                        .timestamp(Instant.now())
                        .build());
    }
}
