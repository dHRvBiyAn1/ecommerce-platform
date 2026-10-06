package com.project.coupon.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.project.common.exception.GlobalExceptionHandler;
import com.project.coupon.config.SecurityConfig;
import com.project.coupon.exception.CouponReservationConflictException;
import com.project.coupon.exception.CouponUnavailableException;
import com.project.coupon.generated.testclient.api.CouponsApi;
import com.project.coupon.generated.mapper.CouponApiMapperImpl;
import com.project.coupon.generated.testclient.invoker.ApiClient;
import com.project.coupon.generated.testclient.invoker.ApiException;
import com.project.coupon.service.CouponService;
import com.project.coupon.validation.CouponRequestValidator;
import jakarta.validation.Validator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestComponent;
import org.springframework.boot.web.servlet.context.ServletWebServerApplicationContext;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SpringBootTest(
        classes = CouponGeneratedClientHttpTest.TestApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "spring.cloud.config.enabled=false",
                "spring.config.import=optional:file:/dev/null",
                "spring.cloud.discovery.enabled=false",
                "eureka.client.enabled=false",
                "management.endpoints.enabled-by-default=false"
        })
class CouponGeneratedClientHttpTest {

    private static final UUID OWNER = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID OTHER = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final String CUSTOMER_TOKEN = "coupon-customer-token";
    private static final String READ_SERVICE_TOKEN = "coupon-read-service-token";
    private static final String WRITE_SERVICE_TOKEN = "coupon-write-service-token";
    private static final String ADMIN_TOKEN = "coupon-admin-token";

    @Autowired
    private ServletWebServerApplicationContext serverContext;

    @Autowired
    @Qualifier("requestMappingHandlerMapping")
    private RequestMappingHandlerMapping handlerMapping;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private Validator validator;

    @Autowired
    private com.project.coupon.mapper.CouponApiMapper apiMapper;

    @MockBean
    private CouponService couponService;

    @MockBean
    private JwtDecoder jwtDecoder;

    private CouponsApi customerApi;
    private CouponsApi readServiceApi;
    private CouponsApi writeServiceApi;
    private CouponsApi adminApi;

    @BeforeEach
    void setUpClientsAndJwtDecoder() {
        when(jwtDecoder.decode(anyString())).thenAnswer(call -> {
            String token = call.getArgument(0);
            Instant now = Instant.now();
            Jwt.Builder builder = Jwt.withTokenValue(token).header("alg", "none")
                    .issuedAt(now).expiresAt(now.plusSeconds(60));
            if (CUSTOMER_TOKEN.equals(token)) {
                builder.subject(OWNER.toString()).claim("token_type", "user")
                        .claim("roles", List.of("ROLE_CUSTOMER"));
            } else if (ADMIN_TOKEN.equals(token)) {
                builder.subject(OWNER.toString()).claim("token_type", "user")
                        .claim("roles", List.of("ROLE_ADMIN"))
                        .claim("permissions", List.of("coupons:read", "coupons:write"));
            } else {
                String scope = READ_SERVICE_TOKEN.equals(token) ? "coupons.read" : "coupons.write";
                builder.subject("order-service").claim("token_type", "service").claim("scope", scope);
            }
            return builder.build();
        });
        customerApi = client(CUSTOMER_TOKEN);
        readServiceApi = client(READ_SERVICE_TOKEN);
        writeServiceApi = client(WRITE_SERVICE_TOKEN);
        adminApi = client(ADMIN_TOKEN);
    }

    @Test
    void generatedClientDecodesValidationAndReservationDecimalsAndNullableFields() throws Exception {
        when(couponService.validate(any())).thenReturn(
                new com.project.coupon.dto.ValidateCouponResponse(true, "SAVE10", null,
                        new BigDecimal("7.50"), null));
        when(couponService.reserve(any())).thenReturn(reservation(OWNER, "order-1", "RESERVED"));

        var validation = customerApi.validateCoupon(new com.project.coupon.generated.testclient.model.ValidateCouponRequest()
                .code("SAVE10").userId(OWNER).subtotal(new BigDecimal("75.00")).currency("INR"));
        var reservation = writeServiceApi.reserveCoupon(
                new com.project.coupon.generated.testclient.model.CouponReservationRequest()
                        .code("SAVE10").userId(OWNER).orderId("order-1")
                        .subtotal(new BigDecimal("75.00")).currency("INR"));

        assertThat(validation.getValid()).isTrue();
        assertThat(validation.getDiscountAmount()).isEqualByComparingTo("7.50");
        assertThat(validation.getReason_JsonNullable().isPresent()).isTrue();
        assertThat(validation.getReason()).isNull();
        assertThat(validation.getDescription_JsonNullable().isPresent()).isTrue();
        assertThat(reservation.getDiscountAmount()).isEqualByComparingTo("7.50");
        assertThat(reservation.getStatus()).isEqualTo(
                com.project.coupon.generated.testclient.model.CouponReservationResponse.StatusEnum.RESERVED);
        verify(couponService).validate(new com.project.coupon.dto.ValidateCouponRequest(
                "SAVE10", OWNER, new BigDecimal("75.00"), "INR"));
    }

    @Test
    void blankReservationCurrencyRetainsTheLegacyNotBlankConstraintOverHttp() throws Exception {
        var request = new com.project.coupon.generated.testclient.model.CouponReservationRequest()
                .code("SAVE10").userId(OWNER).orderId("order-blank-currency")
                .subtotal(BigDecimal.TEN).currency("");
        assertThat(validator.validate(request))
                .anySatisfy(violation -> {
                    assertThat(violation.getPropertyPath().toString()).isEqualTo("currency");
                    assertThat(violation.getMessage()).isEqualTo("must not be blank");
                });

        ApiException invalidCurrency = catchThrowableOfType(
                () -> writeServiceApi.reserveCoupon(request), ApiException.class);

        assertThat(invalidCurrency.getCode()).isEqualTo(HttpStatus.BAD_REQUEST.value());
        var error = objectMapper.readTree(invalidCurrency.getResponseBody());
        assertThat(error.path("code").asText()).isEqualTo("VALIDATION_FAILED");
        assertThat(error.path("fieldErrors").has("currency")).isTrue();
    }

    @Test
    void reservationCommitAndReleaseTransitionsRetainTheirWireStatuses() throws Exception {
        when(couponService.reserve(any())).thenReturn(reservation(OWNER, "order-2", "RESERVED"));
        when(couponService.commit(any())).thenReturn(reservation(OWNER, "order-2", "COMMITTED"));
        when(couponService.release(any())).thenReturn(reservation(OWNER, "order-3", "RELEASED"));
        var reserve = new com.project.coupon.generated.testclient.model.CouponReservationRequest()
                .code("SAVE10").userId(OWNER).orderId("order-2")
                .subtotal(new BigDecimal("30.00")).currency("INR");
        var transition = new com.project.coupon.generated.testclient.model.CouponTransitionRequest()
                .code("SAVE10").userId(OWNER).orderId("order-2");

        assertThat(writeServiceApi.reserveCoupon(reserve).getStatus().name()).isEqualTo("RESERVED");
        assertThat(writeServiceApi.commitCouponReservation(transition).getStatus().name()).isEqualTo("COMMITTED");
        var release = new com.project.coupon.generated.testclient.model.CouponTransitionRequest()
                .code("SAVE10").userId(OWNER).orderId("order-3");
        assertThat(writeServiceApi.releaseCouponReservation(release).getStatus().name()).isEqualTo("RELEASED");
        verify(couponService).commit(new com.project.coupon.dto.CouponTransitionRequest("SAVE10", OWNER, "order-2"));
    }

    @Test
    void userCannotValidateForAnotherOwnerWhileScopedServiceCan() throws Exception {
        when(couponService.validate(any())).thenReturn(
                new com.project.coupon.dto.ValidateCouponResponse(true, "SAVE10", null,
                        new BigDecimal("1.00"), "save"));
        var otherRequest = new com.project.coupon.generated.testclient.model.ValidateCouponRequest()
                .code("SAVE10").userId(OTHER).subtotal(new BigDecimal("10.00"));

        ApiException denied = catchThrowableOfType(() -> customerApi.validateCoupon(otherRequest), ApiException.class);
        var serviceResult = readServiceApi.validateCoupon(otherRequest);

        assertThat(denied.getCode()).isEqualTo(HttpStatus.FORBIDDEN.value());
        assertThat(denied.getResponseBody()).contains("FORBIDDEN");
        assertThat(serviceResult.getValid()).isTrue();
        verify(couponService).validate(new com.project.coupon.dto.ValidateCouponRequest(
                "SAVE10", OTHER, new BigDecimal("10.00"), null));
    }

    @Test
    void anonymousCouponRequestKeepsTheCustomAuthenticationErrorBody() throws Exception {
        ApiException unauthorized = catchThrowableOfType(
                () -> new CouponsApi(new ApiClient().setHost("localhost")
                        .setPort(serverContext.getWebServer().getPort()).setBasePath("")
                ).validateCoupon(new com.project.coupon.generated.testclient.model.ValidateCouponRequest()
                        .code("SAVE10").userId(OWNER).subtotal(BigDecimal.TEN)), ApiException.class);

        assertThat(unauthorized.getCode()).isEqualTo(HttpStatus.UNAUTHORIZED.value());
        assertThat(unauthorized.getResponseHeaders().firstValue(HttpHeaders.WWW_AUTHENTICATE).orElse(null))
                .contains("Bearer");
        assertThat(unauthorized.getResponseBody()).contains("UNAUTHENTICATED", "Authentication required");
    }

    @Test
    void conflictUnavailableAndMissingServiceScopeKeepTheirActualHttpFailures() throws Exception {
        when(couponService.reserve(argThat(request -> request != null && "order-conflict".equals(request.orderId()))))
                .thenThrow(new CouponReservationConflictException("Reservation conflicts with existing data"));
        when(couponService.reserve(argThat(request -> request != null && "order-unavailable".equals(request.orderId()))))
                .thenThrow(new CouponUnavailableException("Coupon is unavailable"));
        var conflictRequest = reservationRequest("order-conflict");
        var unavailableRequest = reservationRequest("order-unavailable");

        ApiException conflict = catchThrowableOfType(
                () -> writeServiceApi.reserveCoupon(conflictRequest), ApiException.class);
        ApiException unavailable = catchThrowableOfType(
                () -> writeServiceApi.reserveCoupon(unavailableRequest), ApiException.class);
        ApiException missingScope = catchThrowableOfType(
                () -> readServiceApi.reserveCoupon(reservationRequest("order-no-scope")), ApiException.class);

        assertThat(conflict.getCode()).isEqualTo(HttpStatus.CONFLICT.value());
        assertThat(conflict.getResponseBody()).contains("DUPLICATE_RESOURCE");
        assertThat(unavailable.getCode()).isEqualTo(HttpStatus.BAD_REQUEST.value());
        assertThat(unavailable.getResponseBody()).contains("VALIDATION_FAILED", "Coupon is unavailable");
        assertThat(missingScope.getCode()).isEqualTo(HttpStatus.FORBIDDEN.value());
        assertThat(missingScope.getResponseBody()).contains("ACCESS_DENIED");
    }

    @Test
    void generatedDefinitionValidationRetainsPatternAndFutureChecks() throws Exception {
        var invalidPattern = new com.project.coupon.generated.testclient.model.CouponRequest()
                .code("lowercase").discountType(
                        com.project.coupon.generated.testclient.model.CouponRequest.DiscountTypeEnum.FIXED)
                .discountValue(BigDecimal.ONE)
                .validFrom(java.time.LocalDateTime.now().minusDays(1))
                .validUntil(java.time.LocalDateTime.now().plusDays(1));
        ApiException patternFailure = catchThrowableOfType(
                () -> adminApi.createCoupon(invalidPattern), ApiException.class);
        var invalidFuture = new com.project.coupon.generated.testclient.model.CouponRequest()
                .code("SAVE10").discountType(
                        com.project.coupon.generated.testclient.model.CouponRequest.DiscountTypeEnum.FIXED)
                .discountValue(BigDecimal.ONE)
                .validFrom(java.time.LocalDateTime.now().minusDays(1))
                .validUntil(java.time.LocalDateTime.now().minusDays(1));
        ApiException futureFailure = catchThrowableOfType(
                () -> adminApi.createCoupon(invalidFuture), ApiException.class);

        assertThat(patternFailure.getCode()).isEqualTo(HttpStatus.BAD_REQUEST.value());
        assertThat(patternFailure.getResponseBody()).contains(
                "Code must be uppercase alphanumeric, dashes, or underscores");
        assertThat(futureFailure.getCode()).isEqualTo(HttpStatus.BAD_REQUEST.value());
        assertThat(futureFailure.getResponseBody()).contains("must be a future date");
    }

    @Test
    void generatedInterfaceRegistersEveryCouponHttpOperationExactlyOnce() {
        Map<String, Set<RequestMethod>> expected = Map.of(
                "/api/v1/coupons", Set.of(RequestMethod.GET, RequestMethod.POST),
                "/api/v1/coupons/{id}", Set.of(RequestMethod.GET, RequestMethod.PUT, RequestMethod.DELETE),
                "/api/v1/coupons/validate", Set.of(RequestMethod.POST),
                "/api/v1/coupons/reserve", Set.of(RequestMethod.POST),
                "/api/v1/coupons/commit", Set.of(RequestMethod.POST),
                "/api/v1/coupons/release", Set.of(RequestMethod.POST),
                "/api/v1/coupons/redeem", Set.of(RequestMethod.POST));
        var registered = handlerMapping.getHandlerMethods().entrySet().stream()
                .filter(entry -> CouponController.class.isAssignableFrom(entry.getValue().getBeanType()))
                .flatMap(entry -> entry.getKey().getMethodsCondition().getMethods().stream()
                        .flatMap(method -> entry.getKey().getPathPatternsCondition().getPatternValues().stream()
                                .map(path -> Map.entry(path, method))))
                .collect(java.util.stream.Collectors.groupingBy(Map.Entry::getKey,
                        java.util.stream.Collectors.groupingBy(Map.Entry::getValue,
                                java.util.stream.Collectors.counting())));

        assertThat(registered).hasSize(expected.size());
        expected.forEach((path, methods) -> {
            assertThat(registered).containsKey(path);
            assertThat(registered.get(path).keySet()).containsExactlyInAnyOrderElementsOf(methods);
            methods.forEach(method -> assertThat(registered.get(path).get(method)).isEqualTo(1L));
        });
    }

    @Test
    void generatedCouponPageMatchesSpringPageJsonAndDecodesOverHttp() throws Exception {
        var response = new com.project.coupon.dto.CouponResponse(UUID.randomUUID(), "SAVE10", "save",
                com.project.coupon.entity.DiscountType.FIXED, new BigDecimal("7.50"), null,
                new BigDecimal("10.00"), "INR", java.time.LocalDateTime.parse("2026-10-01T00:00:00"),
                java.time.LocalDateTime.parse("2030-10-01T00:00:00"), 100, 0, 1, 1, true,
                java.time.LocalDateTime.parse("2026-10-01T00:00:00"), null);
        Page<com.project.coupon.dto.CouponResponse> sorted = new PageImpl<>(List.of(response),
                PageRequest.of(0, 10, Sort.by(Sort.Order.desc("createdAt"))), 1);
        Page<com.project.coupon.dto.CouponResponse> empty = Page.empty(
                PageRequest.of(2, 5, Sort.unsorted()));
        when(couponService.list(any())).thenReturn(sorted);

        var decoded = adminApi.listCoupons(0, 10, List.of("createdAt,desc"));

        assertThat(decoded.getTotalElements()).isEqualTo(1L);
        assertThat(decoded.getContent()).hasSize(1);
        assertThat(decoded.getContent().getFirst().getDiscountValue()).isEqualByComparingTo("7.50");
        assertThat(objectMapper.readTree(objectMapper.writeValueAsString(apiMapper.toApi(sorted))))
                .isEqualTo(objectMapper.readTree(objectMapper.writeValueAsString(sorted)));
        assertThat(objectMapper.readTree(objectMapper.writeValueAsString(apiMapper.toApi(empty))))
                .isEqualTo(objectMapper.readTree(objectMapper.writeValueAsString(empty)));
    }

    private CouponsApi client(String token) {
        ApiClient client = new ApiClient().setHost("localhost")
                .setPort(serverContext.getWebServer().getPort()).setBasePath("")
                .setRequestInterceptor(request -> request.header(HttpHeaders.AUTHORIZATION, "Bearer " + token));
        return new CouponsApi(client);
    }

    private static com.project.coupon.generated.testclient.model.CouponReservationRequest reservationRequest(
            String orderId) {
        return new com.project.coupon.generated.testclient.model.CouponReservationRequest()
                .code("SAVE10").userId(OWNER).orderId(orderId)
                .subtotal(new BigDecimal("10.00")).currency("INR");
    }

    private static com.project.coupon.dto.CouponReservationResponse reservation(
            UUID userId, String orderId, String status) {
        return new com.project.coupon.dto.CouponReservationResponse(UUID.randomUUID(), "SAVE10", userId,
                orderId, new BigDecimal("7.50"), com.project.coupon.entity.RedemptionStatus.valueOf(status));
    }

    @Configuration(proxyBeanMethods = false)
    @TestComponent
    @EnableAutoConfiguration(excludeName = {
            "org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration",
            "org.springframework.boot.autoconfigure.jdbc.DataSourceTransactionManagerAutoConfiguration",
            "org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration",
            "org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration",
            "org.springframework.cloud.netflix.eureka.EurekaClientAutoConfiguration"
    })
    @Import({CouponController.class, SecurityConfig.class, CouponRequestValidator.class,
            CouponApiMapperImpl.class, GlobalExceptionHandler.class})
    static class TestApplication { }
}
