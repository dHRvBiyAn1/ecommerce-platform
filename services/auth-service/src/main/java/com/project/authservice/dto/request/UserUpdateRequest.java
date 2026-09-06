package com.project.authservice.dto.request;

import com.project.authservice.dto.AddressDto;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record UserUpdateRequest(
        @Size(min = 2, max = 80) String displayName,
        String imageUrl,
        @Pattern(regexp = "^\\+?[0-9 ()-]{6,20}$", message = "Invalid phone number") String phone,
        @Valid AddressDto shippingAddress,
        @Valid AddressDto billingAddress
) {}
