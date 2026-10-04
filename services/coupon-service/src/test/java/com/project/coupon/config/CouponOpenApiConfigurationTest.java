package com.project.coupon.config;

import io.swagger.v3.oas.models.security.SecurityScheme;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CouponOpenApiConfigurationTest {

    @Test
    void exposesBearerJwtSecurityScheme() {
        var openApi = new CouponOpenApiConfiguration().couponOpenApi();

        assertThat(openApi.getInfo().getTitle()).isEqualTo("Coupon Service API");
        assertThat(openApi.getComponents().getSecuritySchemes().get("bearerAuth").getType())
                .isEqualTo(SecurityScheme.Type.HTTP);
    }
}
