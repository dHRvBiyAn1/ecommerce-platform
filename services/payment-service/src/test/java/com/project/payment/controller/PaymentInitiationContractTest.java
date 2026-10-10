package com.project.payment.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.project.common.web.Responses;
import com.project.payment.application.mapper.PaymentMapper;
import com.project.payment.application.validator.PaymentOrderValidator;
import com.project.payment.application.validator.PaymentTransitionValidator;
import com.project.payment.client.OrderClient;
import com.project.payment.generated.integration.order.model.OrderResponse;
import com.project.payment.generated.model.PaymentInitiationResponse;
import com.project.payment.generated.model.PaymentRequest;
import com.project.payment.generated.model.PaymentResponse;
import com.project.payment.model.Payment;
import com.project.payment.repository.PaymentOperationRepository;
import com.project.payment.repository.PaymentOutboxRepository;
import com.project.payment.repository.WebhookReceiptRepository;
import com.project.payment.service.impl.PaymentGateway;
import com.project.payment.service.impl.PaymentServiceImpl;
import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class PaymentInitiationContractTest {

  @Test
  void paymentResponseCannotExposeAClientSecret() throws Exception {
    assertThat(PaymentResponse.class.getDeclaredFields())
        .noneMatch(field -> field.getName().equals("clientSecret"));

    Payment payment =
        Payment.builder()
            .id("payment-1")
            .status(com.project.payment.model.PaymentStatus.PENDING)
            .amount(new BigDecimal("10.00"))
            .build();
    String json = new ObjectMapper().writeValueAsString(new PaymentMapper().toResponse(payment));
    assertThat(json).doesNotContain("clientSecret");
  }

  @Test
  void initiationResponseIsTheOnlyResponseContainingTheEphemeralSecret() {
    assertThat(PaymentInitiationResponse.class.getDeclaredFields())
        .filteredOn(field -> !java.lang.reflect.Modifier.isStatic(field.getModifiers()))
        .extracting(java.lang.reflect.Field::getName)
        .containsExactly("payment", "clientSecret");
  }

  @Test
  void generatedPaymentOperationsKeepInitiationAsTheOnlySecretBearingResponse() {
    assertThat(
            com.project.payment.generated.api.PaymentsApi.class.isAssignableFrom(
                PaymentController.class))
        .isTrue();
    String initiationType =
        java.util.Arrays.stream(com.project.payment.generated.api.PaymentsApi.class.getMethods())
            .filter(method -> method.getName().equals("initiatePayment"))
            .findFirst()
            .orElseThrow()
            .getGenericReturnType()
            .getTypeName();
    assertThat(initiationType).contains("ApiResponsePaymentInitiation");

    java.util.Map.of(
            "getPaymentById", "ApiResponsePayment",
            "getPaymentByOrderId", "ApiResponsePayment",
            "getPaymentByReference", "ApiResponsePayment",
            "listMyPayments", "ApiResponsePaymentList",
            "processPayment", "ApiResponsePayment",
            "refundPayment", "ApiResponsePayment")
        .forEach(
            (name, generatedBody) -> {
              var method =
                  java.util.Arrays.stream(
                          com.project.payment.generated.api.PaymentsApi.class.getMethods())
                      .filter(candidate -> candidate.getName().equals(name))
                      .findFirst()
                      .orElseThrow();
              String returnType = method.getGenericReturnType().getTypeName();
              assertThat(returnType)
                  .contains(generatedBody)
                  .doesNotContain("ApiResponsePaymentInitiation", "clientSecret");
            });
  }

  @Test
  void anotherUsersOrderIsRejectedBeforeTheGatewayCanCreateASecret() {
    PaymentGateway gateway = Mockito.mock(PaymentGateway.class);
    OrderClient orderClient = Mockito.mock(OrderClient.class);
    var repository = Mockito.mock(com.project.payment.repository.PaymentRepository.class);
    when(repository.findByOrderId("order-1")).thenReturn(Optional.empty());
    UUID ownerId = UUID.randomUUID();
    when(orderClient.getOrder("order-1"))
        .thenReturn(
            Responses.success(
                new OrderResponse()
                    .id("order-1")
                    .orderNumber("ORD-1")
                    .userId(ownerId)
                    .status(
                        com.project.payment.generated.integration.order.model.OrderResponse
                            .StatusEnum.fromValue("PENDING"))
                    .totalAmount(new BigDecimal("10.00"))
                    .currency("USD")));
    PaymentServiceImpl service =
        new PaymentServiceImpl(
            repository,
            Mockito.mock(PaymentOperationRepository.class),
            Mockito.mock(WebhookReceiptRepository.class),
            Mockito.mock(PaymentOutboxRepository.class),
            gateway,
            new PaymentMapper(),
            orderClient,
            new PaymentOrderValidator(),
            new PaymentTransitionValidator(),
            new ObjectMapper().findAndRegisterModules());

    Throwable forbidden =
        catchThrowable(
            () ->
                service.createPayment(
                    new PaymentRequest("order-1", null, "CARD", null, null, null),
                    UUID.randomUUID(),
                    "other@example.com",
                    null));
    assertThat(forbidden)
        .isInstanceOf(com.project.common.exception.ForbiddenOperationException.class)
        .extracting("status")
        .isEqualTo(org.springframework.http.HttpStatus.FORBIDDEN);
    verify(repository, never()).findByOrderId("order-1");
    verify(gateway, never()).createIntent(any());
  }
}
