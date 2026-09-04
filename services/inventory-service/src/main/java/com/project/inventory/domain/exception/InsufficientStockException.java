package com.project.inventory.domain.exception;

import com.project.common.exception.BusinessException;
import org.springframework.http.HttpStatus;

import static com.project.common.constant.ErrorCode.INSUFFICIENT_STOCK;

public class InsufficientStockException extends BusinessException {
    public InsufficientStockException(String message) {
        super(HttpStatus.CONFLICT, INSUFFICIENT_STOCK.value(), message);
    }

    public InsufficientStockException(String message, Throwable cause) {
        super(HttpStatus.CONFLICT, INSUFFICIENT_STOCK.value(), message, cause);
    }
}
