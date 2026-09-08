package com.project.payment.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.project.common.dto.ApiResponse;
import com.project.payment.api.dto.request.PaymentRequest;
import com.project.payment.api.dto.response.PaymentInitiationResponse;
import com.project.payment.api.dto.response.PaymentResponse;
import com.project.payment.application.mapper.PaymentMapper;
import com.project.payment.application.validator.PaymentOrderValidator;
import com.project.payment.application.validator.PaymentTransitionValidator;
import com.project.payment.client.OrderClient;
import com.project.payment.client.dto.OrderSummary;
import com.project.payment.exception.PaymentException;
import com.project.payment.kafka.PaymentEventPublisher;
import com.project.payment.model.Payment;
import com.project.payment.service.impl.PaymentGateway;
import com.project.payment.service.impl.PaymentServiceImpl;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PaymentInitiationContractTest {

    @Test
    void paymentResponseCannotExposeAClientSecret() throws Exception {
        assertThat(PaymentResponse.class.getDeclaredFields())
                .noneMatch(field -> field.getName().equals("clientSecret"));

        Payment payment = Payment.builder().id("payment-1").status(com.project.payment.model.PaymentStatus.PENDING)
                .amount(new BigDecimal("10.00")).build();
        String json = new ObjectMapper().writeValueAsString(new PaymentMapper().toResponse(payment));
        assertThat(json).doesNotContain("clientSecret");
    }

    @Test
    void initiationResponseIsTheOnlyResponseContainingTheEphemeralSecret() {
        assertThat(PaymentInitiationResponse.class.getRecordComponents())
                .extracting(component -> component.getName())
                .containsExactly("payment", "clientSecret");
    }

    @Test
    void genericPaymentEndpointsReturnOnlySecretFreePaymentResponses() {
        Stream.of("getMyPayments", "getPayment", "getByReference", "getByOrderId",
                        "processPayment", "refundPayment")
                .map(name -> java.util.Arrays.stream(PaymentController.class.getDeclaredMethods())
                        .filter(method -> method.getName().equals(name))
                        .findFirst()
                        .orElseThrow())
                .forEach(method -> assertThat(method.getGenericReturnType().getTypeName())
                        .contains("PaymentResponse")
                        .doesNotContain("PaymentInitiationResponse", "clientSecret"));
    }

    @Test
    void anotherUsersOrderIsRejectedBeforeTheGatewayCanCreateASecret() {
        PaymentGateway gateway = Mockito.mock(PaymentGateway.class);
        OrderClient orderClient = Mockito.mock(OrderClient.class);
        var repository = Mockito.mock(com.project.payment.repository.PaymentRepository.class);
        when(repository.findByOrderId("order-1")).thenReturn(Optional.empty());
        UUID ownerId = UUID.randomUUID();
        when(orderClient.getOrder("order-1")).thenReturn(ApiResponse.success(new OrderSummary(
                "order-1", "ORD-1", ownerId, "PENDING", new BigDecimal("10.00"), "USD")));
        PaymentServiceImpl service = new PaymentServiceImpl(
                repository,
                Mockito.mock(PaymentEventPublisher.class),
                Mockito.mock(StringRedisTemplate.class),
                gateway,
                new PaymentMapper(),
                orderClient,
                new PaymentOrderValidator(),
                new PaymentTransitionValidator());

        Throwable forbidden = catchThrowable(() -> service.createPayment(
                new PaymentRequest("order-1", null, "CARD", null, null, null),
                UUID.randomUUID(), "other@example.com", null));
        assertThat(forbidden)
                .isInstanceOf(com.project.common.exception.ForbiddenOperationException.class)
                .extracting("status")
                .isEqualTo(org.springframework.http.HttpStatus.FORBIDDEN);
        verify(repository, never()).findByOrderId("order-1");
        verify(gateway, never()).createIntent(any());
    }
}
