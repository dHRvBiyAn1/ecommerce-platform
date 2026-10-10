package com.project.coupon.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.project.common.constant.ErrorCode;
import com.project.common.constant.Permissions;
import com.project.common.constant.ServiceScopes;
import com.project.common.exception.GlobalExceptionHandler;
import com.project.common.security.ResourceServerSecurityConfig;
import com.project.coupon.config.SecurityConfig;
import com.project.coupon.generated.model.CouponReservationResponse;
import com.project.coupon.generated.model.CouponResponse;
import com.project.coupon.generated.model.ValidateCouponResponse;
import com.project.coupon.service.CouponService;
import com.project.coupon.validation.CouponRequestValidator;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

@WebMvcTest(
    value = CouponController.class,
    properties = {
      "spring.cloud.config.enabled=false",
      "spring.config.import=optional:file:/dev/null",
      "common.security.resource-server.enabled=false"
    })
@AutoConfigureMockMvc
@Import({
  SecurityConfig.class,
  ResourceServerSecurityConfig.class,
  GlobalExceptionHandler.class,
  CouponRequestValidator.class,
  com.project.coupon.generated.mapper.CouponApiMapperImpl.class
})
class CouponControllerSecurityTest {

  private static final UUID CUSTOMER_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");

  @Autowired private MockMvc mockMvc;

  @Autowired private ApplicationContext applicationContext;

  @MockBean private CouponService couponService;

  @MockBean private JwtDecoder jwtDecoder;

  @Autowired private org.springframework.security.web.FilterChainProxy securityFilters;

  @Test
  void cookiesAndSessionsCannotSupplyBearerIdentity() throws Exception {
    var token =
        org.springframework.security.oauth2.jwt.Jwt.withTokenValue("owner-token")
            .header("alg", "none")
            .subject(CUSTOMER_ID.toString())
            .claim("token_type", "user")
            .claim("roles", java.util.List.of("ROLE_CUSTOMER"))
            .claim("permissions", java.util.List.of(Permissions.COUPONS_READ))
            .build();
    when(jwtDecoder.decode("owner-token")).thenReturn(token);
    when(couponService.validate(any()))
        .thenReturn(
            new ValidateCouponResponse()
                .valid(true)
                .code("SAVE10")
                .discountAmount(BigDecimal.ONE)
                .description("save"));
    var session = new org.springframework.mock.web.MockHttpSession();
    session.setAttribute(
        org.springframework.security.web.context.HttpSessionSecurityContextRepository
            .SPRING_SECURITY_CONTEXT_KEY,
        new org.springframework.security.core.context.SecurityContextImpl(
            new com.project.common.security.JwtAuthenticationConverter().convert(token)));

    mockMvc
        .perform(
            post("/api/v1/coupons/validate")
                .contentType(MediaType.APPLICATION_JSON)
                .content(validationJson())
                .cookie(new jakarta.servlet.http.Cookie("access_token", "owner-token")))
        .andExpect(status().isUnauthorized());
    mockMvc
        .perform(
            post("/api/v1/coupons/validate")
                .contentType(MediaType.APPLICATION_JSON)
                .content(validationJson())
                .session(session))
        .andExpect(status().isUnauthorized());
    mockMvc
        .perform(
            post("/api/v1/coupons/validate")
                .contentType(MediaType.APPLICATION_JSON)
                .content(validationJson())
                .header("Authorization", "Bearer owner-token"))
        .andExpect(status().isOk());
    org.assertj.core.api.Assertions.assertThat(
            securityFilters.getFilterChains().stream()
                .flatMap(chain -> chain.getFilters().stream()))
        .noneMatch(
            filter ->
                filter
                    instanceof
                    org.springframework.security.web.authentication.www.BasicAuthenticationFilter);
  }

  @Test
  void unauthenticatedCouponEndpointsRequireBearerAuth() throws Exception {
    mockMvc
        .perform(
            post("/api/v1/coupons/validate")
                .contentType(MediaType.APPLICATION_JSON)
                .content(validationJson()))
        .andExpect(status().isUnauthorized())
        .andExpect(
            header().string("WWW-Authenticate", org.hamcrest.Matchers.containsString("Bearer")))
        .andExpect(jsonPath("$.status").value(401))
        .andExpect(jsonPath("$.code").value(ErrorCode.UNAUTHENTICATED.value()))
        .andExpect(jsonPath("$.path").value("/api/v1/coupons/validate"))
        .andExpect(jsonPath("$.traceId").isNotEmpty());

    org.assertj.core.api.Assertions.assertThat(
            applicationContext.getBeansOfType(
                org.springframework.security.web.SecurityFilterChain.class))
        .hasSize(1);
  }

  @Test
  void managementEndpointsRequireCouponAdminAuthority() throws Exception {
    when(couponService.create(any())).thenReturn(couponResponse());

    mockMvc
        .perform(
            post("/api/v1/coupons")
                .with(customerJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(couponJson()))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.status").value(403))
        .andExpect(jsonPath("$.code").value(ErrorCode.ACCESS_DENIED.value()))
        .andExpect(jsonPath("$.path").value("/api/v1/coupons"))
        .andExpect(jsonPath("$.traceId").isNotEmpty());

    mockMvc
        .perform(
            post("/api/v1/coupons")
                .with(
                    jwt()
                        .jwt(token -> token.subject(CUSTOMER_ID.toString()))
                        .authorities(new SimpleGrantedAuthority(Permissions.COUPONS_WRITE)))
                .contentType(MediaType.APPLICATION_JSON)
                .content(couponJson()))
        .andExpect(status().isForbidden());

    mockMvc
        .perform(
            post("/api/v1/coupons")
                .with(adminJwt(Permissions.COUPONS_WRITE))
                .contentType(MediaType.APPLICATION_JSON)
                .content(couponJson()))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.code").value("SAVE10"));
  }

  @Test
  void customerValidationRequiresAnAuthenticatedCustomerOrCouponReadService() throws Exception {
    when(couponService.validate(any()))
        .thenReturn(
            new ValidateCouponResponse()
                .valid(true)
                .code("SAVE10")
                .discountAmount(BigDecimal.ONE)
                .description("save"));

    mockMvc
        .perform(
            post("/api/v1/coupons/validate")
                .with(customerJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(validationJson()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.valid").value(true));

    mockMvc
        .perform(
            post("/api/v1/coupons/validate")
                .with(
                    jwt()
                        .jwt(token -> token.subject("22222222-2222-2222-2222-222222222222"))
                        .authorities(new SimpleGrantedAuthority("ROLE_CUSTOMER")))
                .contentType(MediaType.APPLICATION_JSON)
                .content(validationJson()))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value(ErrorCode.FORBIDDEN.value()));

    mockMvc
        .perform(
            post("/api/v1/coupons/validate")
                .with(serviceJwt(ServiceScopes.AUTHORITY_COUPONS_READ))
                .contentType(MediaType.APPLICATION_JSON)
                .content(validationJson()))
        .andExpect(status().isOk());
  }

  @Test
  void reservationLifecycleRequiresCouponWriteServiceAuthority() throws Exception {
    when(couponService.reserve(any())).thenReturn(reservation());
    when(couponService.commit(any())).thenReturn(reservation());
    when(couponService.release(any())).thenReturn(reservation());
    when(couponService.redeem(any()))
        .thenReturn(
            new ValidateCouponResponse()
                .valid(true)
                .code("SAVE10")
                .discountAmount(BigDecimal.ONE)
                .description("save"));

    mockMvc
        .perform(
            post("/api/v1/coupons/reserve")
                .with(customerJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(reservationJson()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("RESERVED"));

    mockMvc
        .perform(
            post("/api/v1/coupons/reserve")
                .with(customerJwt())
                .contentType(MediaType.APPLICATION_JSON)
                .content(otherReservationJson()))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value(ErrorCode.FORBIDDEN.value()));

    mockMvc
        .perform(
            post("/api/v1/coupons/reserve")
                .with(adminJwt(Permissions.COUPONS_WRITE))
                .contentType(MediaType.APPLICATION_JSON)
                .content(otherReservationJson()))
        .andExpect(status().isOk());

    mockMvc
        .perform(
            post("/api/v1/coupons/reserve")
                .with(serviceJwt(ServiceScopes.AUTHORITY_COUPONS_READ))
                .contentType(MediaType.APPLICATION_JSON)
                .content(reservationJson()))
        .andExpect(status().isForbidden());

    mockMvc
        .perform(
            post("/api/v1/coupons/reserve")
                .with(serviceJwt(ServiceScopes.AUTHORITY_COUPONS_WRITE))
                .contentType(MediaType.APPLICATION_JSON)
                .content(reservationJson()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("RESERVED"));

    mockMvc
        .perform(
            post("/api/v1/coupons/release")
                .with(serviceJwt(ServiceScopes.AUTHORITY_COUPONS_WRITE))
                .contentType(MediaType.APPLICATION_JSON)
                .content(transitionJson()))
        .andExpect(status().isOk());

    mockMvc
        .perform(
            post("/api/v1/coupons/commit")
                .with(serviceJwt(ServiceScopes.AUTHORITY_COUPONS_WRITE))
                .contentType(MediaType.APPLICATION_JSON)
                .content(transitionJson()))
        .andExpect(status().isOk());

    mockMvc
        .perform(
            post("/api/v1/coupons/redeem")
                .with(serviceJwt(ServiceScopes.AUTHORITY_COUPONS_WRITE))
                .contentType(MediaType.APPLICATION_JSON)
                .content(redeemJson()))
        .andExpect(status().isOk());
  }

  private static RequestPostProcessor customerJwt() {
    return jwt()
        .jwt(token -> token.subject(CUSTOMER_ID.toString()))
        .authorities(new SimpleGrantedAuthority("ROLE_CUSTOMER"));
  }

  private static RequestPostProcessor adminJwt(String permission) {
    return jwt()
        .jwt(token -> token.subject(CUSTOMER_ID.toString()))
        .authorities(
            new SimpleGrantedAuthority("ROLE_ADMIN"), new SimpleGrantedAuthority(permission));
  }

  private static RequestPostProcessor serviceJwt(String authority) {
    return jwt()
        .jwt(token -> token.subject("order-service").claim("token_type", "service"))
        .authorities(new SimpleGrantedAuthority(authority));
  }

  private static String couponJson() {
    return """
                {"code":"SAVE10","description":"save","discountType":"FIXED","discountValue":1.00,
                "maxDiscountAmount":5.00,"minOrderAmount":10.00,"currency":"USD",
                "validFrom":"2026-01-01T00:00:00","validUntil":"2030-01-01T00:00:00",
                "usageLimit":100,"perUserLimit":1,"active":true}
                """;
  }

  private static String validationJson() {
    return """
                {"code":"SAVE10","userId":"11111111-1111-1111-1111-111111111111","subtotal":10.00,"currency":"USD"}
                """;
  }

  private static String reservationJson() {
    return """
                {"code":"SAVE10","userId":"11111111-1111-1111-1111-111111111111","orderId":"order-1","subtotal":10.00,"currency":"USD"}
                """;
  }

  private static String otherReservationJson() {
    return reservationJson()
        .replace(CUSTOMER_ID.toString(), "22222222-2222-2222-2222-222222222222");
  }

  private static String transitionJson() {
    return """
                {"code":"SAVE10","userId":"11111111-1111-1111-1111-111111111111","orderId":"order-1"}
                """;
  }

  private static String redeemJson() {
    return """
                {"code":"SAVE10","userId":"11111111-1111-1111-1111-111111111111","orderId":"order-1","discountAmount":1.00}
                """;
  }

  private static CouponResponse couponResponse() {
    return new CouponResponse()
        .id(UUID.randomUUID())
        .code("SAVE10")
        .description("save")
        .discountType(CouponResponse.DiscountTypeEnum.FIXED)
        .discountValue(BigDecimal.ONE)
        .maxDiscountAmount(new BigDecimal("5.00"))
        .minOrderAmount(BigDecimal.TEN)
        .currency("USD")
        .validFrom(LocalDateTime.parse("2026-01-01T00:00:00"))
        .validUntil(LocalDateTime.parse("2030-01-01T00:00:00"))
        .usageLimit(100)
        .usageCount(0)
        .reservedCount(0)
        .perUserLimit(1)
        .active(true)
        .createdAt(LocalDateTime.parse("2026-01-01T00:00:00"));
  }

  private static CouponReservationResponse reservation() {
    return new CouponReservationResponse()
        .reservationId(UUID.randomUUID())
        .code("SAVE10")
        .userId(CUSTOMER_ID)
        .orderId("order-1")
        .discountAmount(BigDecimal.ONE)
        .status(CouponReservationResponse.StatusEnum.RESERVED);
  }
}
