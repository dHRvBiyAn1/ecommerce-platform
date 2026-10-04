package com.project.payment.controller;

import com.project.common.constant.ErrorCode;
import com.project.common.constant.Permissions;
import com.project.common.exception.GlobalExceptionHandler;
import com.project.common.exception.ForbiddenOperationException;
import com.project.payment.api.dto.response.PaymentInitiationResponse;
import com.project.payment.api.dto.response.PaymentResponse;
import com.project.payment.application.validator.PaymentAccessValidator;
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
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(value = PaymentController.class, properties = {
        "spring.cloud.config.enabled=false",
        "spring.config.import=optional:file:/dev/null"
})
@AutoConfigureMockMvc
@Import({
        com.project.payment.config.SecurityConfig.class,
        GlobalExceptionHandler.class
})
class PaymentControllerHttpContractTest {

    private static final UUID CUSTOMER_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");

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
    void foreignOrderCreateReturnsCommon403BeforeGatewayOrSecret() throws Exception {
        when(paymentService.createPayment(any(), any(), any(), any()))
                .thenThrow(new ForbiddenOperationException("You cannot create a payment for another user's order"));

        String body = mockMvc.perform(post("/api/v1/payments")
                        .with(customerJwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(paymentRequest()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.error").value("Forbidden"))
                .andExpect(jsonPath("$.code").value(ErrorCode.FORBIDDEN.value()))
                .andExpect(jsonPath("$.path").value("/api/v1/payments"))
                .andReturn().getResponse().getContentAsString();

        assertThat(body).doesNotContain("clientSecret");
        verify(paymentService).createPayment(any(), any(), any(), any());
    }

    @Test
    void authorizedCreateReturnsCreateOnlyInitiationResponse() throws Exception {
        when(paymentService.createPayment(any(), any(), any(), any()))
                .thenReturn(new PaymentInitiationResponse(paymentResponse(), "client-secret"));

        mockMvc.perform(post("/api/v1/payments")
                        .with(customerJwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(paymentRequest()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.clientSecret").value("client-secret"))
                .andExpect(jsonPath("$.data.payment.orderId").value("order-1"))
                .andExpect(jsonPath("$.data.payment.status").value("PENDING"));
    }

    @Test
    void genericGetAndListResponsesNeverSerializeClientSecret() throws Exception {
        PaymentResponse payment = paymentResponse();
        when(paymentService.getUserPayments(CUSTOMER_ID)).thenReturn(List.of(payment));
        when(paymentService.getPayment("payment-1")).thenReturn(payment);

        String listBody = mockMvc.perform(get("/api/v1/payments").with(customerJwt()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String itemBody = mockMvc.perform(get("/api/v1/payments/payment-1")
                        .with(customerJwt()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(listBody).doesNotContain("clientSecret");
        assertThat(itemBody).doesNotContain("clientSecret");
    }

    private org.springframework.test.web.servlet.request.RequestPostProcessor customerJwt() {
        return jwt().jwt(token -> token.subject(CUSTOMER_ID.toString())
                        .claim("email", "customer@example.com"))
                .authorities(
                        new SimpleGrantedAuthority("ROLE_CUSTOMER"),
                        new SimpleGrantedAuthority(Permissions.PAYMENTS_READ));
    }

    private String paymentRequest() {
        return """
                {
                  "orderId": "order-1",
                  "paymentMethod": "CARD",
                  "amount": 10.00,
                  "currency": "USD",
                  "description": "checkout"
                }
                """;
    }

    private PaymentResponse paymentResponse() {
        return new PaymentResponse("payment-1", "PAY-1", "order-1", "ORD-1", CUSTOMER_ID,
                "customer@example.com", PaymentStatus.PENDING, "CARD", new BigDecimal("10.00"),
                BigDecimal.ZERO, "USD", "intent-1", null, null, 0, "checkout", null, null, null);
    }
}
