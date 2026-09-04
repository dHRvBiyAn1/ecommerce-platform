package com.project.notification.config;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class NotificationOpenApiConfigurationTest {

    @Test
    void documentsJwtAuthentication() {
        var openApi = new NotificationOpenApiConfiguration().notificationOpenApi();

        assertThat(openApi.getInfo().getTitle()).isEqualTo("Notification Service API");
        assertThat(openApi.getComponents().getSecuritySchemes()).containsKey("bearerAuth");
    }
}
