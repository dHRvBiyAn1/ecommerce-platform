package com.project.common.exception;

import org.springframework.http.HttpStatus;

import static com.project.common.constant.ErrorCode.VALIDATION_FAILED;

public class ValidationException extends BusinessException {
    public ValidationException(String message) {
        super(HttpStatus.BAD_REQUEST, VALIDATION_FAILED.value(), message);
    }

    public ValidationException(String code, String message) {
        super(HttpStatus.BAD_REQUEST, code, message);
    }
}
