package com.project.authservice.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;
import java.util.Map;
import java.util.Set;

@Validated
@ConfigurationProperties(prefix = "auth.service-clients")
public record ServiceClientProperties(
        @NotNull Duration tokenTtl,
        @NotNull Map<String, @Valid Client> clients) {

    public ServiceClientProperties {
        clients = clients == null ? Map.of() : Map.copyOf(clients);
    }

    public record Client(String secret, @NotEmpty Set<String> allowedScopes) {
        public Client {
            allowedScopes = allowedScopes == null ? Set.of() : Set.copyOf(allowedScopes);
        }
    }
}
