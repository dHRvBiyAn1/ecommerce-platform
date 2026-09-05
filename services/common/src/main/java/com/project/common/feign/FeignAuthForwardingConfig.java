package com.project.common.feign;

import feign.RequestInterceptor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

/**
 * Forwards the caller's JWT from the inbound request to outbound Feign calls so
 * downstream services can verify the same identity. Without this, service-to-service
 * Feign calls would arrive unauthenticated.
 */
@Configuration
@ConditionalOnClass(name = "feign.RequestInterceptor")
@EnableConfigurationProperties(ServiceAuthProperties.class)
public class FeignAuthForwardingConfig {

    @Bean
    @ConditionalOnProperty(prefix = "service.auth", name = "enabled", havingValue = "true")
    public ServiceTokenClient serviceTokenClient() {
        return new HttpServiceTokenClient();
    }

    @Bean
    @ConditionalOnProperty(prefix = "service.auth", name = "enabled", havingValue = "true")
    public ServiceTokenProvider serviceTokenProvider(
            ServiceTokenClient client, ServiceAuthProperties properties) {
        return new ServiceTokenProvider(client, properties);
    }

    @Bean
    public RequestInterceptor bearerTokenForwardingInterceptor(
            ObjectProvider<ServiceTokenProvider> serviceTokenProvider,
            ServiceAuthProperties properties, org.springframework.core.env.Environment environment) {
        boolean required = java.util.Set.of("order-service", "payment-service", "cart-service")
                .contains(environment.getProperty("spring.application.name", ""));
        boolean configured = java.util.stream.Stream.of("enabled", "token-uri", "client-id", "client-secret", "scope")
                .anyMatch(field -> environment.containsProperty("service.auth." + field));
        if (required || configured) {
            if (!environment.getProperty("service.auth.enabled", Boolean.class, false)
                    || !org.springframework.util.StringUtils.hasText(properties.getTokenUri())
                    || !org.springframework.util.StringUtils.hasText(properties.getClientId())
                    || !org.springframework.util.StringUtils.hasText(properties.getClientSecret())
                    || !org.springframework.util.StringUtils.hasText(properties.getScope())) {
                throw new IllegalStateException("Service authentication must be enabled and fully configured");
            }
        }
        return template -> {
            ServiceTokenProvider provider = serviceTokenProvider.getIfAvailable();
            if (provider != null) {
                String token = provider.getAccessToken();
                template.removeHeader("Authorization");
                template.header("Authorization", "Bearer " + token);
                return;
            }
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth instanceof JwtAuthenticationToken jwt) {
                template.header("Authorization", "Bearer " + jwt.getToken().getTokenValue());
            }
        };
    }
}
