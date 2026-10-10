package com.project.common.exception;

import static com.project.common.constant.ErrorCode.VALIDATION_FAILED;

import org.springframework.http.HttpStatus;

public class ValidationException extends BusinessException {
  public ValidationException(String message) {
    super(HttpStatus.BAD_REQUEST, VALIDATION_FAILED.value(), message);
  }

  public ValidationException(String code, String message) {
    super(HttpStatus.BAD_REQUEST, code, message);
  }
}
