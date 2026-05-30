package com.project.authservice.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class UserUpdateRequest {
    @Size(min = 2, max = 80)
    private String displayName;

    private String imageUrl;

    /** Loose phone validation — international format with optional + prefix. */
    @Pattern(regexp = "^\\+?[0-9 ()-]{6,20}$", message = "Invalid phone number")
    private String phone;

    @Valid
    private AddressDto shippingAddress;

    @Valid
    private AddressDto billingAddress;
}
