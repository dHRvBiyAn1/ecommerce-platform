package com.project.authservice;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

class RegistrationValidationParityTest {
    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void generatedRegistrationConstraintsPreserveValidationMessages() {
        assertThat(messages(generated("   ", "ValidPass123"))).contains("email:Email is required");
        assertThat(messages(generated("not-an-email", "ValidPass123"))).contains("email:Email must be valid");
        assertThat(messages(generated("customer@example.com", ""))).contains("password:Password is required");
        assertThat(messages(generated("customer@example.com", "PASSWORD123")))
                .contains("password:Password must contain a lowercase letter");
        assertThat(messages(generated("customer@example.com", "PasswordABC")))
                .contains("password:Password must contain a digit");
    }

    private static com.project.authservice.generated.model.RegistrationRequest generated(String email, String password) {
        return new com.project.authservice.generated.model.RegistrationRequest()
                .email(email).password(password).displayName("Customer");
    }

    private Set<String> messages(Object value) {
        return validator.validate(value).stream()
                .map(violation -> violation.getPropertyPath() + ":" + violation.getMessage())
                .collect(Collectors.toSet());
    }
}
