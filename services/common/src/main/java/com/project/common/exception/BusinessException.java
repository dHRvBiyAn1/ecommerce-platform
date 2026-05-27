package com.project.common.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

/**
 * Base for all domain-level exceptions. Provides a stable error code that the global
 * exception handler turns into structured error responses.
 */
@Getter
public class BusinessException extends RuntimeException {

    private final HttpStatus status;
    private final String code;

    public BusinessException(HttpStatus status, String code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }

    public BusinessException(HttpStatus status, String code, String message, Throwable cause) {
        super(message, cause);
        this.status = status;
        this.code = code;
    }
}
