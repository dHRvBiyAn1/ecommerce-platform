package com.project.common.exception;

import static com.project.common.constant.ErrorCode.FORBIDDEN;

import org.springframework.http.HttpStatus;

public class ForbiddenOperationException extends BusinessException {
  public ForbiddenOperationException(String message) {
    super(HttpStatus.FORBIDDEN, FORBIDDEN.value(), message);
  }
}
