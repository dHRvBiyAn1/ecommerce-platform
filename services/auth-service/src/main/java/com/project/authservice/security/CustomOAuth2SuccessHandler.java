package com.project.authservice.security;

import com.project.authservice.entity.AuthProvider;
import com.project.authservice.entity.User;
import com.project.authservice.service.AuthService;
import com.project.authservice.util.CookieUtils;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.IOException;

@Component
@RequiredArgsConstructor
public class CustomOAuth2SuccessHandler implements AuthenticationSuccessHandler {

    private final AuthService authService;

    @Value("${jwt.refresh-token-expiration:2592000000}")
    private long refreshTokenDurationMs;

    @Value("${security.cookies.secure:true}")
    private boolean secureCookie;

    @Value("${app.frontend-base-url:http://localhost:5173}")
    private String frontendBaseUrl;

    @Value("${app.oauth2-redirect-path:/oauth2/redirect}")
    private String oauthRedirectPath;

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response,
                                        Authentication authentication) throws IOException, ServletException {

        OAuth2AuthenticationToken oauth = (OAuth2AuthenticationToken) authentication;
        OAuth2User principal = oauth.getPrincipal();

        String email = principal.getAttribute("email");
        String name = principal.getAttribute("name");
        String providerId = principal.getAttribute("sub");
        AuthProvider provider = parseProvider(oauth.getAuthorizedClientRegistrationId());

        User user = authService.processOAuth2User(email, name, providerId, provider);

        AuthService.TokenResponseWithRefresh tokens = authService.generateTokenPairForOAuth2(
                user, request.getHeader(HttpHeaders.USER_AGENT), request.getRemoteAddr());

        CookieUtils.addCookie(response, CookieUtils.REFRESH_TOKEN_COOKIE_NAME,
                tokens.getRefreshToken(),
                (int) (refreshTokenDurationMs / 1000),
                secureCookie);

        // Token in URL fragment so it's not sent in Referer / not logged at proxies.
        String redirect = UriComponentsBuilder.fromUriString(frontendBaseUrl)
                .path(oauthRedirectPath)
                .fragment("token=" + tokens.getAccessToken())
                .build(true)
                .toUriString();
        response.sendRedirect(redirect);
    }

    private static AuthProvider parseProvider(String registrationId) {
        return switch (registrationId == null ? "" : registrationId.toLowerCase()) {
            case "github" -> AuthProvider.GITHUB;
            case "google" -> AuthProvider.GOOGLE;
            default -> AuthProvider.LOCAL;
        };
    }
}
