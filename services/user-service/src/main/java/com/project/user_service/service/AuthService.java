package com.project.user_service.service;

import java.util.UUID;

import com.project.user_service.dtos.JwtResponse;
import com.project.user_service.dtos.LoginRequest;
import com.project.user_service.dtos.RegisterRequest;
import com.project.user_service.dtos.UserProfileDto;

public interface AuthService {

    UserProfileDto register(RegisterRequest request);

    JwtResponse login(LoginRequest request);

    JwtResponse refreshAccessToken(String refreshToken);

    UserProfileDto getProfile(UUID userId);

    void verifyEmail(String token);
    
}
