package com.project.payment.controller;

import com.project.common.constant.Permissions;
import com.project.common.exception.GlobalExceptionHandler;
import com.project.payment.application.validator.PaymentAccessValidator;
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

@WebMvcTest(value = PaymentController.class, properties = {
        "spring.cloud.config.enabled=false",
        "spring.config.import=optional:file:/dev/null"
})
@AutoConfigureMockMvc
@Import({com.project.payment.config.SecurityConfig.class, GlobalExceptionHandler.class})
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

    private String paymentRequest() {
        return """
                {"orderId":"order-1","paymentMethod":"CARD","amount":10.00,"currency":"USD"}
                """;
    }
}
