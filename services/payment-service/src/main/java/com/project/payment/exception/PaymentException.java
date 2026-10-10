package com.project.payment.exception;

import static com.project.common.constant.ErrorCode.PAYMENT_ERROR;

import com.project.common.exception.BusinessException;
import org.springframework.http.HttpStatus;

public class PaymentException extends BusinessException {

  public PaymentException(String message) {
    super(HttpStatus.BAD_REQUEST, PAYMENT_ERROR.value(), message);
  }

  public PaymentException(String message, Throwable cause) {
    super(HttpStatus.BAD_REQUEST, PAYMENT_ERROR.value(), message, cause);
  }
}
