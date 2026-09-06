package com.project.authservice.dto.response;

import com.project.authservice.dto.AddressDto;

import java.time.LocalDateTime;
import java.util.Set;
import java.util.UUID;

public record UserProfileDto(
        UUID id,
        String email,
        String displayName,
        String imageUrl,
        String phone,
        boolean active,
        LocalDateTime createdAt,
        Set<String> roles,
        Set<String> permissions,
        AddressDto shippingAddress,
        AddressDto billingAddress,
        boolean hasPassword
) {
    public UserProfileDto {
        roles = roles == null ? Set.of() : Set.copyOf(roles);
        permissions = permissions == null ? Set.of() : Set.copyOf(permissions);
    }
}
