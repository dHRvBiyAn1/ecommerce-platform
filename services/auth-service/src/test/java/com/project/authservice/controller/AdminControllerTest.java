package com.project.authservice.controller;

import com.project.authservice.dto.ApiResponse;
import com.project.authservice.dto.UserProfileDto;
import com.project.authservice.entity.Role;
import com.project.authservice.entity.Permission;
import com.project.authservice.entity.User;
import com.project.authservice.mapper.UserMapper;
import com.project.authservice.repository.PermissionRepository;
import com.project.authservice.repository.RoleRepository;
import com.project.authservice.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminControllerTest {

    @Mock private UserRepository userRepository;
    @Mock private RoleRepository roleRepository;
    @Mock private PermissionRepository permissionRepository;
    @Mock private UserMapper userMapper;

    private AdminController adminController;

    @BeforeEach
    void setUp() {
        adminController = new AdminController(userRepository, roleRepository, permissionRepository, userMapper);
    }

    @Test
    void listUsers_ReturnsOk() {
        when(userRepository.findAll(any(Pageable.class))).thenReturn(Page.empty());

        ResponseEntity<ApiResponse<Page<UserProfileDto>>> response = adminController.listUsers(Pageable.unpaged());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void getUser_WhenFound_ReturnsOk() {
        UUID userId = UUID.randomUUID();
        User user = new User();
        user.setId(userId);
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(userMapper.toDto(user)).thenReturn(new UserProfileDto());

        ResponseEntity<ApiResponse<UserProfileDto>> response = adminController.getUser(userId);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void listRoles_ReturnsOk() {
        when(roleRepository.findAll()).thenReturn(List.of());

        ResponseEntity<ApiResponse<List<Role>>> response = adminController.listRoles();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void toggleUserActive_ReturnsOk() {
        UUID userId = UUID.randomUUID();
        User user = new User();
        user.setId(userId);
        user.setActive(true);

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(userRepository.save(any(User.class))).thenReturn(user);
        when(userMapper.toDto(any(User.class))).thenReturn(new UserProfileDto());

        ResponseEntity<ApiResponse<UserProfileDto>> response = adminController.toggleUserActive(userId, false);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(user.isActive()).isFalse();
    }

    @Test
    void assignRole_ReturnsOk() {
        UUID userId = UUID.randomUUID();
        User user = new User();
        user.setId(userId);
        user.setRoles(new java.util.HashSet<>());

        Role role = new Role();
        role.setName("ROLE_SELLER");

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(roleRepository.findByName("ROLE_SELLER")).thenReturn(Optional.of(role));
        when(userRepository.save(any(User.class))).thenReturn(user);
        when(userMapper.toDto(any(User.class))).thenReturn(new UserProfileDto());

        ResponseEntity<ApiResponse<UserProfileDto>> response = adminController.assignRole(userId, "ROLE_SELLER");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(user.getRoles()).contains(role);
    }
}
