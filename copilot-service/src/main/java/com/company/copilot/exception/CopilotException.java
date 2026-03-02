package com.company.copilot.exception;

import com.company.common.exception.BaseException;
import org.springframework.http.HttpStatus;

public class CopilotException extends BaseException {

    public CopilotException(String message) {
        super(message, HttpStatus.INTERNAL_SERVER_ERROR);
    }

    public CopilotException(String message, Throwable cause) {
        super(message, HttpStatus.INTERNAL_SERVER_ERROR, cause);
    }
}
