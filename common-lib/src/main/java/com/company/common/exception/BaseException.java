package com.company.common.exception;

import org.springframework.http.HttpStatus;

/**
 * Root exception for all services.
 * Subclasses carry an HTTP status so GlobalExceptionHandlers
 * can respond with the correct HTTP code automatically.
 */
public abstract class BaseException extends RuntimeException {

    private final HttpStatus status;

    protected BaseException(String message, HttpStatus status) {
        super(message);
        this.status = status;
    }

    protected BaseException(String message, HttpStatus status, Throwable cause) {
        super(message, cause);
        this.status = status;
    }

    public HttpStatus getStatus() {
        return status;
    }
}
