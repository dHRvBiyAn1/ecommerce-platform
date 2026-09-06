package com.project.authservice.controller;

import com.project.authservice.dto.request.ChangePasswordRequest;
import com.project.authservice.dto.request.RegistrationRequest;
import com.project.authservice.dto.TokenResponse;
import com.project.authservice.dto.UserProfileDto;
import com.project.authservice.dto.request.ClientCredentialsRequest;
import com.project.authservice.dto.response.ServiceTokenResponse;
import com.project.authservice.exception.InvalidScopeException;
import com.project.authservice.exception.AuthException;
import com.project.common.dto.ApiResponse;
import com.project.authservice.service.AuthService;
import com.project.authservice.service.ClientCredentialsService;
import com.project.authservice.util.CookieUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final ClientCredentialsService clientCredentialsService;

    @Value("${jwt.refresh-token-expiration:2592000000}")
    private long refreshTokenDurationMs;

    @Value("${security.cookies.secure:true}")
    private boolean secureCookie;

    @PostMapping("/register")
    public ResponseEntity<ApiResponse<UserProfileDto>> register(
            @Valid @RequestBody RegistrationRequest request) {
        return new ResponseEntity<>(ApiResponse.created(authService.register(request)), HttpStatus.CREATED);
    }

    /**
     * OAuth2-style token endpoint supporting {@code password}, {@code refresh_token}, and
     * {@code client_credentials}
     * grants. The refresh token is delivered as an HttpOnly Secure SameSite=Strict cookie;
     * only the access token is returned in the body.
     */
    @PostMapping("/token")
    @Operation(summary = "Issue an access token", description = "Supports password, refresh_token, and client_credentials grants")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Token issued"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Invalid credentials or requested scope")
    })
    public ResponseEntity<?> token(
            @RequestParam(value = "grant_type", required = false) String grantType,
            @RequestParam(value = "email", required = false) String email,
            @RequestParam(value = "password", required = false) String password,
            @Parameter(name = "client_id")
            @RequestParam(value = "client_id", required = false) String clientId,
            @Parameter(name = "client_secret")
            @RequestParam(value = "client_secret", required = false) String clientSecret,
            @RequestParam(value = "scope", required = false) String scope,
            HttpServletRequest request,
            HttpServletResponse response) {

        if (grantType == null || grantType.isBlank()) {
            return oauthError(HttpStatus.BAD_REQUEST, "invalid_request", "Missing grant_type");
        }

        if ("client_credentials".equals(grantType)) {
            try {
                ServiceTokenResponse serviceToken = clientCredentialsService.issue(
                        new ClientCredentialsRequest(clientId, clientSecret, scope));
                return ResponseEntity.ok(serviceToken);
            } catch (InvalidScopeException ex) {
                return oauthError(HttpStatus.BAD_REQUEST, "invalid_scope", ex.getMessage());
            } catch (AuthException ex) {
                return oauthError(HttpStatus.UNAUTHORIZED, "invalid_client", "Invalid client credentials");
            }
        }

        if (!"password".equals(grantType) && !"refresh_token".equals(grantType)) {
            return oauthError(HttpStatus.BAD_REQUEST, "unsupported_grant_type", "Unsupported grant_type");
        }

        String existingRefresh = CookieUtils.getCookieValue(request, CookieUtils.REFRESH_TOKEN_COOKIE_NAME);
        String userAgent = request.getHeader(HttpHeaders.USER_AGENT);
        String ipAddress = clientIp(request);

        AuthService.TokenResponseWithRefresh result =
                authService.authenticate(grantType, email, password, existingRefresh, userAgent, ipAddress);

        CookieUtils.addCookie(response, CookieUtils.REFRESH_TOKEN_COOKIE_NAME,
                result.getRefreshToken(), (int) (refreshTokenDurationMs / 1000), secureCookie);

        return ResponseEntity.ok(ApiResponse.success(
                new TokenResponse(result.getAccessToken())));
    }

    @PostMapping("/logout")
    public ResponseEntity<ApiResponse<Void>> logout(HttpServletRequest request, HttpServletResponse response) {
        String authHeader = request.getHeader(HttpHeaders.AUTHORIZATION);
        String accessToken = (authHeader != null && authHeader.startsWith("Bearer ")) ? authHeader.substring(7) : null;
        String refreshTokenValue = CookieUtils.getCookieValue(request, CookieUtils.REFRESH_TOKEN_COOKIE_NAME);
        authService.logout(accessToken, refreshTokenValue);
        CookieUtils.deleteCookie(request, response, CookieUtils.REFRESH_TOKEN_COOKIE_NAME);
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    /**
     * Authenticated user changes their own password. Replaces previous {@code permitAll}
     * version that NPE'd on {@code authentication.getName()}.
     */
    @PostMapping("/change-password")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<Void>> changePassword(
            @Valid @RequestBody ChangePasswordRequest request,
            Authentication authentication) {
        // Principal is the user id (UUID string) from the JWT subject claim. We use the
        // Authentication.getName() to load the email indirectly via the service; in the
        // common-resource-server flow we'd read 'email' claim instead.
        // For the auth-service self-call we keep the legacy form: authentication.getName()
        // here will be the user id; the service signature accepts email lookup directly so
        // we surface it explicitly via the Authentication.principal token.
        String userId = authentication.getName();
        // We need the email for matching. AuthService.changePassword expects email — but the
        // caller is the authenticated user, so resolve email by loading the user once.
        // (Done inside AuthService.changePassword via UserRepository.)
        // We pass userId-as-email for compatibility with downstream lookup-by-email which
        // we replace here with a lookup-by-id helper.
        authService.changePasswordByUserId(java.util.UUID.fromString(userId), request.oldPassword(),
                request.newPassword());
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    private static String clientIp(HttpServletRequest req) {
        String xff = req.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) return xff.split(",")[0].trim();
        return req.getRemoteAddr();
    }

    private static ResponseEntity<Map<String, String>> oauthError(HttpStatus status, String error, String description) {
        return ResponseEntity.status(status).body(Map.of(
                "error", error,
                "error_description", description));
    }
}
