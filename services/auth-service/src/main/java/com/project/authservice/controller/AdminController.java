package com.project.authservice.controller;

import com.project.authservice.dto.ApiResponse;
import com.project.authservice.dto.UserProfileDto;
import com.project.authservice.entity.Role;
import com.project.authservice.entity.User;
import com.project.authservice.mapper.UserMapper;
import com.project.authservice.repository.RoleRepository;
import com.project.authservice.repository.UserRepository;
import jakarta.transaction.Transactional;
import org.springframework.http.ResponseEntity;
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
    private final UserMapper userMapper;

    public AdminController(UserRepository userRepository, RoleRepository roleRepository, UserMapper userMapper) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.userMapper = userMapper;
    }

    @PutMapping("/users/{userId}/roles")
    @Transactional
    public ResponseEntity<ApiResponse<UserProfileDto>> updateUserRoles(@PathVariable UUID userId, @RequestBody List<String> roleNames) {
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
}
