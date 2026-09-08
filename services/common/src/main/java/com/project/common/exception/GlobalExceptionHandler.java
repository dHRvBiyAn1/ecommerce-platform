package com.project.common.exception;

import com.project.common.dto.ApiResponse;
import com.project.common.dto.ErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static com.project.common.constant.ErrorCode.ACCESS_DENIED;
import static com.project.common.constant.ErrorCode.BAD_CREDENTIALS;
import static com.project.common.constant.ErrorCode.COUPON_RESERVATION_CONFLICT;
import static com.project.common.constant.ErrorCode.COUPON_UNAVAILABLE;
import static com.project.common.constant.ErrorCode.DUPLICATE_RESOURCE;
import static com.project.common.constant.ErrorCode.INTERNAL_ERROR;
import static com.project.common.constant.ErrorCode.INSUFFICIENT_STOCK;
import static com.project.common.constant.ErrorCode.INVALID_COUPON_REQUEST;
import static com.project.common.constant.ErrorCode.METHOD_NOT_ALLOWED;
import static com.project.common.constant.ErrorCode.NOT_FOUND;
import static com.project.common.constant.ErrorCode.OAUTH2_PROVIDER_NOT_CONFIGURED;
import static com.project.common.constant.ErrorCode.RESOURCE_NOT_FOUND;
import static com.project.common.constant.ErrorCode.TYPE_MISMATCH;
import static com.project.common.constant.ErrorCode.UNAUTHENTICATED;
import static com.project.common.constant.ErrorCode.VALIDATION_FAILED;

/**
 * Single global exception handler for every service. Each service should not redefine
 * its own; just import this through the common module by component scanning the package.
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Set<String> SAFE_PUBLIC_CODES = Set.of(
            VALIDATION_FAILED.value(),
            "ORDER_VALIDATION",
            RESOURCE_NOT_FOUND.value(),
            NOT_FOUND.value(),
            DUPLICATE_RESOURCE.value(),
            INSUFFICIENT_STOCK.value(),
            COUPON_RESERVATION_CONFLICT.value(),
            COUPON_UNAVAILABLE.value(),
            INVALID_COUPON_REQUEST.value());

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ErrorResponse> handleBusiness(BusinessException ex, HttpServletRequest req) {
        String message = businessMessage(ex);
        return build(ex.getStatus(), ex.getCode(), message, req, null);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException ex, HttpServletRequest req) {
        Map<String, String> fieldErrors = new HashMap<>();
        ex.getBindingResult().getAllErrors().forEach(err -> {
            if (err instanceof FieldError fe) {
                fieldErrors.put(fe.getField(), Optional.ofNullable(fe.getDefaultMessage()).orElse("invalid"));
            }
        });
        return build(HttpStatus.BAD_REQUEST, VALIDATION_FAILED.value(), "Validation failed", req, fieldErrors);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ErrorResponse> handleConstraintViolation(ConstraintViolationException ex,
                                                                   HttpServletRequest req) {
        Map<String, String> fieldErrors = new HashMap<>();
        ex.getConstraintViolations().forEach(v ->
                fieldErrors.put(v.getPropertyPath().toString(), v.getMessage()));
        return build(HttpStatus.BAD_REQUEST, VALIDATION_FAILED.value(), "Validation failed", req, fieldErrors);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorResponse> handleTypeMismatch(MethodArgumentTypeMismatchException ex,
                                                            HttpServletRequest req) {
        return build(HttpStatus.BAD_REQUEST, TYPE_MISMATCH.value(),
                "Parameter '%s' has invalid value: %s".formatted(ex.getName(), ex.getValue()), req, null);
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ErrorResponse> handleMethodNotSupported(HttpRequestMethodNotSupportedException ex,
                                                                  HttpServletRequest req) {
        return build(HttpStatus.METHOD_NOT_ALLOWED, METHOD_NOT_ALLOWED.value(), ex.getMessage(), req, null);
    }

    /**
     * Spring 6 raises {@link NoResourceFoundException} (instead of falling through
     * to the default 404 handler) when no controller mapping matches and the path
     * also doesn't resolve to a static resource. Treat it as a clean 404.
     *
     * <p>Most common trigger in this codebase: hitting
     * {@code /oauth2/authorization/google} on a deployment where Google client
     * credentials aren't configured, so the OAuth2Login filter chain isn't
     * installed.
     */
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ErrorResponse> handleNoResource(NoResourceFoundException ex, HttpServletRequest req) {
        String path = req.getRequestURI();
        String code = NOT_FOUND.value();
        String message = "Endpoint not found: " + path;
        if (path != null && (path.startsWith("/oauth2/") || path.startsWith("/login/oauth2/"))) {
            code = OAUTH2_PROVIDER_NOT_CONFIGURED.value();
            message = "OAuth2 social login isn't configured on this server. " +
                    "Set GOOGLE_CLIENT_ID / GITHUB_CLIENT_ID env vars and restart auth-service to enable.";
        }
        return build(HttpStatus.NOT_FOUND, code, message, req, null);
    }

    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<ErrorResponse> handleBadCredentials(BadCredentialsException ex, HttpServletRequest req) {
        return build(HttpStatus.UNAUTHORIZED, BAD_CREDENTIALS.value(), "Invalid credentials", req, null);
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ErrorResponse> handleAuthentication(AuthenticationException ex, HttpServletRequest req) {
        return build(HttpStatus.UNAUTHORIZED, UNAUTHENTICATED.value(), "Authentication required", req, null);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorResponse> handleAccessDenied(AccessDeniedException ex, HttpServletRequest req) {
        return build(HttpStatus.FORBIDDEN, ACCESS_DENIED.value(), "Access denied", req, null);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleAny(Exception ex, HttpServletRequest req) {
        log.error("Unhandled exception at {} {}: {}", req.getMethod(), req.getRequestURI(), ex.getMessage(), ex);
        return build(HttpStatus.INTERNAL_SERVER_ERROR, INTERNAL_ERROR.value(),
                "An unexpected error occurred", req, null);
    }

    private ResponseEntity<ErrorResponse> build(HttpStatus status, String code, String message,
                                                HttpServletRequest req, Map<String, String> fieldErrors) {
        ErrorResponse body = ApiResponse.errorResponse(
                status.value(), code, message, req.getRequestURI(), fieldErrors, requestId(req));
        return ResponseEntity.status(status).body(body);
    }

    private boolean isRetryable(HttpStatus status) {
        return status == HttpStatus.BAD_GATEWAY
                || status == HttpStatus.SERVICE_UNAVAILABLE
                || status == HttpStatus.GATEWAY_TIMEOUT;
    }

    private String businessMessage(BusinessException ex) {
        if (isRetryable(ex.getStatus())) {
            return "Upstream service temporarily unavailable";
        }
        if (SAFE_PUBLIC_CODES.contains(ex.getCode())) {
            return ex.getMessage();
        }
        return "Request could not be processed";
    }

    private String requestId(HttpServletRequest req) {
        String requestId = req.getHeader("X-Request-Id");
        return requestId == null || requestId.isBlank() ? UUID.randomUUID().toString() : requestId;
    }
}
