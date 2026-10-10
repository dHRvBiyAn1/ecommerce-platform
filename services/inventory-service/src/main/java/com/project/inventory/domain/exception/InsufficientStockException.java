package com.project.inventory.domain.exception;

import static com.project.common.constant.ErrorCode.INSUFFICIENT_STOCK;

import com.project.common.exception.BusinessException;
import org.springframework.http.HttpStatus;

public class InsufficientStockException extends BusinessException {
  public InsufficientStockException(String message) {
    super(HttpStatus.CONFLICT, INSUFFICIENT_STOCK.value(), message);
  }

  public InsufficientStockException(String message, Throwable cause) {
    super(HttpStatus.CONFLICT, INSUFFICIENT_STOCK.value(), message, cause);
  }
}
