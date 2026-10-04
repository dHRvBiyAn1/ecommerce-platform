package com.project.authservice.dto;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.project.authservice.dto.request.seller.RejectApplicationRequest;
import com.project.authservice.dto.request.seller.SellerApplicationRequest;
import com.project.authservice.dto.response.seller.SellerApplicationResponse;
import com.project.authservice.entity.SellerApplicationStatus;
import jakarta.validation.Validation;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.lang.reflect.Modifier;
import java.time.LocalDateTime;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SellerApplicationDtoContractTest {

    private final ObjectMapper objectMapper = new ObjectMapper()
            .findAndRegisterModules()
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void sellerApplicationDtosAreRecordsInNormalizedPackagesAndLegacyClassesAreAbsent() throws Exception {
        assertThat(SellerApplicationRequest.class.getPackageName()).isEqualTo("com.project.authservice.dto.request.seller");
        assertThat(RejectApplicationRequest.class.getPackageName()).isEqualTo("com.project.authservice.dto.request.seller");
        assertThat(SellerApplicationResponse.class.getPackageName()).isEqualTo("com.project.authservice.dto.response.seller");
        assertThat(SellerApplicationRequest.class.isRecord()).isTrue();
        assertThat(RejectApplicationRequest.class.isRecord()).isTrue();
        assertThat(SellerApplicationResponse.class.isRecord()).isTrue();
        assertThat(Modifier.isFinal(SellerApplicationRequest.class.getModifiers())).isTrue();
        assertThatThrownBy(() -> Class.forName("com.project.authservice.dto.seller.SellerApplicationRequest", false,
                getClass().getClassLoader())).isInstanceOf(ClassNotFoundException.class);
        assertThatThrownBy(() -> Class.forName("com.project.authservice.dto.seller.RejectApplicationRequest", false,
                getClass().getClassLoader())).isInstanceOf(ClassNotFoundException.class);
        assertThatThrownBy(() -> Class.forName("com.project.authservice.dto.seller.SellerApplicationResponse", false,
                getClass().getClassLoader())).isInstanceOf(ClassNotFoundException.class);
    }

    @Test
    void sellerApplicationRecordsPreserveRequestAndResponseJsonFields() throws Exception {
        UUID id = UUID.fromString("11111111-1111-1111-1111-111111111111");
        UUID userId = UUID.fromString("22222222-2222-2222-2222-222222222222");
        UUID reviewerId = UUID.fromString("33333333-3333-3333-3333-333333333333");
        String requestJson = "{\"businessName\":\"Acme\",\"gstin\":\"27ABCDE1234F1Z5\",\"contactPhone\":\"+91 9876543210\",\"pickupAddress\":{\"fullName\":\"Asha\",\"phone\":\"+91 9876543210\",\"street\":\"1 Main St\",\"city\":\"Pune\",\"state\":\"MH\",\"zipCode\":\"411001\",\"country\":\"IN\"},\"bankAccountLast4\":\"1234\",\"notes\":\"Call first\"}";
        SellerApplicationRequest request = objectMapper.readValue(requestJson, SellerApplicationRequest.class);
        SellerApplicationResponse response = new SellerApplicationResponse(id, userId, "seller@example.com", "Seller",
                SellerApplicationStatus.REJECTED, request.businessName(), request.gstin(), request.contactPhone(),
                request.pickupAddress(), request.bankAccountLast4(), request.notes(), "Missing documents",
                LocalDateTime.of(2025, 1, 2, 3, 4, 5), LocalDateTime.of(2025, 1, 3, 4, 5, 6), reviewerId);

        assertThat(objectMapper.writeValueAsString(request)).isEqualTo(requestJson);
        assertThat(objectMapper.writeValueAsString(response)).isEqualTo("{\"id\":\"11111111-1111-1111-1111-111111111111\",\"userId\":\"22222222-2222-2222-2222-222222222222\",\"userEmail\":\"seller@example.com\",\"userDisplayName\":\"Seller\",\"status\":\"REJECTED\",\"businessName\":\"Acme\",\"gstin\":\"27ABCDE1234F1Z5\",\"contactPhone\":\"+91 9876543210\",\"pickupAddress\":{\"fullName\":\"Asha\",\"phone\":\"+91 9876543210\",\"street\":\"1 Main St\",\"city\":\"Pune\",\"state\":\"MH\",\"zipCode\":\"411001\",\"country\":\"IN\"},\"bankAccountLast4\":\"1234\",\"notes\":\"Call first\",\"rejectionReason\":\"Missing documents\",\"submittedAt\":\"2025-01-02T03:04:05\",\"reviewedAt\":\"2025-01-03T04:05:06\",\"reviewedBy\":\"33333333-3333-3333-3333-333333333333\"}");
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("invalidSellerApplicationRequests")
    void sellerApplicationRequestReportsExpectedValidation(String scenario, SellerApplicationRequest request,
            String propertyPath, String message) {
        assertViolation(validator.validate(request), propertyPath, message);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("invalidRejectApplicationRequests")
    void rejectApplicationRequestReportsExpectedValidation(String scenario, RejectApplicationRequest request,
            String propertyPath, String message) {
        assertViolation(validator.validate(request), propertyPath, message);
    }

    private static Stream<Arguments> invalidSellerApplicationRequests() {
        return Stream.of(
                invalidApplication("blank business name", "", null, null, null, null,
                        "businessName", "must not be blank"),
                invalidApplication("long business name", "x".repeat(181), null, null, null, null,
                        "businessName", "size must be between 0 and 180"),
                invalidApplication("invalid GSTIN", null, "invalid", null, null, null,
                        "gstin", "GSTIN must be 15 alphanumeric characters"),
                invalidApplication("blank contact phone", null, null, "", null, null,
                        "contactPhone", "must not be blank"),
                invalidApplication("malformed contact phone", null, null, "abc", null, null,
                        "contactPhone", "Invalid phone number"),
                invalidApplication("missing pickup address", null, null, null, null, null,
                        "pickupAddress", "must not be null"),
                invalidApplication("nested pickup full name", null, null, null,
                        new AddressDto("", "+91 9876543210", "1 Main St", "Pune", "MH", "411001", "IN"), null,
                        "pickupAddress.fullName", "Full name is required"),
                invalidApplication("invalid bank account suffix", null, null, null, null, "12",
                        "bankAccountLast4", "Last-4 of bank account must be 4 digits"),
                invalidApplication("long notes", null, null, null, null, null, "notes",
                        "size must be between 0 and 4000"));
    }

    private static Stream<Arguments> invalidRejectApplicationRequests() {
        return Stream.of(
                Arguments.of("blank rejection reason", new RejectApplicationRequest(" "), "reason", "must not be blank"),
                Arguments.of("long rejection reason", new RejectApplicationRequest("x".repeat(4001)), "reason",
                        "size must be between 0 and 4000"));
    }

    private static Arguments invalidApplication(String scenario, String businessName, String gstin, String contactPhone,
            AddressDto pickupAddress, String bankAccountLast4, String propertyPath, String message) {
        return Arguments.of(scenario, new SellerApplicationRequest(
                businessName == null ? "Acme" : businessName,
                gstin == null ? "27ABCDE1234F1Z5" : gstin,
                contactPhone == null ? "+91 9876543210" : contactPhone,
                propertyPath.equals("pickupAddress") ? null : pickupAddress == null ? validAddress() : pickupAddress,
                bankAccountLast4 == null ? "1234" : bankAccountLast4,
                propertyPath.equals("notes") ? "x".repeat(4001) : "Call first"), propertyPath, message);
    }

    private static AddressDto validAddress() {
        return new AddressDto("Asha", "+91 9876543210", "1 Main St", "Pune", "MH", "411001", "IN");
    }

    private static void assertViolation(Set<? extends ConstraintViolation<?>> violations, String propertyPath,
            String message) {
        assertThat(violations).anySatisfy(violation -> {
            assertThat(violation.getPropertyPath().toString()).isEqualTo(propertyPath);
            assertThat(violation.getMessage()).isEqualTo(message);
        });
    }
}
