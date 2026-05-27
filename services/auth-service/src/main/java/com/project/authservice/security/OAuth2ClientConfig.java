package com.project.authservice.security;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository;
import org.springframework.security.config.oauth2.client.CommonOAuth2Provider;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * Programmatically registers OAuth2 social-login providers ONLY when their
 * client-id env vars are non-empty.
 *
 * <p>Spring Boot's default {@code spring.security.oauth2.client.registration.*}
 * YAML config validates strictly: an empty client-id throws on startup. By
 * registering providers in code we skip the validation entirely when the
 * deployment doesn't have credentials configured (the default for first-run
 * local dev).
 *
 * <p>Set in {@code .env}:
 * <pre>
 *   GOOGLE_CLIENT_ID=...
 *   GOOGLE_CLIENT_SECRET=...
 *   GITHUB_CLIENT_ID=...
 *   GITHUB_CLIENT_SECRET=...
 * </pre>
 *
 * <p>If neither is set, a no-op {@link ClientRegistrationRepository} is returned
 * and the {@code .oauth2Login(...)} chain in {@link SecurityConfig} is skipped.
 */
@Slf4j
@Configuration
public class OAuth2ClientConfig {

    @Value("${oauth2.google.client-id:}")
    private String googleId;
    @Value("${oauth2.google.client-secret:}")
    private String googleSecret;

    @Value("${oauth2.github.client-id:}")
    private String githubId;
    @Value("${oauth2.github.client-secret:}")
    private String githubSecret;

    @Bean
    public ClientRegistrationRepository clientRegistrationRepository() {
        List<ClientRegistration> registrations = new ArrayList<>();

        if (StringUtils.hasText(googleId) && StringUtils.hasText(googleSecret)) {
            registrations.add(CommonOAuth2Provider.GOOGLE
                    .getBuilder("google")
                    .clientId(googleId)
                    .clientSecret(googleSecret)
                    .build());
            log.info("OAuth2: registered Google provider");
        }
        if (StringUtils.hasText(githubId) && StringUtils.hasText(githubSecret)) {
            registrations.add(CommonOAuth2Provider.GITHUB
                    .getBuilder("github")
                    .clientId(githubId)
                    .clientSecret(githubSecret)
                    .build());
            log.info("OAuth2: registered GitHub provider");
        }

        if (registrations.isEmpty()) {
            log.info("OAuth2: no social providers configured (set GOOGLE_CLIENT_ID / GITHUB_CLIENT_ID to enable)");
            // No-op repository: lookups return null. SecurityConfig will skip the
            // oauth2Login() chain so nothing iterates the empty registration set.
            return registrationId -> null;
        }

        return new InMemoryClientRegistrationRepository(registrations);
    }

    /**
     * True when at least one OAuth2 provider is configured. Used by
     * {@link SecurityConfig} to decide whether to wire {@code oauth2Login()}.
     */
    @Bean
    public OAuth2EnabledFlag oauth2EnabledFlag() {
        boolean enabled =
                (StringUtils.hasText(googleId) && StringUtils.hasText(googleSecret))
                        || (StringUtils.hasText(githubId) && StringUtils.hasText(githubSecret));
        return new OAuth2EnabledFlag(enabled);
    }

    public record OAuth2EnabledFlag(boolean enabled) {}
}
