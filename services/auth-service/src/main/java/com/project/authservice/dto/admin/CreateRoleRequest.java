package com.project.authservice.dto.admin;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record CreateRoleRequest(
        @NotBlank
        @Pattern(regexp = "ROLE_[A-Z_]+", message = "Role name must match ROLE_[A-Z_]+")
        String name
) {}
