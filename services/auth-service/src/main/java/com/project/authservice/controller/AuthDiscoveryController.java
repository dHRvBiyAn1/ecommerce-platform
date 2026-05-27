package com.project.authservice.controller;

import com.project.authservice.security.OAuth2ClientConfig;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Public discovery endpoint the frontend uses to find out which authentication
 * methods are actually wired on this deployment. Avoids broken-button UX where
 * the storefront shows a "Sign in with Google" link that returns 404 because
 * GOOGLE_CLIENT_ID isn't set.
 *
 * <p>Response shape:
 * <pre>
 * {
 *   "password": true,
 *   "providers": [
 *     { "id": "google", "label": "Google", "authorizationUrl": "/oauth2/authorization/google" }
 *   ]
 * }
 * </pre>
 */
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthDiscoveryController {

    private final OAuth2ClientConfig.OAuth2EnabledFlag oauth2Flag;

    @Value("${oauth2.google.client-id:}")
    private String googleId;
    @Value("${oauth2.github.client-id:}")
    private String githubId;

    @GetMapping("/providers")
    public Map<String, Object> providers() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("password", true);
        body.put("oauth2", oauth2Flag.enabled());

        java.util.List<Map<String, String>> providers = new java.util.ArrayList<>();
        if (!googleId.isBlank()) {
            providers.add(Map.of(
                    "id", "google",
                    "label", "Google",
                    "authorizationUrl", "/oauth2/authorization/google"
            ));
        }
        if (!githubId.isBlank()) {
            providers.add(Map.of(
                    "id", "github",
                    "label", "GitHub",
                    "authorizationUrl", "/oauth2/authorization/github"
            ));
        }
        body.put("providers", providers);
        return body;
    }
}
