package com.project.authservice.security;

import com.project.authservice.entity.AuthProvider;
import com.project.authservice.entity.User;
import com.project.authservice.service.AuthService;
import com.project.authservice.util.CookieUtils;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
public class CustomOAuth2SuccessHandler implements AuthenticationSuccessHandler {

    private final AuthService authService;
    
    @Value("${jwt.refresh-token-expiration}")
    private long refreshTokenDurationMs;

    public CustomOAuth2SuccessHandler(AuthService authService) {
        this.authService = authService;
    }

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response, Authentication authentication) throws IOException, ServletException {
        OAuth2User oAuth2User = (OAuth2User) authentication.getPrincipal();
        
        String email = oAuth2User.getAttribute("email");
        String name = oAuth2User.getAttribute("name");
        String providerId = oAuth2User.getAttribute("sub"); // Google specific

        User user = authService.processOAuth2User(email, name, providerId, AuthProvider.GOOGLE);
        
        AuthService.TokenResponseWithRefresh tokenResponse = authService.generateTokenPairForOAuth2(user);

        CookieUtils.addCookie(response, CookieUtils.REFRESH_TOKEN_COOKIE_NAME, tokenResponse.getRefreshToken(), (int) (refreshTokenDurationMs / 1000));

        // Redirect to frontend with token in URL fragment (not query param) to avoid
        // exposure in server logs, browser history, and Referer headers.
        String frontendUrl = "http://localhost:4200/oauth2/redirect#token=" + tokenResponse.getAccessToken();
        response.sendRedirect(frontendUrl);
    }
}
