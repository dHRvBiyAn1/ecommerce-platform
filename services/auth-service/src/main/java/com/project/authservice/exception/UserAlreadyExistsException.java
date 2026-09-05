package com.project.authservice.exception;

import com.project.common.constant.ErrorCode;
import com.project.common.exception.BusinessException;
import org.springframework.http.HttpStatus;

public class UserAlreadyExistsException extends BusinessException {
    public UserAlreadyExistsException(String message) {
        super(HttpStatus.CONFLICT, ErrorCode.DUPLICATE_RESOURCE.value(), message);
    }
}
