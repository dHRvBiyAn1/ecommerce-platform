package com.project.authservice;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.project.authservice.generated.model.AddressDto;
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
    void generatedUserProfileModelPreservesWireFieldsAndNullDefaults() throws Exception {
        var profile = new com.project.authservice.generated.model.UserProfileDto();
        assertThat(profile.getRoles()).isEmpty();
        assertThat(profile.getPermissions()).isEmpty();
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
