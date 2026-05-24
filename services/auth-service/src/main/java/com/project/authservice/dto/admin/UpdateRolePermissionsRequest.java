package com.project.authservice.dto.admin;

import jakarta.validation.constraints.NotEmpty;

import java.util.List;

public record UpdateRolePermissionsRequest(
        @NotEmpty List<String> permissions
) {}
