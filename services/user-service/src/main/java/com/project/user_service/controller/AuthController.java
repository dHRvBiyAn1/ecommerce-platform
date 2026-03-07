package com.project.user_service.controller;

import lombok.RequiredArgsConstructor;

import java.util.UUID;

import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import com.project.user_service.dtos.JwtResponse;
import com.project.user_service.dtos.LoginRequest;
import com.project.user_service.dtos.RegisterRequest;
import com.project.user_service.dtos.UserProfileDto;
import com.project.user_service.service.AuthService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/register")
    public ResponseEntity<?> register(@Valid @RequestBody RegisterRequest request) {
        UserProfileDto user = authService.register(request);
        return ResponseEntity.ok(user);
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@Valid @RequestBody LoginRequest request) {
        // Returns access token in body and sets refresh token as HTTP-only cookie
        JwtResponse response = authService.login(request);
        ResponseCookie refreshTokenCookie = ResponseCookie.from("refreshToken", response.getRefreshToken())
                .httpOnly(true)
                .secure(true) // set to true in production with HTTPS
                .path("/api/v1/users/refresh")
                .maxAge(7 * 24 * 60 * 60) // 7 days
                .build();
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, refreshTokenCookie.toString())
                .body(new JwtResponse(response.getAccessToken(), response.getTokenType(), response.getExpiresIn(), null));
    }

    @PostMapping("/refresh")
    public ResponseEntity<?> refresh(@CookieValue("refreshToken") String refreshToken) {
        // Validate refresh token and issue new access token
        JwtResponse response = authService.refreshAccessToken(refreshToken);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/logout")
    public ResponseEntity<?> logout() {
        // Clear the refresh token cookie
        ResponseCookie clearCookie = ResponseCookie.from("refreshToken", "")
                .httpOnly(true)
                .secure(true)
                .path("/api/v1/users/refresh")
                .maxAge(0)
                .build();
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, clearCookie.toString())
                .body("Logged out successfully");
    }

    @GetMapping("/profile")
    public ResponseEntity<UserProfileDto> getProfile(@RequestAttribute("userId") UUID userId) {
        // userId would be set by API Gateway after JWT validation
        UserProfileDto profile = authService.getProfile(userId);
        return ResponseEntity.ok(profile);
    }

    @GetMapping("/verify-email")
    public ResponseEntity<?> verifyEmail(@RequestParam("token") String token) {
        authService.verifyEmail(token);
        return ResponseEntity.ok("Email verified successfully");
    }
}
