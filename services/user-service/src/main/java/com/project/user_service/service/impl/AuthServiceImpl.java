package com.project.user_service.service.impl;

import java.util.UUID;

import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import com.project.user_service.dtos.JwtResponse;
import com.project.user_service.dtos.LoginRequest;
import com.project.user_service.dtos.RegisterRequest;
import com.project.user_service.dtos.UserProfileDto;
import com.project.user_service.enums.Role;
import com.project.user_service.model.User;
import com.project.user_service.repository.UserRepository;
import com.project.user_service.security.JwtService;
import com.project.user_service.service.AuthService;
import com.project.user_service.service.CustomUserDetailsService;
import com.project.user_service.service.EmailService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private final CustomUserDetailsService userDetailsService; // we need this for refresh token validation
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;
    private final EmailService emailService; // to be implemented

    public UserProfileDto register(RegisterRequest request) {
        // Check if user already exists
        if (userRepository.findByEmail(request.getEmail()).isPresent()) {
            throw new RuntimeException("Email already registered");
        }

        // Determine role (default CUSTOMER)
        Role role;
        try {
            role = Role.valueOf(request.getRole() != null ? request.getRole().toUpperCase() : "CUSTOMER");
        } catch (IllegalArgumentException e) {
            role = Role.CUSTOMER;
        }

        // Build user
        User user = User.builder()
                .email(request.getEmail())
                .passwordHash(passwordEncoder.encode(request.getPassword()))
                .firstName(request.getFirstName())
                .lastName(request.getLastName())
                .role(role)
                .emailVerified(false)
                .emailVerificationToken(UUID.randomUUID().toString()) // simple token
                .build();

        user = userRepository.save(user);

        // Send verification email (mock for now)
        String verificationLink = "http://localhost:8081/api/v1/users/verify-email?token="
                + user.getEmailVerificationToken();
        emailService.sendVerificationEmail(user.getEmail(), verificationLink);
        log.info("Verification email sent to {}: {}", user.getEmail(), verificationLink);

        return mapToDto(user);
    }

    public JwtResponse login(LoginRequest request) {
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.getEmail(), request.getPassword()));
        SecurityContextHolder.getContext().setAuthentication(authentication);

        UserDetails userDetails = (UserDetails) authentication.getPrincipal();
        String accessToken = jwtService.generateAccessToken(userDetails);
        String refreshToken = jwtService.generateRefreshToken(userDetails);

        return new JwtResponse(accessToken, "Bearer", jwtService.getAccessTokenExpiration(), refreshToken);
    }

    public JwtResponse refreshAccessToken(String refreshToken) {
        // Validate refresh token
        String username = jwtService.extractUsername(refreshToken);
        UserDetails userDetails = userDetailsService.loadUserByUsername(username); // we need userDetailsService
        if (!jwtService.validateToken(refreshToken, userDetails)) {
            throw new RuntimeException("Invalid refresh token");
        }

        String newAccessToken = jwtService.generateAccessToken(userDetails);
        return new JwtResponse(newAccessToken, "Bearer", jwtService.getAccessTokenExpiration(), null);
    }

    public UserProfileDto getProfile(UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));
        return mapToDto(user);
    }

    public void verifyEmail(String token) {
        User user = userRepository.findByEmailVerificationToken(token)
                .orElseThrow(() -> new RuntimeException("Invalid verification token"));
        user.setEmailVerified(true);
        user.setEmailVerificationToken(null);
        userRepository.save(user);
    }

    private UserProfileDto mapToDto(User user) {
        UserProfileDto dto = new UserProfileDto();
        dto.setId(user.getId());
        dto.setEmail(user.getEmail());
        dto.setFirstName(user.getFirstName());
        dto.setLastName(user.getLastName());
        dto.setRole(user.getRole().name());
        dto.setEmailVerified(user.isEmailVerified());
        return dto;
    }
}
