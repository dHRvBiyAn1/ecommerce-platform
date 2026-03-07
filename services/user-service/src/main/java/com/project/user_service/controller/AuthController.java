package com.project.user_service.controller;

import lombok.RequiredArgsConstructor;

import java.util.UUID;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
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
    public ResponseEntity<UserProfileDto> register(@Valid @RequestBody RegisterRequest request) {
        UserProfileDto user = authService.register(request);
        return new ResponseEntity<>(user, HttpStatus.CREATED);
    }

    @PostMapping("/login")
    public ResponseEntity<JwtResponse> login(@Valid @RequestBody LoginRequest request) {
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
    public ResponseEntity<JwtResponse> refresh(@CookieValue("refreshToken") String refreshToken) {
        return new ResponseEntity<>(authService.refreshAccessToken(refreshToken), HttpStatus.OK);
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
        return new ResponseEntity<>(authService.getProfile(userId), HttpStatus.OK);
    }

    @GetMapping("/verify-email")
    public ResponseEntity<String> verifyEmail(@RequestParam("token") String token) {
        authService.verifyEmail(token);
        return new ResponseEntity<>("Email verified successfully", HttpStatus.OK);
    }
}
