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
}
