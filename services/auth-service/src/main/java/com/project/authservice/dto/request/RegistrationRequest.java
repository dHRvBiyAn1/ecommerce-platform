package com.project.authservice.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Public registration request. Always creates a customer; sellers must be promoted
 * via the admin API or the seller-onboarding flow.
 */
public record RegistrationRequest(
        @NotBlank(message = "Email is required")
        @Email(message = "Email must be valid")
        @Size(max = 254)
        String email,
        @NotBlank(message = "Password is required")
        @Size(min = 10, max = 128, message = "Password must be 10-128 characters")
        @Pattern(regexp = ".*[A-Z].*", message = "Password must contain an uppercase letter")
        @Pattern(regexp = ".*[a-z].*", message = "Password must contain a lowercase letter")
        @Pattern(regexp = ".*\\d.*", message = "Password must contain a digit")
        String password,
        @NotBlank(message = "Display name is required")
        @Size(min = 2, max = 80)
        String displayName
) {}
