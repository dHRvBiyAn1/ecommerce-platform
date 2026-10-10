package com.project.common.web;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.project.common.generated.model.ErrorResponse;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

class ResponsesSerializationTest {
  @Test
  void serializesNullDataAsAnExplicitEnvelopeField() throws Exception {
    ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule());

    String json = mapper.writeValueAsString(Responses.success(null));

    assertThat(json).contains("\"data\":null");
  }

  @Test
  void preservesEnvelopeMetadataAcrossSuccessCreationAndErrors() {
    var success = Responses.success("Saved", "payload");
    var created = Responses.created("payload");
    var error = Responses.error(409, "Conflict", "details");
    var emptyError = Responses.error(503, "Unavailable");
    assertThat(success.getStatus()).isEqualTo(200);
    assertThat(success.getMessage()).isEqualTo("Saved");
    assertThat(success.getData()).isEqualTo("payload");
    assertThat(UUID.fromString(success.getTraceId())).isNotNull();
    assertThat(success.getTimestamp()).isNotNull();
    assertThat(created.getStatus()).isEqualTo(201);
    assertThat(created.getMessage()).isEqualTo("Created");
    assertThat(created.getTraceId()).isNotEqualTo(success.getTraceId());
    assertThat(error.getStatus()).isEqualTo(409);
    assertThat(error.getData()).isEqualTo("details");
    assertThat(emptyError.getData()).isNull();
  }

  @Test
  void preservesPageMetadataAndWirePropertyNames() throws Exception {
    var page = Responses.page(new PageImpl<>(List.of("item"), PageRequest.of(1, 1), 3));
    assertThat(page.getContent()).containsExactly("item");
    assertThat(page.getPage()).isEqualTo(1);
    assertThat(page.getSize()).isEqualTo(1);
    assertThat(page.getTotalElements()).isEqualTo(3L);
    assertThat(page.getTotalPages()).isEqualTo(3);
    assertThat(page.getFirst()).isFalse();
    assertThat(page.getLast()).isFalse();
    var json = new ObjectMapper().readTree(new ObjectMapper().writeValueAsString(page));
    assertThat(json.get("first").booleanValue()).isFalse();
    assertThat(json.get("last").booleanValue()).isFalse();
  }

  @Test
  void preservesSecurityErrorFieldsAndOmitsOptionalNulls() throws Exception {
    var response =
        Responses.errorResponse(
            400,
            "VALIDATION_ERROR",
            "Invalid",
            "/cart",
            Map.of("quantity", "Quantity must be at least 1"),
            "request-id");
    assertThat(response.getError()).isEqualTo("Bad Request");
    assertThat(response.getFieldErrors()).containsEntry("quantity", "Quantity must be at least 1");
    assertThat(response.getTimestamp()).isNotNull();
    var mapper = new ObjectMapper().registerModule(new JavaTimeModule());
    var json = mapper.readTree(mapper.writeValueAsString(response));
    assertThat(json.get("traceId").asText()).isEqualTo("request-id");
    var withoutFields =
        Responses.errorResponse(401, "UNAUTHORIZED", "Required", "/cart", null, null);
    var withoutJson = mapper.readTree(mapper.writeValueAsString(withoutFields));
    assertThat(withoutJson.has("fieldErrors")).isFalse();
    assertThat(withoutJson.has("traceId")).isFalse();
  }

  @Test
  void usesSafeReasonPhraseForNonstandardStatus() {
    ErrorResponse response =
        Responses.errorResponse(
            599, "UPSTREAM_ERROR", "Request failed", "/checkout", null, "request-id");

    assertThat(response.getStatus()).isEqualTo(599);
    assertThat(response.getError()).isEqualTo("Unknown Status");
  }
}
