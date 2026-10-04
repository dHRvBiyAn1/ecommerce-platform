package com.project.inventory.controller;

import com.project.inventory.service.InventoryService;
import com.project.inventory.config.InventoryOpenApiConfiguration;
import com.project.inventory.application.validator.InventoryValidator;
import org.springdoc.core.configuration.SpringDocConfiguration;
import org.springdoc.core.configuration.SpringDocPageableConfiguration;
import org.springdoc.core.configuration.SpringDocSecurityConfiguration;
import org.springdoc.core.configuration.SpringDocSortConfiguration;
import org.springdoc.core.properties.SpringDocConfigProperties;
import org.springdoc.webmvc.core.configuration.SpringDocWebMvcConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.context.ContextConfiguration;

import java.util.List;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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

    @Autowired
    private MockMvc mockMvc;

    @Test
    void apiDocsExposeTheSecuredPublicInventoryContract() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/api/v1/inventory']").exists())
                .andExpect(jsonPath("$.paths['/api/v1/inventory/{productId}']").exists())
                .andExpect(jsonPath("$.paths['/api/v1/inventory/{productId}/reserve']").exists())
                .andExpect(jsonPath("$.paths['/api/v1/inventory/{productId}/commit']").exists())
                .andExpect(jsonPath("$.components.securitySchemes.bearerAuth.type").value("http"))
                .andExpect(jsonPath("$.components.securitySchemes.bearerAuth.scheme").value("bearer"))
                .andExpect(jsonPath("$.components.securitySchemes.bearerAuth.bearerFormat").value("JWT"))
                .andExpect(jsonPath("$.paths['/api/v1/inventory'].post.responses.400.$ref")
                        .value("#/components/responses/ValidationError"))
                .andExpect(jsonPath("$.paths['/api/v1/inventory/{productId}'].get.responses.404.$ref")
                        .value("#/components/responses/NotFoundError"))
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
                public Page<com.project.inventory.api.dto.response.InventoryResponse> getAllInventory(Pageable pageable) {
                    throw new UnsupportedOperationException();
                }

                @Override
                public com.project.inventory.api.dto.response.InventoryResponse getByProductId(String productId) {
                    throw new UnsupportedOperationException();
                }

                @Override
                public com.project.inventory.api.dto.response.InventoryResponse getBySku(String sku) {
                    throw new UnsupportedOperationException();
                }

                @Override
                public com.project.inventory.api.dto.response.InventoryResponse createInventory(
                        com.project.inventory.api.dto.request.InventoryRequest request) {
                    throw new UnsupportedOperationException();
                }

                @Override
                public com.project.inventory.api.dto.response.InventoryResponse updateInventory(
                        String id, com.project.inventory.api.dto.request.InventoryRequest request) {
                    throw new UnsupportedOperationException();
                }

                @Override
                public void deleteInventory(String id) {
                    throw new UnsupportedOperationException();
                }

                @Override
                public com.project.inventory.api.dto.response.InventoryResponse reserveStock(String productId, int quantity, String orderId) {
                    throw new UnsupportedOperationException();
                }

                @Override
                public com.project.inventory.api.dto.response.InventoryResponse commitStock(String productId, int quantity, String orderId) {
                    throw new UnsupportedOperationException();
                }

                @Override
                public com.project.inventory.api.dto.response.InventoryResponse releaseStock(String productId, int quantity, String orderId) {
                    throw new UnsupportedOperationException();
                }

                @Override
                public com.project.inventory.api.dto.response.InventoryResponse addStock(String productId, int quantity) {
                    throw new UnsupportedOperationException();
                }

                @Override
                public List<com.project.inventory.api.dto.response.InventoryResponse> getLowStockItems() {
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

    @SpringBootConfiguration
    @EnableAutoConfiguration
    @Import({InventoryController.class, InventoryOpenApiConfiguration.class, OpenApiTestConfiguration.class})
    static class OpenApiTestApplication {
    }
}
