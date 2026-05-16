package com.project.authservice.controller;

import com.project.authservice.dto.ApiResponse;
import com.project.authservice.dto.UserProfileDto;
import com.project.authservice.dto.UserUpdateRequest;
import com.project.authservice.mapper.UserMapper;
import com.project.authservice.repository.UserRepository;
import com.project.authservice.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserControllerTest {

    @Mock private UserRepository userRepository;
    @Mock private UserMapper userMapper;
    @Mock private Authentication authentication;

    private UserController userController;

    @BeforeEach
    void setUp() {
        userController = new UserController(userRepository, userMapper);
    }

    @Test
    void getProfile_ReturnsOk() {
        UUID userId = UUID.randomUUID();
        User user = new User();
        user.setId(userId);
        user.setEmail("test@example.com");

        when(authentication.getName()).thenReturn(userId.toString());
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));

        UserProfileDto dto = new UserProfileDto();
        dto.setId(userId);
        dto.setEmail("test@example.com");
        when(userMapper.toDto(user)).thenReturn(dto);

        ResponseEntity<ApiResponse<UserProfileDto>> response = userController.getProfile(authentication);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().getData().getEmail()).isEqualTo("test@example.com");
    }

    @Test
    void updateProfile_ReturnsOk() {
        UUID userId = UUID.randomUUID();
        User user = new User();
        user.setId(userId);
        user.setDisplayName("Old Name");

        UserUpdateRequest request = new UserUpdateRequest();
        request.setDisplayName("New Name");

        when(authentication.getName()).thenReturn(userId.toString());
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(userRepository.save(any(User.class))).thenAnswer(i -> i.getArgument(0));

        UserProfileDto dto = new UserProfileDto();
        dto.setDisplayName("New Name");
        when(userMapper.toDto(any(User.class))).thenReturn(dto);

        ResponseEntity<ApiResponse<UserProfileDto>> response = userController.updateProfile(request, authentication);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().getData().getDisplayName()).isEqualTo("New Name");
    }
}
