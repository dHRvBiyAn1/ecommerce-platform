package com.project.common.exception;

import org.springframework.http.HttpStatus;

import static com.project.common.constant.ErrorCode.FORBIDDEN;

public class ForbiddenOperationException extends BusinessException {
    public ForbiddenOperationException(String message) {
        super(HttpStatus.FORBIDDEN, FORBIDDEN.value(), message);
    }
}
