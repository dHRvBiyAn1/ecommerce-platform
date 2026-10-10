package com.project.inventory.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.project.inventory.application.validator.InventoryValidator;
import com.project.inventory.config.InventoryOpenApiConfiguration;
import com.project.inventory.generated.mapper.InventoryApiMapperImpl;
import com.project.inventory.service.InventoryService;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springdoc.core.configuration.SpringDocConfiguration;
import org.springdoc.core.configuration.SpringDocPageableConfiguration;
import org.springdoc.core.configuration.SpringDocSecurityConfiguration;
import org.springdoc.core.configuration.SpringDocSortConfiguration;
import org.springdoc.core.properties.SpringDocConfigProperties;
import org.springdoc.webmvc.core.configuration.SpringDocWebMvcConfiguration;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestComponent;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(value = InventoryController.class, properties = "spring.cloud.config.enabled=false")
@AutoConfigureMockMvc(addFilters = false)
@ContextConfiguration(classes = InventoryOpenApiContractTest.OpenApiTestApplication.class)
@ImportAutoConfiguration({
  SpringDocConfiguration.class,
  SpringDocWebMvcConfiguration.class,
  SpringDocSecurityConfiguration.class,
  SpringDocPageableConfiguration.class,
  SpringDocSortConfiguration.class
})
class InventoryOpenApiContractTest {

  @Autowired private MockMvc mockMvc;

  @Test
  void apiDocsExposeTheSecuredPublicInventoryContract() throws Exception {
    mockMvc
        .perform(get("/v3/api-docs"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.paths['/api/v1/inventory']").exists())
        .andExpect(jsonPath("$.paths['/api/v1/inventory/{id}']").exists())
        .andExpect(jsonPath("$.paths['/api/v1/inventory/{productId}/reserve']").exists())
        .andExpect(jsonPath("$.paths['/api/v1/inventory/{productId}/commit']").exists())
        .andExpect(jsonPath("$.components.securitySchemes.bearerAuth.type").value("http"))
        .andExpect(jsonPath("$.components.securitySchemes.bearerAuth.scheme").value("bearer"))
        .andExpect(jsonPath("$.components.securitySchemes.bearerAuth.bearerFormat").value("JWT"))
        .andExpect(jsonPath("$.paths['/api/v1/inventory'].post.security[0].bearerAuth").exists())
        .andExpect(
            jsonPath(
                    "$.paths['/api/v1/inventory'].post.responses.400.content['application/json'].schema.$ref")
                .value("#/components/schemas/ErrorResponse"))
        .andExpect(
            jsonPath(
                    "$.paths['/api/v1/inventory/{id}'].get.responses.404.content['application/json'].schema.$ref")
                .value("#/components/schemas/ErrorResponse"))
        .andExpect(jsonPath("$.components.responses.ValidationError").exists())
        .andExpect(jsonPath("$.components.responses.NotFoundError").exists())
        .andExpect(jsonPath("$.paths['/internal/inventory/{productId}/reserve']").doesNotExist());
  }

  @TestConfiguration(proxyBeanMethods = false)
  @EnableConfigurationProperties(SpringDocConfigProperties.class)
  static class OpenApiTestConfiguration {

    @Bean
    InventoryService inventoryService() {
      return new InventoryService() {
        @Override
        public Page<com.project.inventory.generated.model.InventoryResponse> getAllInventory(
            Pageable pageable) {
          throw new UnsupportedOperationException();
        }

        @Override
        public com.project.inventory.generated.model.InventoryResponse getByProductId(
            String productId) {
          throw new UnsupportedOperationException();
        }

        @Override
        public com.project.inventory.generated.model.InventoryResponse getBySku(String sku) {
          throw new UnsupportedOperationException();
        }

        @Override
        public com.project.inventory.generated.model.InventoryResponse createInventory(
            com.project.inventory.generated.model.InventoryRequest request) {
          throw new UnsupportedOperationException();
        }

        @Override
        public com.project.inventory.generated.model.InventoryResponse updateInventory(
            String id, com.project.inventory.generated.model.InventoryRequest request) {
          throw new UnsupportedOperationException();
        }

        @Override
        public void deleteInventory(String id) {
          throw new UnsupportedOperationException();
        }

        @Override
        public com.project.inventory.generated.model.InventoryResponse reserveStock(
            String productId, int quantity, String orderId) {
          throw new UnsupportedOperationException();
        }

        @Override
        public com.project.inventory.generated.model.InventoryResponse commitStock(
            String productId, int quantity, String orderId) {
          throw new UnsupportedOperationException();
        }

        @Override
        public com.project.inventory.generated.model.InventoryResponse releaseStock(
            String productId, int quantity, String orderId) {
          throw new UnsupportedOperationException();
        }

        @Override
        public com.project.inventory.generated.model.InventoryResponse addStock(
            String productId, int quantity) {
          throw new UnsupportedOperationException();
        }

        @Override
        public List<com.project.inventory.generated.model.InventoryResponse> getLowStockItems() {
          throw new UnsupportedOperationException();
        }

        @Override
        public boolean isInStock(String productId, int quantity) {
          throw new UnsupportedOperationException();
        }
      };
    }

    @Bean
    InventoryValidator inventoryValidator() {
      return new InventoryValidator();
    }
  }

  @Configuration(proxyBeanMethods = false)
  @TestComponent
  @EnableAutoConfiguration
  @Import({
    InventoryController.class,
    InventoryOpenApiConfiguration.class,
    OpenApiTestConfiguration.class,
    InventoryApiMapperImpl.class
  })
  static class OpenApiTestApplication {}
}
