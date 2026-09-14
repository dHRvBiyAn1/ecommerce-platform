package com.project.coupon.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.DateTimeSchema;
import io.swagger.v3.oas.models.media.IntegerSchema;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.ObjectSchema;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.media.StringSchema;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;

@Configuration
public class CouponOpenApiConfiguration {

    public static final String UNAUTHORIZED_ERROR = "#/components/responses/UnauthorizedError";
    public static final String FORBIDDEN_ERROR = "#/components/responses/ForbiddenError";

    @Bean
    public OpenAPI couponOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("Coupon Service API")
                        .version("v1")
                        .description("Coupon validation and order-owned reservation lifecycle"))
                .components(new Components()
                        .addSchemas("ErrorResponse", errorResponseSchema())
                        .addSecuritySchemes("bearerAuth", new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT"))
                        .addResponses("UnauthorizedError", errorResponse("Authentication required"))
                        .addResponses("ForbiddenError", errorResponse("Access denied")));
    }

    private ApiResponse errorResponse(String description) {
        return new ApiResponse().description(description)
                .content(new Content().addMediaType(APPLICATION_JSON_VALUE,
                        new MediaType().schema(new Schema<>().$ref("#/components/schemas/ErrorResponse"))));
    }

    private Schema<?> errorResponseSchema() {
        return new ObjectSchema()
                .addProperty("status", new IntegerSchema().format("int32"))
                .addProperty("error", new StringSchema())
                .addProperty("message", new StringSchema())
                .addProperty("path", new StringSchema())
                .addProperty("code", new StringSchema())
                .addProperty("fieldErrors", new ObjectSchema())
                .addProperty("traceId", new StringSchema())
                .addProperty("timestamp", new DateTimeSchema());
    }
}
