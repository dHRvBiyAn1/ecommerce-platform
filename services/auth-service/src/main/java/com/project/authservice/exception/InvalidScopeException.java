package com.project.authservice.exception;

import com.project.common.exception.BusinessException;
import org.springframework.http.HttpStatus;

public class InvalidScopeException extends BusinessException {
    private static final String CODE = "INVALID_SCOPE";

    public InvalidScopeException(String message) {
        super(HttpStatus.BAD_REQUEST, CODE, message);
    }
}
