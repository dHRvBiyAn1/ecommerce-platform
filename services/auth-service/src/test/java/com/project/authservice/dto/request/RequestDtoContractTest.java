package com.project.authservice.dto.request;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

import java.lang.reflect.AnnotatedParameterizedType;
import java.lang.reflect.RecordComponent;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RequestDtoContractTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void requestDtosUseNormalizedPackagesAndRecords() {
        assertRecord("com.project.authservice.dto.request.RegistrationRequest");
        assertRecord("com.project.authservice.dto.request.UserUpdateRequest");
        assertRecord("com.project.authservice.dto.request.ChangePasswordRequest");
        assertRecord("com.project.authservice.dto.request.admin.AssignRolesRequest");
        assertRecord("com.project.authservice.dto.request.admin.CreateRoleRequest");
        assertRecord("com.project.authservice.dto.request.admin.UpdateRolePermissionsRequest");
        assertNotLoadable("com.project.authservice.dto.RegistrationRequest");
        assertNotLoadable("com.project.authservice.dto.UserUpdateRequest");
        assertNotLoadable("com.project.authservice.dto.ChangePasswordRequest");
        assertNotLoadable("com.project.authservice.dto.admin.AssignRolesRequest");
        assertNotLoadable("com.project.authservice.dto.admin.CreateRoleRequest");
        assertNotLoadable("com.project.authservice.dto.admin.UpdateRolePermissionsRequest");
    }

    @Test
    void registrationRequestRoundTripsExactJsonNames() throws Exception {
        Class<?> requestType = load("com.project.authservice.dto.request.RegistrationRequest");
        Object request = objectMapper.readValue("""
                {"email":"customer@example.com","password":"ValidPass123","displayName":"Customer One"}
                """, requestType);

        var json = objectMapper.readTree(objectMapper.writeValueAsString(request));
        assertThat(json.path("email").asText()).isEqualTo("customer@example.com");
        assertThat(json.path("password").asText()).isEqualTo("ValidPass123");
        assertThat(json.path("displayName").asText()).isEqualTo("Customer One");
    }

    @Test
    void userUpdateRequestPreservesNullFieldsWhenBindingPartialJson() throws Exception {
        Class<?> requestType = load("com.project.authservice.dto.request.UserUpdateRequest");
        Object request = objectMapper.readValue("{" + "\"phone\":\"+1 555 0100\"}", requestType);

        assertThat(requestType.getMethod("displayName").invoke(request)).isNull();
        assertThat(requestType.getMethod("imageUrl").invoke(request)).isNull();
        assertThat(requestType.getMethod("phone").invoke(request)).isEqualTo("+1 555 0100");
        assertThat(requestType.getMethod("shippingAddress").invoke(request)).isNull();
        assertThat(requestType.getMethod("billingAddress").invoke(request)).isNull();
    }

    @Test
    void adminRoleListsRejectBlankAndNullElements() throws Exception {
        assertInvalidListElement("com.project.authservice.dto.request.admin.AssignRolesRequest", "roles");
        assertInvalidListElement("com.project.authservice.dto.request.admin.UpdateRolePermissionsRequest", "permissions");
    }

    private void assertInvalidListElement(String className, String componentName) throws Exception {
        Class<?> requestType = load(className);
        RecordComponent component = requestType.getRecordComponents()[0];
        assertThat(component.getName()).isEqualTo(componentName);
        assertThat(component.getAnnotatedType()).isInstanceOf(AnnotatedParameterizedType.class);

        for (String invalidValue : List.of("", "   ")) {
            Object request = requestType.getConstructors()[0].newInstance(List.of(invalidValue));
            assertThat(validator.validate(request)).isNotEmpty();
        }

        Object requestWithNull = requestType.getConstructors()[0].newInstance(java.util.Collections.singletonList(null));
        assertThat(validator.validate(requestWithNull)).isNotEmpty();
    }

    private void assertRecord(String className) {
        assertThat(load(className).isRecord()).isTrue();
    }

    private void assertNotLoadable(String className) {
        assertThatThrownBy(() -> Class.forName(className)).isInstanceOf(ClassNotFoundException.class);
    }

    private Class<?> load(String className) {
        try {
            return Class.forName(className);
        } catch (ClassNotFoundException exception) {
            fail("Expected request DTO at " + className);
            throw new AssertionError(exception);
        }
    }
}
