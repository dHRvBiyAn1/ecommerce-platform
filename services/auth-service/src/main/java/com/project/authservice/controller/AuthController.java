package com.project.authservice.controller;

import com.project.authservice.dto.ApiResponse;
import com.project.authservice.dto.LoginRequest;
import com.project.authservice.dto.RegistrationRequest;
import com.project.authservice.dto.TokenResponse;
import com.project.authservice.dto.UserProfileDto;
import com.project.authservice.service.AuthService;
import com.project.authservice.util.CookieUtils;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    @Value("${jwt.refresh-token-expiration}")
    private int refreshTokenDurationMs;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/register")
    public ResponseEntity<ApiResponse<UserProfileDto>> register(@Valid @RequestBody RegistrationRequest request) {
        UserProfileDto userProfile = authService.register(request);
        return new ResponseEntity<>(ApiResponse.created(userProfile), HttpStatus.CREATED);
    }

    @PostMapping("/token")
    public ResponseEntity<ApiResponse<TokenResponse>> token(
            @RequestParam("grant_type") String grantType,
            @RequestParam(value = "email", required = false) String email,
            @RequestParam(value = "password", required = false) String password,
            HttpServletRequest request,
            HttpServletResponse response) {

        String refreshTokenCookie = CookieUtils.getCookieValue(request, CookieUtils.REFRESH_TOKEN_COOKIE_NAME);
        
        TokenResponse tokenResponse = authService.authenticate(grantType, email, password, refreshTokenCookie);

        if (tokenResponse instanceof AuthService.TokenResponseWithRefresh trwr) {
            CookieUtils.addCookie(response, CookieUtils.REFRESH_TOKEN_COOKIE_NAME, trwr.getRefreshToken(), refreshTokenDurationMs / 1000);
            return ResponseEntity.ok(ApiResponse.success(new TokenResponse(tokenResponse.getAccessToken())));
        }

        return ResponseEntity.ok(ApiResponse.success(tokenResponse));
    }

    @PostMapping("/logout")
    public ResponseEntity<ApiResponse<Void>> logout(HttpServletRequest request, HttpServletResponse response) {
        String refreshTokenValue = CookieUtils.getCookieValue(request, CookieUtils.REFRESH_TOKEN_COOKIE_NAME);
        authService.logout(refreshTokenValue);
        CookieUtils.deleteCookie(request, response, CookieUtils.REFRESH_TOKEN_COOKIE_NAME);
        return ResponseEntity.ok(ApiResponse.success(null));
    }
}
