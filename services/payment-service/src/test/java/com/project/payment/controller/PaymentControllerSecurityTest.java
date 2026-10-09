package com.project.payment.controller;

import com.project.common.constant.Permissions;
import com.project.common.exception.GlobalExceptionHandler;
import com.project.payment.application.validator.PaymentAccessValidator;
import com.project.payment.generated.model.PaymentResponse;
import com.project.payment.model.PaymentStatus;
import com.project.payment.service.PaymentService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.data.mongodb.core.mapping.MongoMappingContext;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@WebMvcTest(value = PaymentController.class, properties = {
        "spring.cloud.config.enabled=false",
        "spring.config.import=optional:file:/dev/null"
})
@AutoConfigureMockMvc
@Import({com.project.payment.config.SecurityConfig.class, GlobalExceptionHandler.class,
        com.project.payment.application.mapper.PaymentMapper.class})
class PaymentControllerSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private PaymentService paymentService;

    @MockBean
    private PaymentAccessValidator accessValidator;

    @MockBean
    private JwtDecoder jwtDecoder;

    @MockBean(name = "mongoMappingContext")
    private MongoMappingContext mongoMappingContext;

    @Autowired
    private org.springframework.security.web.FilterChainProxy securityFilters;

    @Test
    void cookiesAndSessionsCannotSupplyBearerIdentity() throws Exception {
        var owner = UUID.fromString("11111111-1111-1111-1111-111111111111");
        var token = org.springframework.security.oauth2.jwt.Jwt.withTokenValue("owner-token")
                .header("alg", "none").subject(owner.toString()).claim("token_type", "user")
                .claim("roles", java.util.List.of("ROLE_CUSTOMER"))
                .claim("permissions", java.util.List.of(Permissions.PAYMENTS_READ)).build();
        org.mockito.Mockito.when(jwtDecoder.decode("owner-token")).thenReturn(token);
        org.mockito.Mockito.when(paymentService.getUserPayments(owner)).thenReturn(java.util.List.of());
        var session = new org.springframework.mock.web.MockHttpSession();
        session.setAttribute(org.springframework.security.web.context.HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY,
                new org.springframework.security.core.context.SecurityContextImpl(
                        new com.project.common.security.JwtAuthenticationConverter().convert(token)));

        mockMvc.perform(get("/api/v1/payments")
                        .cookie(new jakarta.servlet.http.Cookie("access_token", "owner-token")))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/payments").session(session)).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/payments").header("Authorization", "Bearer owner-token"))
                .andExpect(status().isOk());
        org.assertj.core.api.Assertions.assertThat(securityFilters.getFilterChains().stream()
                .flatMap(chain -> chain.getFilters().stream()))
                .noneMatch(filter -> filter instanceof org.springframework.security.web.authentication.www.BasicAuthenticationFilter);
    }

    @Test
    void onlyPostWebhookEndpointsAllowAnonymousRequests() throws Exception {
        mockMvc.perform(post("/api/v1/payments/webhook").content("{}"))
                .andExpect(status().isServiceUnavailable());
        mockMvc.perform(post("/api/v1/payments/webhook/stripe").content("{}"))
                .andExpect(status().isServiceUnavailable());
        mockMvc.perform(post("/api/v1/payments/webhook/unrecognized").content("{}"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/v1/payments").contentType(MediaType.APPLICATION_JSON).content(paymentRequest()))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/v3/api-docs")).andExpect(status().isUnauthorized());
        // The MVC slice omits actuator handlers; 404 proves SecurityConfig allowed each route through.
        mockMvc.perform(get("/actuator/health")).andExpect(status().isNotFound());
        mockMvc.perform(get("/actuator/health/liveness")).andExpect(status().isNotFound());
        mockMvc.perform(get("/actuator/prometheus")).andExpect(status().isNotFound());
    }

    @Test
    void customerCanInitiateButCannotProcessWhileProcessorCanProcess() throws Exception {
        when(paymentService.createPayment(any(), any(), any(), any())).thenReturn(
                new com.project.payment.generated.model.PaymentInitiationResponse(
                        paymentResponse(UUID.randomUUID(), PaymentStatus.PENDING), "client-secret"));
        when(paymentService.processPayment("payment-1")).thenReturn(
                paymentResponse(UUID.randomUUID(), PaymentStatus.COMPLETED));
        mockMvc.perform(post("/api/v1/payments").with(jwt().jwt(token -> token.subject(UUID.randomUUID().toString()))
                        .authorities(new SimpleGrantedAuthority("ROLE_CUSTOMER")))
                        .contentType(MediaType.APPLICATION_JSON).content(paymentRequest()))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/v1/payments").with(jwt().jwt(token -> token.subject(UUID.randomUUID().toString()))
                        .authorities(new SimpleGrantedAuthority(Permissions.PAYMENTS_PROCESS)))
                        .contentType(MediaType.APPLICATION_JSON).content(paymentRequest()))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/v1/payments/payment-1/process").with(jwt().authorities(
                        new SimpleGrantedAuthority("ROLE_CUSTOMER"))))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/v1/payments/payment-1/process").with(jwt().authorities(
                        new SimpleGrantedAuthority(Permissions.PAYMENTS_PROCESS))))
                .andExpect(status().isOk());
    }

    @Test
    void processPermissionDoesNotRequirePaymentOwnershipIdentity() throws Exception {
        UUID otherOwner = UUID.fromString("22222222-2222-2222-2222-222222222222");
        when(paymentService.processPayment("payment-1")).thenReturn(paymentResponse(otherOwner, PaymentStatus.COMPLETED));

        mockMvc.perform(post("/api/v1/payments/payment-1/process").with(jwt()
                        .jwt(token -> token.subject("payment-service").claim("token_type", "service"))
                        .authorities(new SimpleGrantedAuthority(Permissions.PAYMENTS_PROCESS))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value("payment-1"));
        mockMvc.perform(post("/api/v1/payments/payment-1/process").with(jwt()
                        .jwt(token -> token.subject(otherOwner.toString()).claim("token_type", "user"))
                        .authorities(new SimpleGrantedAuthority(Permissions.PAYMENTS_PROCESS))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value("payment-1"));

        verify(paymentService, times(2)).processPayment("payment-1");
        verify(accessValidator, never()).validateAccess(any(), any(), org.mockito.ArgumentMatchers.anyBoolean());
    }

    private PaymentResponse paymentResponse(UUID ownerId, PaymentStatus status) {
        return new PaymentResponse().id("payment-1").paymentReference("PAY-1").orderId("order-1").orderNumber("ORD-1").userId(ownerId).userEmail(null).status(status == null ? null : com.project.payment.generated.model.PaymentStatus.valueOf(status.name())).paymentMethod("CARD").amount(java.math.BigDecimal.TEN).refundedAmount(java.math.BigDecimal.ZERO).currency("USD").transactionId(null).gatewayResponse(null).failureReason(null).retryCount(0).description(null).createdAt(null).updatedAt(null).completedAt(null);
    }

    private String paymentRequest() {
        return """
                {"orderId":"order-1","paymentMethod":"CARD","amount":10.00,"currency":"USD"}
                """;
    }
}
