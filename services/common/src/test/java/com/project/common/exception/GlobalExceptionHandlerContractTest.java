package com.project.common.exception;

import com.project.common.constant.ErrorCode;
import com.project.common.dto.ErrorResponse;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.time.Instant;
import java.util.Set;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class GlobalExceptionHandlerContractTest {

    private static final String REQUEST_ID = "req-contract-24";

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    private final MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new ValidationController())
            .setControllerAdvice(handler)
            .build();

    @Test
    void mapsEveryCommonFailureToTheStableErrorContract() {
        assertContract(handler.handleConstraintViolation(
                new ConstraintViolationException("validation details", Set.of()), request("/validation")),
                HttpStatus.BAD_REQUEST, ErrorCode.VALIDATION_FAILED.value(), "Validation failed");
        assertContract(handler.handleBusiness(new ResourceNotFoundException("Order", "secret-id"),
                        request("/orders/secret-id")),
                HttpStatus.NOT_FOUND, ErrorCode.RESOURCE_NOT_FOUND.value(), "Order not found: secret-id");
        assertContract(handler.handleAccessDenied(new AccessDeniedException("permission internals"),
                        request("/orders/1")),
                HttpStatus.FORBIDDEN, ErrorCode.ACCESS_DENIED.value(), "Access denied");
        assertContract(handler.handleBusiness(new DuplicateResourceException("already exists"),
                        request("/orders")),
                HttpStatus.CONFLICT, ErrorCode.DUPLICATE_RESOURCE.value(), "already exists");
        assertContract(handler.handleBadCredentials(new BadCredentialsException("password details"),
                        request("/login")),
                HttpStatus.UNAUTHORIZED, ErrorCode.BAD_CREDENTIALS.value(), "Invalid credentials");
        assertContract(handler.handleAuthentication(new AuthenticationException("token secret") { },
                        request("/session")),
                HttpStatus.UNAUTHORIZED, ErrorCode.UNAUTHENTICATED.value(), "Authentication required");
        assertContract(handler.handleBusiness(new BusinessException(HttpStatus.SERVICE_UNAVAILABLE,
                        "UPSTREAM_UNAVAILABLE", "downstream token=secret"), request("/checkout")),
                HttpStatus.SERVICE_UNAVAILABLE, "UPSTREAM_UNAVAILABLE", "Upstream service temporarily unavailable");
    }

    @Test
    void doesNotExposeDetailsFromAnUnclassifiedBusinessException() {
        ResponseEntity<ErrorResponse> response = handler.handleBusiness(
                new BusinessException(HttpStatus.BAD_REQUEST, "PROVIDER_ERROR",
                        "provider=stripe secret=sk_live_sensitive"), request("/payments"));

        assertThat(response.getBody().message()).isEqualTo("Request could not be processed");
    }

    @Test
    void preservesIntentionalMessagesFromTypedUserFacingExceptions() {
        assertThat(handler.handleBusiness(new ValidationException("Email is required"), request("/users"))
                .getBody().message()).isEqualTo("Email is required");
        assertThat(handler.handleBusiness(new ResourceNotFoundException("Order", "42"), request("/orders/42"))
                .getBody().message()).isEqualTo("Order not found: 42");
        assertThat(handler.handleBusiness(new DuplicateResourceException("Order already exists"), request("/orders"))
                .getBody().message()).isEqualTo("Order already exists");
    }

    @ParameterizedTest
    @MethodSource("businessMessageCases")
    void classifiesRawBusinessExceptionsByErrorCode(String code, String expectedMessage) {
        String detail = "sensitive detail for " + code;

        assertThat(handler.handleBusiness(
                new BusinessException(HttpStatus.BAD_REQUEST, code, detail), request("/orders"))
                .getBody().message()).isEqualTo(expectedMessage);
    }

    private static Stream<Arguments> businessMessageCases() {
        return Stream.of(
                Arguments.of("ORDER_VALIDATION", "sensitive detail for ORDER_VALIDATION"),
                Arguments.of(ErrorCode.INSUFFICIENT_STOCK.value(),
                        "sensitive detail for INSUFFICIENT_STOCK"),
                Arguments.of(ErrorCode.RESOURCE_NOT_FOUND.value(),
                        "sensitive detail for RESOURCE_NOT_FOUND"),
                Arguments.of(ErrorCode.DUPLICATE_RESOURCE.value(),
                        "sensitive detail for DUPLICATE_RESOURCE"),
                Arguments.of("PROVIDER_ERROR", "Request could not be processed"),
                Arguments.of(ErrorCode.PAYMENT_ERROR.value(), "Request could not be processed"),
                Arguments.of("INTERNAL_ERROR", "Request could not be processed")
        );
    }

    @Test
    void serializesValidationErrorsWithRequestIdAndFieldDetails() throws Exception {
        String body = mockMvc.perform(post("/validate")
                        .header("X-Request-Id", REQUEST_ID)
                        .contentType("application/json")
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andReturn().getResponse().getContentAsString();
        JsonNode json = new ObjectMapper().readTree(body);

        assertThat(json.get("code").asText()).isEqualTo(ErrorCode.VALIDATION_FAILED.value());
        assertThat(json.get("message").asText()).isEqualTo("Validation failed");
        assertThat(json.get("traceId").asText()).isEqualTo(REQUEST_ID);
        assertThat(json.get("fieldErrors").get("name").asText()).isEqualTo("must not be blank");
        assertThat(json.get("timestamp").asText()).isNotBlank();
    }

    @Test
    void serializesMissingResourceErrorsWithGeneratedRequestIdWhenHeaderIsAbsent() throws Exception {
        mockMvc.perform(get("/missing-resource"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(ErrorCode.NOT_FOUND.value()))
                .andExpect(jsonPath("$.traceId").isNotEmpty())
                .andExpect(jsonPath("$.message").value("Endpoint not found: /missing-resource"));
    }

    private void assertContract(ResponseEntity<ErrorResponse> response, HttpStatus status,
                                String code, String message) {
        ErrorResponse body = response.getBody();

        assertThat(response.getStatusCode()).isEqualTo(status);
        assertThat(body).isNotNull();
        assertThat(body.status()).isEqualTo(status.value());
        assertThat(body.error()).isEqualTo(status.getReasonPhrase());
        assertThat(body.code()).isEqualTo(code);
        assertThat(body.message()).isEqualTo(message);
        assertThat(body.traceId()).isEqualTo(REQUEST_ID);
        assertThat(body.timestamp()).isBetween(Instant.now().minusSeconds(5), Instant.now());
    }

    private MockHttpServletRequest request(String path) {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", path);
        request.addHeader("X-Request-Id", REQUEST_ID);
        return request;
    }

    @RestController
    private static class ValidationController {
        @PostMapping("/validate")
        void validate(@Valid @RequestBody ValidationRequest request) {
        }

        @org.springframework.web.bind.annotation.GetMapping("/missing-resource")
        void missingResource() throws NoResourceFoundException {
            throw new NoResourceFoundException(HttpMethod.GET, "/missing-resource");
        }
    }

    private record ValidationRequest(@NotBlank String name) {
    }

}
