package com.project.authservice.dto;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.Set;
import java.util.UUID;

@Data
public class UserProfileDto {
    private UUID id;
    private String email;
    private String displayName;
    private String imageUrl;
    private String phone;
    private boolean active;
    private LocalDateTime createdAt;
    private Set<String> roles;
    private Set<String> permissions;
    private AddressDto shippingAddress;
    private AddressDto billingAddress;
    /**
     * True when the user has a LOCAL credential — i.e. they registered with
     * email + password (or set one later). Used by the frontend to hide the
     * "change password" UI for users who only signed in via Google / GitHub
     * and therefore have no password to change.
     */
    private boolean hasPassword;
}
