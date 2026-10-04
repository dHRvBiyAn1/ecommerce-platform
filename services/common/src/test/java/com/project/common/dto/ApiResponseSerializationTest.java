package com.project.common.dto;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ApiResponseSerializationTest {
    @Test
    void serializesNullDataAsAnExplicitEnvelopeField() throws Exception {
        ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule());

        String json = mapper.writeValueAsString(ApiResponse.success(null));

        assertThat(json).contains("\"data\":null");
    }

    @Test
    void usesSafeReasonPhraseForNonstandardStatus() {
        ErrorResponse response = ApiResponse.errorResponse(599, "UPSTREAM_ERROR", "Request failed",
                "/checkout", null, "request-id");

        assertThat(response.status()).isEqualTo(599);
        assertThat(response.error()).isEqualTo("Unknown Status");
    }
}
