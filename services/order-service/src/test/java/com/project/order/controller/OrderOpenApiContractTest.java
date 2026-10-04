package com.project.order.controller;

import com.project.order.service.OrderService;
import com.project.order.validation.OrderAccessValidator;
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
import static org.hamcrest.Matchers.hasItem;

@WebMvcTest(value = OrderController.class, properties = "spring.cloud.config.enabled=false")
@AutoConfigureMockMvc(addFilters = false)
@ContextConfiguration(classes = OrderOpenApiContractTest.OpenApiTestApplication.class)
@Import({SpringDocConfiguration.class, SpringDocWebMvcConfiguration.class, SpringDocSecurityConfiguration.class,
        SpringDocPageableConfiguration.class, SpringDocSortConfiguration.class})
class OrderOpenApiContractTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private OrderService orderService;

    @MockBean
    private OrderAccessValidator orderAccessValidator;

    @Test
    void orderApiDocumentsIdempotencySecurityAndErrors() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.components.securitySchemes.bearerAuth.scheme").value("bearer"))
                .andExpect(jsonPath("$.paths['/api/v1/orders'].post.security[0].bearerAuth").exists())
                .andExpect(jsonPath("$.paths['/api/v1/orders'].post.parameters[?(@.name == 'X-Idempotency-Key')]")
                        .isNotEmpty())
                .andExpect(jsonPath("$.paths['/api/v1/orders'].post.parameters[?(@.name == 'X-Idempotency-Key')].required")
                        .value(hasItem(false)))
                .andExpect(jsonPath("$.paths['/api/v1/orders'].post.parameters[?(@.name == 'X-Idempotency-Key')].description")
                        .isNotEmpty())
                .andExpect(jsonPath("$.paths['/api/v1/orders'].post.responses['400']").exists())
                .andExpect(jsonPath("$.paths['/api/v1/orders'].post.responses['401']").exists())
                .andExpect(jsonPath("$.paths['/api/v1/orders'].post.responses['403']").exists())
                .andExpect(jsonPath("$.paths['/api/v1/orders/{orderId}'].get.responses['404']").exists())
                .andExpect(jsonPath("$.paths['/api/v1/orders/{orderId}/status'].put.responses['400']").exists())
                .andExpect(jsonPath("$.paths['/api/v1/orders/{orderId}/status'].put.responses['403']").exists());
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration
    @EnableConfigurationProperties(SpringDocConfigProperties.class)
    @Import(OrderController.class)
    static class OpenApiTestApplication {
    }
}
