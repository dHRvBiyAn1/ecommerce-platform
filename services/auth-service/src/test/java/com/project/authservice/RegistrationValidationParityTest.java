package com.project.authservice;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

class RegistrationValidationParityTest {

    @Test
    void generatedRegistrationConstraintsKeepLegacyViolationMessages() {
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            Validator validator = factory.getValidator();

            assertParity(validator, "   ", "ValidPass123");
            assertParity(validator, "not-an-email", "ValidPass123");
            assertParity(validator, "customer@example.com", "");
            assertParity(validator, "customer@example.com", "PASSWORD123");
            assertParity(validator, "customer@example.com", "PasswordABC");

            assertThat(messages(validator, generated("not-an-email", "ValidPass123")))
                    .contains("email:Email must be valid");
            assertThat(messages(validator, generated("customer@example.com", "PASSWORD123")))
                    .contains("password:Password must contain a lowercase letter");
            assertThat(messages(validator, generated("customer@example.com", "PasswordABC")))
                    .contains("password:Password must contain a digit");
        }
    }

    private static void assertParity(Validator validator, String email, String password) {
        var legacy = new com.project.authservice.dto.request.RegistrationRequest(
                email, password, "Customer");
        assertThat(messages(validator, generated(email, password)))
                .containsExactlyInAnyOrderElementsOf(messages(validator, legacy));
    }

    private static com.project.authservice.generated.model.RegistrationRequest generated(
            String email, String password) {
        return new com.project.authservice.generated.model.RegistrationRequest()
                .email(email)
                .password(password)
                .displayName("Customer");
    }

    private static Set<String> messages(Validator validator, Object value) {
        return validator.validate(value).stream()
                .map(violation -> violation.getPropertyPath() + ":" + violation.getMessage())
                .collect(Collectors.toSet());
    }
}
