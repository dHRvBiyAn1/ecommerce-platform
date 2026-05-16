package com.project.authservice.controller;

import com.project.authservice.dto.ApiResponse;
import com.project.authservice.dto.UserProfileDto;
import com.project.authservice.entity.Permission;
import com.project.authservice.entity.Role;
import com.project.authservice.entity.User;
import com.project.authservice.mapper.UserMapper;
import com.project.authservice.repository.PermissionRepository;
import com.project.authservice.repository.RoleRepository;
import com.project.authservice.repository.UserRepository;
import jakarta.transaction.Transactional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/admin")
public class AdminController {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PermissionRepository permissionRepository;
    private final UserMapper userMapper;

    public AdminController(UserRepository userRepository, RoleRepository roleRepository,
                           PermissionRepository permissionRepository, UserMapper userMapper) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.permissionRepository = permissionRepository;
        this.userMapper = userMapper;
    }

    @GetMapping("/users")
    @PreAuthorize("hasAuthority('admin:users:read')")
    public ResponseEntity<ApiResponse<Page<UserProfileDto>>> listUsers(Pageable pageable) {
        Page<User> users = userRepository.findAll(pageable);
        Page<UserProfileDto> dtos = users.map(userMapper::toDto);
        return ResponseEntity.ok(ApiResponse.success(dtos));
    }

    @GetMapping("/users/{userId}")
    @PreAuthorize("hasAuthority('admin:users:read')")
    public ResponseEntity<ApiResponse<UserProfileDto>> getUser(@PathVariable UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));
        return ResponseEntity.ok(ApiResponse.success(userMapper.toDto(user)));
    }

    @GetMapping("/roles")
    @PreAuthorize("hasAuthority('admin:roles:read')")
    public ResponseEntity<ApiResponse<List<Role>>> listRoles() {
        List<Role> roles = roleRepository.findAll();
        return ResponseEntity.ok(ApiResponse.success(roles));
    }

    @PostMapping("/roles")
    @PreAuthorize("hasAuthority('admin:roles:write')")
    @Transactional
    public ResponseEntity<ApiResponse<Role>> createRole(@RequestBody String roleName) {
        if (roleRepository.findByName(roleName).isPresent()) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.<Role>success(null));
        }
        Role role = new Role();
        role.setName(roleName);
        role = roleRepository.save(role);
        return new ResponseEntity<>(ApiResponse.success(role), HttpStatus.CREATED);
    }

    @PutMapping("/permissions/{roleId}")
    @PreAuthorize("hasAuthority('admin:roles:write')")
    @Transactional
    public ResponseEntity<ApiResponse<Role>> updateRolePermissions(
            @PathVariable UUID roleId,
            @RequestBody List<String> permissionNames) {
        Role role = roleRepository.findById(roleId)
                .orElseThrow(() -> new RuntimeException("Role not found"));

        Set<Permission> permissions = permissionNames.stream()
                .map(name -> permissionRepository.findByName(name)
                        .orElseThrow(() -> new RuntimeException("Permission not found: " + name)))
                .collect(Collectors.toSet());

        role.setPermissions(permissions);
        role = roleRepository.save(role);
        return ResponseEntity.ok(ApiResponse.success(role));
    }

    @PutMapping("/users/{userId}/roles")
    @PreAuthorize("hasAuthority('admin:users:write')")
    @Transactional
    public ResponseEntity<ApiResponse<UserProfileDto>> updateUserRoles(
            @PathVariable UUID userId,
            @RequestBody List<String> roleNames) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        Set<Role> roles = roleNames.stream()
                .map(name -> roleRepository.findByName(name)
                        .orElseThrow(() -> new RuntimeException("Role not found: " + name)))
                .collect(Collectors.toSet());

        user.setRoles(roles);
        user = userRepository.save(user);

        return ResponseEntity.ok(ApiResponse.success(userMapper.toDto(user)));
    }

    @PutMapping("/users/{userId}/activate")
    @PreAuthorize("hasAuthority('admin:users:write')")
    @Transactional
    public ResponseEntity<ApiResponse<UserProfileDto>> toggleUserActive(
            @PathVariable UUID userId,
            @RequestParam boolean active) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));
        user.setActive(active);
        user = userRepository.save(user);
        return ResponseEntity.ok(ApiResponse.success(userMapper.toDto(user)));
    }

    @PutMapping("/users/{userId}/assign-role/{roleName}")
    @PreAuthorize("hasAuthority('admin:users:write')")
    @Transactional
    public ResponseEntity<ApiResponse<UserProfileDto>> assignRole(
            @PathVariable UUID userId,
            @PathVariable String roleName) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));
        Role role = roleRepository.findByName(roleName)
                .orElseThrow(() -> new RuntimeException("Role not found: " + roleName));
        user.getRoles().add(role);
        user = userRepository.save(user);
        return ResponseEntity.ok(ApiResponse.success(userMapper.toDto(user)));
    }
}
