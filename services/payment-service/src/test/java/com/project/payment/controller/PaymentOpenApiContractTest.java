package com.project.payment.controller;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springdoc.core.configuration.SpringDocConfiguration;
import org.springdoc.core.configuration.SpringDocPageableConfiguration;
import org.springdoc.core.configuration.SpringDocSecurityConfiguration;
import org.springdoc.core.configuration.SpringDocSortConfiguration;
import org.springdoc.core.properties.SpringDocConfigProperties;
import org.springdoc.webmvc.core.configuration.SpringDocWebMvcConfiguration;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.mongodb.core.mapping.MongoMappingContext;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(
    value = PaymentController.class,
    properties = {
      "spring.cloud.config.enabled=false",
      "spring.config.import=optional:file:/dev/null",
      "springdoc.api-docs.enabled=true"
    })
@Import({
  com.project.payment.config.SecurityConfig.class,
  com.project.common.exception.GlobalExceptionHandler.class,
  com.project.payment.generated.mapper.PaymentMapperImpl.class,
  SpringDocConfiguration.class,
  SpringDocWebMvcConfiguration.class,
  SpringDocSecurityConfiguration.class,
  SpringDocPageableConfiguration.class,
  SpringDocSortConfiguration.class
})
@EnableConfigurationProperties(SpringDocConfigProperties.class)
class PaymentOpenApiContractTest {

  @Autowired private MockMvc mockMvc;

  @MockitoBean private com.project.payment.service.PaymentService paymentService;

  @MockitoBean
  private com.project.payment.application.validator.PaymentAccessValidator accessValidator;

  @MockitoBean private JwtDecoder jwtDecoder;

  @MockitoBean(name = "mongoMappingContext")
  private MongoMappingContext mongoMappingContext;

  @Test
  void authenticatedApiDocsExposeRedactedPaymentAndWebhookOutcomes() throws Exception {
    mockMvc
        .perform(get("/v3/api-docs").with(jwt()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.paths['/api/v1/payments'].post.security[0].bearerAuth").isArray())
        .andExpect(jsonPath("$.paths['/api/v1/payments'].post.responses['201']").exists())
        .andExpect(jsonPath("$.paths['/api/v1/payments'].post.responses['403']").exists())
        .andExpect(jsonPath("$.paths['/api/v1/payments'].post.responses['409']").exists())
        .andExpect(jsonPath("$.paths['/api/v1/payments/webhook'].post.responses['200']").exists())
        .andExpect(jsonPath("$.paths['/api/v1/payments/webhook'].post.responses['401']").exists())
        .andExpect(jsonPath("$.paths['/api/v1/payments/webhook'].post.responses['503']").exists())
        .andExpect(
            jsonPath("$.paths['/api/v1/payments/webhook/stripe'].post.responses['200']").exists())
        .andExpect(
            jsonPath("$.paths['/api/v1/payments/webhook/stripe'].post.responses['400']").exists())
        .andExpect(
            jsonPath("$.paths['/api/v1/payments/webhook/stripe'].post.responses['503']").exists())
        .andExpect(
            jsonPath("$.components.schemas.PaymentResponse.properties.clientSecret")
                .doesNotExist());
  }
}
