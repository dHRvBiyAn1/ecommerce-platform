package com.project.authservice.exception;

import com.project.common.constant.ErrorCode;
import com.project.common.exception.BusinessException;
import org.springframework.http.HttpStatus;

public class AuthException extends BusinessException {
    public AuthException(String message) {
        super(HttpStatus.UNAUTHORIZED, ErrorCode.UNAUTHENTICATED.value(), message);
    }
}
