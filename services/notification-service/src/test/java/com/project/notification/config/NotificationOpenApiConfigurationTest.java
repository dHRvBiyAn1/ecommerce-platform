package com.project.notification.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class NotificationOpenApiConfigurationTest {

  @Test
  void documentsJwtAuthentication() {
    var openApi = new NotificationOpenApiConfiguration().notificationOpenApi();

    assertThat(openApi.getInfo().getTitle()).isEqualTo("Notification Service API");
    assertThat(openApi.getComponents().getSecuritySchemes()).containsKey("bearerAuth");
  }
}
