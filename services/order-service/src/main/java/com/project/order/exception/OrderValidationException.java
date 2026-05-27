package com.project.order.exception;

import com.project.common.exception.BusinessException;
import org.springframework.http.HttpStatus;

public class OrderValidationException extends BusinessException {
    public OrderValidationException(String message) {
        super(HttpStatus.BAD_REQUEST, "ORDER_VALIDATION", message);
    }
}
