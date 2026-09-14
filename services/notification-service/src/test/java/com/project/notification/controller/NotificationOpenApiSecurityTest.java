package com.project.notification.controller;

import com.project.common.exception.GlobalExceptionHandler;
import com.project.notification.config.NotificationOpenApiConfiguration;
import com.project.notification.config.SecurityConfig;
import com.project.notification.service.NotificationService;
import org.junit.jupiter.api.Test;
import org.springdoc.core.configuration.SpringDocConfiguration;
import org.springdoc.core.configuration.SpringDocPageableConfiguration;
import org.springdoc.core.configuration.SpringDocSecurityConfiguration;
import org.springdoc.core.configuration.SpringDocSortConfiguration;
import org.springdoc.core.properties.SpringDocConfigProperties;
import org.springdoc.webmvc.core.configuration.SpringDocWebMvcConfiguration;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.mongodb.core.mapping.MongoMappingContext;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(value = NotificationController.class, properties = {
        "spring.cloud.config.enabled=false",
        "spring.config.import=optional:file:/dev/null",
        "springdoc.api-docs.enabled=true"
})
@Import({SecurityConfig.class, NotificationOpenApiConfiguration.class, GlobalExceptionHandler.class,
        SpringDocConfiguration.class, SpringDocWebMvcConfiguration.class, SpringDocSecurityConfiguration.class,
        SpringDocPageableConfiguration.class, SpringDocSortConfiguration.class})
@EnableConfigurationProperties(SpringDocConfigProperties.class)
class NotificationOpenApiSecurityTest {

    private static final UUID OWNER_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private NotificationService notificationService;

    @MockBean
    private JwtDecoder jwtDecoder;

    @MockBean(name = "mongoMappingContext")
    private MongoMappingContext mongoMappingContext;

    @Test
    void anonymousReadReturnsBearerChallengeAndSharedErrorBody() throws Exception {
        mockMvc.perform(get("/api/v1/notifications"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("WWW-Authenticate", org.hamcrest.Matchers.containsString("Bearer")))
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
    }

    @Test
    void authenticatedReadUsesJwtOwner() throws Exception {
        when(jwtDecoder.decode("owner-token")).thenReturn(jwt(OWNER_ID));
        when(notificationService.listForUser(org.mockito.ArgumentMatchers.eq(OWNER_ID), any()))
                .thenReturn(Page.empty());

        mockMvc.perform(get("/api/v1/notifications")
                        .header("Authorization", "Bearer owner-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content").isArray());
    }

    @Test
    void openApiDocumentsBearerAuthenticationAndAuthorizationErrors() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.components.securitySchemes.bearerAuth.type").value("http"))
                .andExpect(jsonPath("$.paths['/api/v1/notifications'].get.security[0].bearerAuth").isArray())
                .andExpect(jsonPath("$.paths['/api/v1/notifications'].get.responses['401']").exists())
                .andExpect(jsonPath("$.paths['/api/v1/notifications'].get.responses['403']").exists());
    }

    private Jwt jwt(UUID subject) {
        Instant now = Instant.parse("2026-09-12T10:00:00Z");
        return Jwt.withTokenValue("owner-token")
                .header("alg", "none")
                .subject(subject.toString())
                .issuedAt(now)
                .expiresAt(now.plusSeconds(300))
                .build();
    }
}
