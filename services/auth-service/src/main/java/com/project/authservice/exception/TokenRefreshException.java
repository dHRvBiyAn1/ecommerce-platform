package com.project.authservice.exception;

import com.project.common.constant.ErrorCode;
import com.project.common.exception.BusinessException;
import org.springframework.http.HttpStatus;

public class TokenRefreshException extends BusinessException {
    public TokenRefreshException(String message) {
        super(HttpStatus.FORBIDDEN, ErrorCode.FORBIDDEN.value(), message);
    }
}
