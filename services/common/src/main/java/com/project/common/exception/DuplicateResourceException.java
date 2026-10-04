package com.project.common.exception;

import org.springframework.http.HttpStatus;

import static com.project.common.constant.ErrorCode.DUPLICATE_RESOURCE;

public class DuplicateResourceException extends BusinessException {
    public DuplicateResourceException(String message) {
        super(HttpStatus.CONFLICT, DUPLICATE_RESOURCE.value(), message);
    }
}
