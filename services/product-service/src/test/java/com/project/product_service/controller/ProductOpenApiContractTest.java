package com.project.product_service.controller;

import com.project.product_service.service.ProductService;
import com.project.product_service.service.CategoryService;
import org.junit.jupiter.api.Test;
import org.springdoc.core.configuration.SpringDocConfiguration;
import org.springdoc.core.configuration.SpringDocPageableConfiguration;
import org.springdoc.core.configuration.SpringDocSecurityConfiguration;
import org.springdoc.core.configuration.SpringDocSortConfiguration;
import org.springdoc.core.properties.SpringDocConfigProperties;
import org.springdoc.webmvc.core.configuration.SpringDocWebMvcConfiguration;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(value = ProductController.class, properties = "spring.cloud.config.enabled=false")
@AutoConfigureMockMvc(addFilters = false)
@ContextConfiguration(classes = ProductOpenApiContractTest.OpenApiTestApplication.class)
@Import({SpringDocConfiguration.class, SpringDocWebMvcConfiguration.class, SpringDocSecurityConfiguration.class,
        SpringDocPageableConfiguration.class, SpringDocSortConfiguration.class})
class ProductOpenApiContractTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ProductService productService;

    @MockBean
    private CategoryService categoryService;

    @Test
    void apiDocsDescribePublicCatalogAndSecuredSellerMutationErrors() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/api/v1/products/{id}'].get.responses.404").exists())
                .andExpect(jsonPath("$.paths['/api/v1/products/{id}/stock'].patch.responses.401").exists())
                .andExpect(jsonPath("$.paths['/api/v1/products/{id}/stock'].patch.responses.403").exists())
                .andExpect(jsonPath("$.paths['/api/v1/products/{id}/stock'].patch.security[0].bearerAuth").exists())
                .andExpect(jsonPath("$.paths['/api/v1/products'].post.responses.401").exists())
                .andExpect(jsonPath("$.paths['/api/v1/products'].post.responses.403").exists())
                .andExpect(jsonPath("$.paths['/api/v1/products/{id}'].put.responses.401").exists())
                .andExpect(jsonPath("$.paths['/api/v1/products/{id}'].delete.responses.403").exists())
                .andExpect(jsonPath("$.paths['/api/v1/products/{id}/active'].put.responses.403").exists())
                .andExpect(jsonPath("$.paths['/api/v1/products/{id}/approve'].put.responses.403").exists())
                .andExpect(jsonPath("$.paths['/api/v1/products/{id}/reject'].put.responses.403").exists())
                .andExpect(jsonPath("$.paths['/api/v1/products/seller'].get.responses.401").exists())
                .andExpect(jsonPath("$.paths['/api/v1/products/seller/{sellerId}'].get.responses.403").exists())
                .andExpect(jsonPath("$.paths['/api/v1/products/admin/by-status'].get.responses.403").exists())
                .andExpect(jsonPath("$.paths['/api/v1/categories'].post.responses.403").exists())
                .andExpect(jsonPath("$.paths['/api/v1/categories/{id}'].put.responses.401").exists())
                .andExpect(jsonPath("$.paths['/api/v1/categories/{id}'].delete.responses.403").exists())
                .andExpect(jsonPath("$.components.securitySchemes.bearerAuth.scheme").value("bearer"));
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration
    @EnableConfigurationProperties(SpringDocConfigProperties.class)
    @Import({ProductController.class, CategoryController.class})
    static class OpenApiTestApplication {
    }
}
