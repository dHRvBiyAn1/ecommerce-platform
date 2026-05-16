package com.project.authservice.controller;

import com.project.authservice.dto.ApiResponse;
import com.project.authservice.entity.Permission;
import com.project.authservice.entity.Role;
import com.project.authservice.repository.PermissionRepository;
import com.project.authservice.repository.RoleRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api")
public class RoleController {

    private final RoleRepository roleRepository;
    private final PermissionRepository permissionRepository;

    public RoleController(RoleRepository roleRepository, PermissionRepository permissionRepository) {
        this.roleRepository = roleRepository;
        this.permissionRepository = permissionRepository;
    }

    @GetMapping("/roles")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<List<Role>>> listRoles() {
        List<Role> roles = roleRepository.findAll();
        return ResponseEntity.ok(ApiResponse.success(roles));
    }

    @GetMapping("/permissions")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<List<Permission>>> listPermissions() {
        List<Permission> permissions = permissionRepository.findAll();
        return ResponseEntity.ok(ApiResponse.success(permissions));
    }
}
