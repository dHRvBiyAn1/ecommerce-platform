package com.project.order.dto;

import jakarta.validation.constraints.NotBlank;

public record BillingAddressRequest(
    @NotBlank(message = "Full name is required")
    String fullName,

    @NotBlank(message = "Phone is required")
    String phone,

    @NotBlank(message = "Street is required")
    String street,

    @NotBlank(message = "City is required")
    String city,

    String state,

    @NotBlank(message = "Zip code is required")
    String zipCode,

    @NotBlank(message = "Country is required")
    String country
) {}
