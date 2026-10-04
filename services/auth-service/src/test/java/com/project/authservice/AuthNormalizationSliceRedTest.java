package com.project.authservice;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.project.authservice.dto.AddressDto;
import com.project.authservice.mapper.UserMapper;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AuthNormalizationSliceRedTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();
    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @Test
    void addressDtoUsesDatabaseAlignedLimits() {
        AddressDto address = new AddressDto(
                "Customer One", "+1 555 0100", "s".repeat(201), "c".repeat(81),
                "s".repeat(81), "12345", "c".repeat(81));

        assertThat(validator.validate(address)).extracting("propertyPath").extracting(Object::toString)
                .contains("street", "city", "state", "country");
    }

    @Test
    void addressDtoAcceptsDatabaseMaximumFullName() {
        AddressDto address = new AddressDto(
                "n".repeat(120), "+1 555 0100", "1 Main Street", "Pune", "MH", "411001", "IN");

        assertThat(validator.validate(address)).isEmpty();
    }

    @Test
    void addressDtoRejectsFullNameBeyondDatabaseMaximum() {
        AddressDto address = new AddressDto(
                "n".repeat(121), "+1 555 0100", "1 Main Street", "Pune", "MH", "411001", "IN");

        assertThat(validator.validate(address)).extracting("propertyPath").extracting(Object::toString)
                .contains("fullName");
    }

    @Test
    void userProfileDtoCopiesCollectionsAndNormalizesNullCollections() throws Exception {
        Class<?> type = Class.forName("com.project.authservice.dto.response.UserProfileDto");
        Set<String> roles = new HashSet<>(Set.of("ROLE_CUSTOMER"));
        Set<String> permissions = new HashSet<>(Set.of("users:read"));
        Object profile = type.getDeclaredConstructors()[0].newInstance(
                null, null, null, null, null, false, null, roles, permissions, null, null, false);

        roles.add("ROLE_ADMIN");
        permissions.add("users:write");
        assertThat(type.getMethod("roles").invoke(profile)).isEqualTo(Set.of("ROLE_CUSTOMER"));
        assertThat(type.getMethod("permissions").invoke(profile)).isEqualTo(Set.of("users:read"));
        assertThatThrownBy(() -> ((Set<String>) type.getMethod("roles").invoke(profile)).add("ROLE_ADMIN"))
                .isInstanceOf(UnsupportedOperationException.class);

        Object emptyProfile = type.getDeclaredConstructors()[0].newInstance(
                null, null, null, null, null, false, null, null, null, null, null, false);
        assertThat(type.getMethod("roles").invoke(emptyProfile)).isEqualTo(Set.of());
        assertThat(type.getMethod("permissions").invoke(emptyProfile)).isEqualTo(Set.of());
    }

    @Test
    void userProfileDtoIsAnImmutableResponseRecordWithExactWireContract() throws Exception {
        Class<?> type = Class.forName("com.project.authservice.dto.response.UserProfileDto");

        assertThat(type.isRecord()).isTrue();
        assertThat(type.getRecordComponents()).hasSize(12);
        assertThat(Arrays.stream(type.getRecordComponents()).map(component -> component.getName()))
                .containsExactly("id", "email", "displayName", "imageUrl", "phone", "active",
                        "createdAt", "roles", "permissions", "shippingAddress", "billingAddress", "hasPassword");

        Object profile = type.getDeclaredConstructors()[0].newInstance(
                null, null, null, null, null, false, null, null, null, null, null, true);
        assertThat(objectMapper.readTree(objectMapper.writeValueAsString(profile)).fieldNames())
                .toIterable().containsExactlyInAnyOrder("id", "email", "displayName", "imageUrl", "phone",
                        "active", "createdAt", "roles", "permissions", "shippingAddress", "billingAddress",
                        "hasPassword");
    }

    @Test
    void userMapperHasNoRepositoryState() {
        assertThat(Arrays.stream(UserMapper.class.getDeclaredFields())
                .filter(field -> !Modifier.isStatic(field.getModifiers()))
                .map(Field::getType)
                .map(Class::getName))
                .noneMatch(name -> name.endsWith("Repository"));
    }

    @Test
    void profileBoundaryAndPrincipalValidatorExist() throws Exception {
        assertThat(Class.forName("com.project.authservice.service.UserProfileService")).isNotNull();
        assertThat(Class.forName("com.project.authservice.security.AuthenticatedUserValidator")).isNotNull();
    }
}
