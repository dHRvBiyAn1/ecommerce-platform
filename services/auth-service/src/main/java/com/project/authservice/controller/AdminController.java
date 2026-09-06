package com.project.authservice.controller;

import com.project.authservice.dto.response.UserProfileDto;
import com.project.authservice.dto.request.admin.AssignRolesRequest;
import com.project.authservice.dto.request.admin.CreateRoleRequest;
import com.project.authservice.dto.request.admin.UpdateRolePermissionsRequest;
import com.project.authservice.entity.Permission;
import com.project.authservice.entity.Role;
import com.project.authservice.entity.User;
import com.project.authservice.service.UserProfileService;
import com.project.authservice.repository.PermissionRepository;
import com.project.authservice.repository.RoleRepository;
import com.project.authservice.repository.UserRepository;
import com.project.common.exception.DuplicateResourceException;
import com.project.common.exception.ResourceNotFoundException;
import com.project.common.dto.ApiResponse;
import jakarta.transaction.Transactional;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminController {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PermissionRepository permissionRepository;
    private final UserProfileService userProfileService;

    @GetMapping("/users")
    @PreAuthorize("hasAuthority('admin:users:read')")
    public ResponseEntity<ApiResponse<Page<UserProfileDto>>> listUsers(Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.success(userRepository.findAll(pageable).map(userProfileService::toDto)));
    }

    @GetMapping("/users/{userId}")
    @PreAuthorize("hasAuthority('admin:users:read')")
    public ResponseEntity<ApiResponse<UserProfileDto>> getUser(@PathVariable UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", userId));
        return ResponseEntity.ok(ApiResponse.success(userProfileService.toDto(user)));
    }

    @GetMapping("/roles")
    @PreAuthorize("hasAuthority('admin:roles:read')")
    public ResponseEntity<ApiResponse<List<Role>>> listRoles() {
        return ResponseEntity.ok(ApiResponse.success(roleRepository.findAll()));
    }

    @PostMapping("/roles")
    @PreAuthorize("hasAuthority('admin:roles:write')")
    @Transactional
    public ResponseEntity<ApiResponse<Role>> createRole(@Valid @RequestBody CreateRoleRequest request) {
        if (roleRepository.findByName(request.name()).isPresent()) {
            throw new DuplicateResourceException("Role already exists: " + request.name());
        }
        Role role = new Role();
        role.setName(request.name());
        role = roleRepository.save(role);
        return new ResponseEntity<>(ApiResponse.created(role), HttpStatus.CREATED);
    }

    @PutMapping("/roles/{roleId}/permissions")
    @PreAuthorize("hasAuthority('admin:roles:write')")
    @Transactional
    public ResponseEntity<ApiResponse<Role>> updateRolePermissions(
            @PathVariable UUID roleId,
            @Valid @RequestBody UpdateRolePermissionsRequest request) {
        Role role = roleRepository.findById(roleId)
                .orElseThrow(() -> new ResourceNotFoundException("Role", roleId));
        Set<Permission> perms = request.permissions().stream()
                .map(name -> permissionRepository.findByName(name)
                        .orElseThrow(() -> new ResourceNotFoundException("Permission", name)))
                .collect(Collectors.toSet());
        role.setPermissions(perms);
        return ResponseEntity.ok(ApiResponse.success(roleRepository.save(role)));
    }

    @PutMapping("/users/{userId}/roles")
    @PreAuthorize("hasAuthority('admin:users:write')")
    @Transactional
    public ResponseEntity<ApiResponse<UserProfileDto>> setRoles(
            @PathVariable UUID userId,
            @Valid @RequestBody AssignRolesRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", userId));
        Set<Role> roles = request.roles().stream()
                .map(name -> roleRepository.findByName(name)
                        .orElseThrow(() -> new ResourceNotFoundException("Role", name)))
                .collect(Collectors.toSet());
        user.setRoles(roles);
        return ResponseEntity.ok(ApiResponse.success(userProfileService.toDto(userRepository.save(user))));
    }

    @PutMapping("/users/{userId}/active")
    @PreAuthorize("hasAuthority('admin:users:write')")
    @Transactional
    public ResponseEntity<ApiResponse<UserProfileDto>> setActive(
            @PathVariable UUID userId,
            @RequestParam boolean active) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", userId));
        user.setActive(active);
        return ResponseEntity.ok(ApiResponse.success(userProfileService.toDto(userRepository.save(user))));
    }

    @DeleteMapping("/roles/{roleId}")
    @PreAuthorize("hasAuthority('admin:roles:write')")
    @Transactional
    public ResponseEntity<ApiResponse<Void>> deleteRole(@PathVariable UUID roleId) {
        Role role = roleRepository.findById(roleId)
                .orElseThrow(() -> new ResourceNotFoundException("Role", roleId));
        roleRepository.delete(role);
        return ResponseEntity.noContent().build();
    }
}
