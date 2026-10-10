package com.project.common.web;

import com.project.common.generated.model.ErrorResponse;
import com.project.common.generated.model.PageEnvelope;
import com.project.common.generated.model.ResponseEnvelope;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;

/** Creates response metadata around contract-generated models. */
public final class Responses {
  private Responses() {}

  public static <T> ResponseEnvelope<T> success(T data) {
    return envelope(200, "Success", data);
  }

  public static <T> ResponseEnvelope<T> success(String message, T data) {
    return envelope(200, message, data);
  }

  public static <T> ResponseEnvelope<T> created(T data) {
    return envelope(201, "Created", data);
  }

  public static <T> ResponseEnvelope<T> error(int status, String message) {
    return envelope(status, message, null);
  }

  public static <T> ResponseEnvelope<T> error(int status, String message, T data) {
    return envelope(status, message, data);
  }

  public static <T> ResponseEnvelope<T> envelope(int status, String message, T data) {
    return new ResponseEnvelope<>(
        status, message, data, UUID.randomUUID().toString(), Instant.now());
  }

  public static <T> PageEnvelope<T> page(Page<T> page) {
    return new PageEnvelope<>(
        page.getContent(),
        page.getNumber(),
        page.getSize(),
        page.getTotalElements(),
        page.getTotalPages(),
        page.isFirst(),
        page.isLast());
  }

  public static ErrorResponse errorResponse(
      int status,
      String code,
      String message,
      String path,
      Map<String, String> fieldErrors,
      String traceId) {
    HttpStatus resolved = HttpStatus.resolve(status);
    return new ErrorResponse(
        status,
        resolved == null ? "Unknown Status" : resolved.getReasonPhrase(),
        message,
        path,
        code,
        fieldErrors,
        traceId,
        Instant.now());
  }
}
