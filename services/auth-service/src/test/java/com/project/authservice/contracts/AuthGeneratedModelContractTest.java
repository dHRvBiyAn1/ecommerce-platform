package com.project.authservice.contracts;

import static org.assertj.core.api.Assertions.assertThat;

import com.project.authservice.generated.model.AssignRolesRequest;
import com.project.authservice.generated.model.RegistrationRequest;
import com.project.authservice.generated.model.UpdateRolePermissionsRequest;
import com.project.authservice.generated.model.UserUpdateRequest;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import java.util.List;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

class AuthGeneratedModelContractTest {
  private final ObjectMapper objectMapper = new ObjectMapper();
  private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

  @Test
  void generatedModelsRoundTripRequestJson() throws Exception {
    RegistrationRequest request =
        objectMapper.readValue(
            "{\"email\":\"customer@example.com\",\"password\":\"ValidPass123\",\"displayName\":\"Customer One\"}",
            RegistrationRequest.class);
    var json = objectMapper.readTree(objectMapper.writeValueAsString(request));
    assertThat(json.path("email").asText()).isEqualTo("customer@example.com");
    assertThat(json.path("password").asText()).isEqualTo("ValidPass123");
    assertThat(json.path("displayName").asText()).isEqualTo("Customer One");
  }

  @Test
  void partialUpdateKeepsUnspecifiedPropertiesNull() throws Exception {
    UserUpdateRequest request =
        objectMapper.readValue("{\"phone\":\"+1 555 0100\"}", UserUpdateRequest.class);
    assertThat(request.getDisplayName()).isNull();
    assertThat(request.getImageUrl()).isNull();
    assertThat(request.getPhone()).isEqualTo("+1 555 0100");
    assertThat(request.getShippingAddress()).isNull();
    assertThat(request.getBillingAddress()).isNull();
  }

  @Test
  void generatedAdminModelsRejectBlankAndNullListElements() {
    for (String blank : List.of("", " ", "\u0000\t\n")) {
      assertThat(validator.validate(new AssignRolesRequest(List.of(blank)))).isNotEmpty();
      assertThat(validator.validate(new UpdateRolePermissionsRequest(List.of(blank)))).isNotEmpty();
    }
    assertThat(validator.validate(new UpdateRolePermissionsRequest(List.of("")))).isNotEmpty();
    assertThat(
            validator.validate(new AssignRolesRequest(java.util.Collections.singletonList(null))))
        .isNotEmpty();
    assertThat(
            validator.validate(
                new UpdateRolePermissionsRequest(java.util.Collections.singletonList(null))))
        .isNotEmpty();
  }
}
