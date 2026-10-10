package com.project.common.exception;

import static com.project.common.constant.ErrorCode.RESOURCE_NOT_FOUND;

import org.springframework.http.HttpStatus;

public class ResourceNotFoundException extends BusinessException {

  public ResourceNotFoundException(String resource, Object id) {
    super(
        HttpStatus.NOT_FOUND,
        RESOURCE_NOT_FOUND.value(),
        "%s not found: %s".formatted(resource, id));
  }

  public ResourceNotFoundException(String message) {
    super(HttpStatus.NOT_FOUND, RESOURCE_NOT_FOUND.value(), message);
  }
}
