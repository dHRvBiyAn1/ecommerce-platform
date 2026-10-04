package com.project.authservice.dto.request.admin;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

public record UpdateRolePermissionsRequest(
        @NotEmpty List<@NotBlank String> permissions
) {}
