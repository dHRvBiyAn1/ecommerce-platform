package com.project.coupon.controller;

import com.project.coupon.config.CouponOpenApiConfiguration;
import com.project.coupon.service.CouponService;
import com.project.coupon.validation.CouponRequestValidator;
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

@WebMvcTest(value = CouponController.class, properties = {
        "spring.cloud.config.enabled=false",
        "spring.config.import=optional:file:/dev/null"
})
@AutoConfigureMockMvc(addFilters = false)
@ContextConfiguration(classes = CouponOpenApiContractTest.OpenApiTestApplication.class)
@Import({SpringDocConfiguration.class, SpringDocWebMvcConfiguration.class, SpringDocSecurityConfiguration.class,
        SpringDocPageableConfiguration.class, SpringDocSortConfiguration.class, CouponOpenApiConfiguration.class})
class CouponOpenApiContractTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private CouponService couponService;

    @MockBean
    private CouponRequestValidator requestValidator;

    @MockBean
    private JwtDecoder jwtDecoder;

    @Test
    void couponEndpointsDocumentBearerSecurityAndCommonErrors() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.components.securitySchemes.bearerAuth.scheme").value("bearer"))
                .andExpect(jsonPath("$.components.schemas.ErrorResponse.properties.status.format").value("int32"))
                .andExpect(jsonPath("$.components.schemas.ErrorResponse.properties.code.type").value("string"))
                .andExpect(jsonPath("$.components.schemas.ErrorResponse.properties.fieldErrors.type").value("object"))
                .andExpect(jsonPath("$.components.responses.UnauthorizedError.content['application/json'].schema.$ref")
                        .value("#/components/schemas/ErrorResponse"))
                .andExpect(jsonPath("$.components.responses.ForbiddenError.content['application/json'].schema.$ref")
                        .value("#/components/schemas/ErrorResponse"))
                .andExpect(jsonPath("$.paths['/api/v1/coupons'].post.security[0].bearerAuth").exists())
                .andExpect(jsonPath("$.paths['/api/v1/coupons'].post.responses['401'].$ref")
                        .value("#/components/responses/UnauthorizedError"))
                .andExpect(jsonPath("$.paths['/api/v1/coupons'].post.responses['403'].$ref")
                        .value("#/components/responses/ForbiddenError"))
                .andExpect(jsonPath("$.paths['/api/v1/coupons/{id}'].put.responses['403'].$ref")
                        .value("#/components/responses/ForbiddenError"))
                .andExpect(jsonPath("$.paths['/api/v1/coupons/{id}'].delete.responses['403'].$ref")
                        .value("#/components/responses/ForbiddenError"))
                .andExpect(jsonPath("$.paths['/api/v1/coupons/validate'].post.security[0].bearerAuth").exists())
                .andExpect(jsonPath("$.paths['/api/v1/coupons/validate'].post.responses['401'].$ref")
                        .value("#/components/responses/UnauthorizedError"))
                .andExpect(jsonPath("$.paths['/api/v1/coupons/validate'].post.responses['403'].$ref")
                        .value("#/components/responses/ForbiddenError"))
                .andExpect(jsonPath("$.paths['/api/v1/coupons/reserve'].post.security[0].bearerAuth").exists())
                .andExpect(jsonPath("$.paths['/api/v1/coupons/reserve'].post.responses['401'].$ref")
                        .value("#/components/responses/UnauthorizedError"))
                .andExpect(jsonPath("$.paths['/api/v1/coupons/reserve'].post.responses['403'].$ref")
                        .value("#/components/responses/ForbiddenError"))
                .andExpect(jsonPath("$.paths['/api/v1/coupons/release'].post.security[0].bearerAuth").exists())
                .andExpect(jsonPath("$.paths['/api/v1/coupons/release'].post.responses['401'].$ref")
                        .value("#/components/responses/UnauthorizedError"))
                .andExpect(jsonPath("$.paths['/api/v1/coupons/release'].post.responses['403'].$ref")
                        .value("#/components/responses/ForbiddenError"))
                .andExpect(jsonPath("$.paths['/api/v1/coupons/redeem'].post.security[0].bearerAuth").exists())
                .andExpect(jsonPath("$.paths['/api/v1/coupons/redeem'].post.responses['403'].$ref")
                        .value("#/components/responses/ForbiddenError"))
                .andExpect(jsonPath("$.paths['/api/v1/coupons/commit'].post.responses['403'].$ref")
                        .value("#/components/responses/ForbiddenError"));
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration
    @EnableConfigurationProperties(SpringDocConfigProperties.class)
    @Import(CouponController.class)
    static class OpenApiTestApplication {
    }
}
