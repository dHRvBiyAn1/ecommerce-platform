package com.project.cart.controller;

import com.project.cart.service.CartService;
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
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(value = CartController.class, properties = {
        "spring.cloud.config.enabled=false",
        "spring.config.import=optional:file:/dev/null"
})
@AutoConfigureMockMvc(addFilters = false)
@ContextConfiguration(classes = CartOpenApiContractTest.OpenApiTestApplication.class)
@Import({SpringDocConfiguration.class, SpringDocWebMvcConfiguration.class, SpringDocSecurityConfiguration.class,
        SpringDocPageableConfiguration.class, SpringDocSortConfiguration.class})
class CartOpenApiContractTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private CartService cartService;

    @MockBean
    private JwtDecoder jwtDecoder;

    @Test
    void cartEndpointsDocumentBearerSecurityAndAuthenticationErrors() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.components.securitySchemes.bearerAuth.scheme").value("bearer"))
                .andExpect(jsonPath("$.components.schemas.ErrorResponse.properties.code").exists())
                .andExpect(jsonPath("$.paths['/api/v1/cart'].get.security[0].bearerAuth").exists())
                .andExpect(jsonPath("$.paths['/api/v1/cart'].delete.security[0].bearerAuth").exists())
                .andExpect(jsonPath("$.paths['/api/v1/cart/items'].post.security[0].bearerAuth").exists())
                .andExpect(jsonPath("$.paths['/api/v1/cart/items/{productId}'].patch.security[0].bearerAuth").exists())
                .andExpect(jsonPath("$.paths['/api/v1/cart/items/{productId}'].delete.security[0].bearerAuth").exists())
                .andExpect(jsonPath("$.paths['/api/v1/cart/coupon'].post.security[0].bearerAuth").exists())
                .andExpect(jsonPath("$.paths['/api/v1/cart/coupon'].delete.security[0].bearerAuth").exists())
                .andExpect(jsonPath("$.paths['/api/v1/cart'].get.responses['401'].headers['WWW-Authenticate'].schema.type")
                        .value("string"))
                .andExpect(jsonPath("$.paths['/api/v1/cart'].get.responses['401'].content['application/json'].schema.$ref")
                        .value("#/components/schemas/ErrorResponse"))
                .andExpect(jsonPath("$.paths['/api/v1/cart'].get.responses['403'].content['application/json'].schema.$ref")
                        .value("#/components/schemas/ErrorResponse"))
                .andExpect(jsonPath("$.paths['/api/v1/cart/items'].post.responses['403'].description").exists())
                .andExpect(jsonPath("$.paths['/api/v1/cart/items/{productId}'].patch.responses['401'].description").exists())
                .andExpect(jsonPath("$.paths['/api/v1/cart/coupon'].post.responses['403'].description").exists())
                .andExpect(jsonPath("$.paths['/api/v1/cart/coupon'].delete.responses['401'].description").exists());
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration
    @EnableConfigurationProperties(SpringDocConfigProperties.class)
    @Import(CartController.class)
    static class OpenApiTestApplication {
    }
}
