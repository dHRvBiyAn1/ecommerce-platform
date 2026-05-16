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
    private boolean active;
    private LocalDateTime createdAt;
    private Set<String> roles;
    private Set<String> permissions;
}
