package com.project.inventory.config;

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

@Configuration(proxyBeanMethods = false)
public class InventoryOpenApiConfiguration {

    public static final String BEARER_AUTH = "bearerAuth";
    public static final String NOT_FOUND_ERROR = "#/components/responses/NotFoundError";
    public static final String VALIDATION_ERROR = "#/components/responses/ValidationError";

    @Bean
    OpenAPI inventoryOpenApi() {
        Components components = new Components()
                .addSchemas("ErrorResponse", errorResponseSchema())
                .addSecuritySchemes(BEARER_AUTH, new SecurityScheme()
                        .type(SecurityScheme.Type.HTTP)
                        .scheme("bearer")
                        .bearerFormat("JWT"))
                .addResponses("ValidationError", errorResponse("Request validation failed"))
                .addResponses("NotFoundError", errorResponse("Inventory resource was not found"));

        return new OpenAPI()
                .info(new Info()
                        .title("Inventory Service API")
                        .description("Inventory availability, stock management, and order reservation API")
                        .version("v1"))
                .components(components);
    }

    private ApiResponse errorResponse(String description) {
        return new ApiResponse()
                .description(description)
                .content(new Content().addMediaType(APPLICATION_JSON_VALUE,
                        new MediaType().schema(new io.swagger.v3.oas.models.media.Schema<>()
                                .$ref("#/components/schemas/ErrorResponse"))));
    }

    private Schema<?> errorResponseSchema() {
        return new ObjectSchema()
                .addProperty("status", new IntegerSchema().format("int32"))
                .addProperty("error", new StringSchema())
                .addProperty("message", new StringSchema())
                .addProperty("path", new StringSchema())
                .addProperty("code", new StringSchema())
                .addProperty("fieldErrors", new ObjectSchema().description("Validation messages keyed by field path"))
                .addProperty("traceId", new StringSchema())
                .addProperty("timestamp", new DateTimeSchema());
    }
}
