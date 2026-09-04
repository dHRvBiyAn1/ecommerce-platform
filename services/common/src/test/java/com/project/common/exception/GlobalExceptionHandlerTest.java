package com.project.common.exception;

import com.project.common.constant.ErrorCode;
import com.project.common.dto.ErrorResponse;
import jakarta.validation.ConstraintViolationException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.assertj.core.api.Assertions.assertThat;

class GlobalExceptionHandlerTest {

    @Test
    void returnsStableValidationCodeWithoutLeakingUnexpectedDetails() {
        GlobalExceptionHandler handler = new GlobalExceptionHandler();
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/orders");

        ResponseEntity<ErrorResponse> response = handler.handleConstraintViolation(
                new ConstraintViolationException("invalid order", java.util.Set.of()), request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody())
                .extracting(ErrorResponse::code, ErrorResponse::message, ErrorResponse::path)
                .containsExactly(ErrorCode.VALIDATION_FAILED.value(), "Validation failed", "/api/v1/orders");
    }
}
