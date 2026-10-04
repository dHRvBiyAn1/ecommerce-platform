package com.project.authservice.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
public record AddressDto(
        @NotBlank(message = "Full name is required")
        @Size(max = 120, message = "Full name must not exceed 120 characters") String fullName,
        @NotBlank(message = "Phone number is required")
        @Pattern(regexp = "^\\+?[0-9 ()-]{6,20}$", message = "Invalid phone number") String phone,
        @NotBlank(message = "Street address is required")
        @Size(max = 200, message = "Street address must not exceed 200 characters") String street,
        @NotBlank(message = "City is required")
        @Size(max = 80, message = "City must not exceed 80 characters") String city,
        @NotBlank(message = "State/Province is required")
        @Size(max = 80, message = "State must not exceed 80 characters") String state,
        @NotBlank(message = "Zip/Postal code is required")
        @Size(max = 20, message = "Zip code must not exceed 20 characters") String zipCode,
        @NotBlank(message = "Country is required")
        @Size(max = 80, message = "Country must not exceed 80 characters") String country
) {}
