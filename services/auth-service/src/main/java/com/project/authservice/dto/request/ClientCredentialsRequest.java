package com.project.authservice.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "OAuth client credentials grant parameters")
public record ClientCredentialsRequest(
        @Schema(name = "client_id", example = "order-service") String clientId,
        @Schema(name = "client_secret", accessMode = Schema.AccessMode.WRITE_ONLY) String clientSecret,
        @Schema(description = "Optional space-delimited requested scopes") String scope) {
}
