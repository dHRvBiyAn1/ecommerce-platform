package com.project.product_service.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.project.product_service.config.SecurityConfig;
import com.project.product_service.service.CategoryService;
import com.project.product_service.service.ProductService;
import org.junit.jupiter.api.Test;
import org.springdoc.core.configuration.SpringDocConfiguration;
import org.springdoc.core.configuration.SpringDocPageableConfiguration;
import org.springdoc.core.configuration.SpringDocSecurityConfiguration;
import org.springdoc.core.configuration.SpringDocSortConfiguration;
import org.springdoc.core.properties.SpringDocConfigProperties;
import org.springdoc.webmvc.core.configuration.SpringDocWebMvcConfiguration;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(value = ProductController.class, properties = "spring.cloud.config.enabled=false")
@AutoConfigureMockMvc(addFilters = false)
@ContextConfiguration(classes = ProductOpenApiContractTest.OpenApiTestApplication.class)
@Import({
  SpringDocConfiguration.class,
  SpringDocWebMvcConfiguration.class,
  SpringDocSecurityConfiguration.class,
  SpringDocPageableConfiguration.class,
  SpringDocSortConfiguration.class,
  SecurityConfig.class
})
class ProductOpenApiContractTest {

  @Autowired private MockMvc mockMvc;

  @MockitoBean private ProductService productService;

  @MockitoBean private CategoryService categoryService;

  @MockitoBean private JwtDecoder jwtDecoder;

  @Test
  void apiDocsDescribePublicCatalogAndSecuredSellerMutationErrors() throws Exception {
    mockMvc
        .perform(get("/v3/api-docs"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.paths['/api/v1/products/{id}'].get.responses.404").exists())
        .andExpect(jsonPath("$.paths['/api/v1/products/{id}/stock'].patch.responses.401").exists())
        .andExpect(jsonPath("$.paths['/api/v1/products/{id}/stock'].patch.responses.403").exists())
        .andExpect(
            jsonPath("$.paths['/api/v1/products/{id}/stock'].patch.security[0].bearerAuth")
                .exists())
        .andExpect(jsonPath("$.paths['/api/v1/products'].post.responses.401").exists())
        .andExpect(jsonPath("$.paths['/api/v1/products'].post.responses.403").exists())
        .andExpect(jsonPath("$.paths['/api/v1/products/{id}'].put.responses.401").exists())
        .andExpect(jsonPath("$.paths['/api/v1/products/{id}'].delete.responses.403").exists())
        .andExpect(jsonPath("$.paths['/api/v1/products/{id}/active'].put.responses.403").exists())
        .andExpect(jsonPath("$.paths['/api/v1/products/{id}/approve'].put.responses.403").exists())
        .andExpect(jsonPath("$.paths['/api/v1/products/{id}/reject'].put.responses.403").exists())
        .andExpect(jsonPath("$.paths['/api/v1/products/seller'].get.responses.401").exists())
        .andExpect(
            jsonPath("$.paths['/api/v1/products/seller/{sellerId}'].get.responses.403").exists())
        .andExpect(
            jsonPath("$.paths['/api/v1/products/admin/by-status'].get.responses.403").exists())
        .andExpect(jsonPath("$.paths['/api/v1/categories'].post.responses.403").exists())
        .andExpect(jsonPath("$.paths['/api/v1/categories/{id}'].put.responses.401").exists())
        .andExpect(jsonPath("$.paths['/api/v1/categories/{id}'].delete.responses.403").exists())
        .andExpect(jsonPath("$.components.securitySchemes.bearerAuth.scheme").value("bearer"));
  }

  @org.springframework.context.annotation.Configuration(proxyBeanMethods = false)
  @org.springframework.boot.test.context.TestComponent
  @EnableAutoConfiguration
  @EnableConfigurationProperties(SpringDocConfigProperties.class)
  @Import({ProductController.class, CategoryController.class})
  static class OpenApiTestApplication {}
}
