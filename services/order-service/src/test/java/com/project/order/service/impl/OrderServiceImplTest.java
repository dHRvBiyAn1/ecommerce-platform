package com.project.order.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.project.common.exception.ForbiddenOperationException;
import com.project.order.application.mapper.OrderMapperImpl;
import com.project.order.application.validator.OrderRequestValidator;
import com.project.order.client.CouponClient;
import com.project.order.client.InventoryClient;
import com.project.order.client.ProductClient;
import com.project.order.constant.OrderPricing;
import com.project.order.exception.OrderValidationException;
import com.project.order.generated.integration.coupon.model.CouponReservationRequest;
import com.project.order.generated.integration.coupon.model.CouponTransitionRequest;
import com.project.order.generated.integration.coupon.model.ValidateCouponResponse;
import com.project.order.generated.integration.product.model.ProductResponse;
import com.project.order.generated.model.OrderItemRequest;
import com.project.order.generated.model.OrderRequest;
import com.project.order.generated.model.OrderResponse;
import com.project.order.generated.model.OrderStatusUpdateRequest;
import com.project.order.generated.model.ShippingAddressRequest;
import com.project.order.model.Order;
import com.project.order.model.OrderItem;
import com.project.order.model.OrderStatus;
import com.project.order.model.PaymentStatus;
import com.project.order.repository.OrderRepository;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;

@ExtendWith(MockitoExtension.class)
class OrderServiceImplTest {

  @Mock private OrderRepository orderRepository;

  @Mock private ProductClient productClient;

  @Mock private InventoryClient inventoryClient;

  @Mock private CouponClient couponClient;

  private OrderServiceImpl service;

  @BeforeEach
  void setUp() {
    service =
        new OrderServiceImpl(
            orderRepository,
            productClient,
            inventoryClient,
            couponClient,
            new OrderMapperImpl(),
            new OrderRequestValidator(),
            new ObjectMapper().findAndRegisterModules());
  }

  @Test
  void paymentCompletionCommitsReservedStockBeforeConfirmingTheOrder() {
    Order order = pendingOrder();
    when(orderRepository.findById("order-1")).thenReturn(Optional.of(order));
    when(orderRepository.save(any(Order.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    service.onPaymentResult("order-1", "payment-1", PaymentStatus.COMPLETED);

    verify(inventoryClient)
        .commit(
            "product-1",
            new com.project.order.generated.integration.inventory.model.StockReservationRequest(
                2, "order-1"));
    assertThat(order.getStatus()).isEqualTo(OrderStatus.CONFIRMED);
    assertThat(order.getPaymentStatus()).isEqualTo(PaymentStatus.COMPLETED);
  }

  @Test
  void duplicatePaymentCompletionDoesNotRepeatSideEffects() {
    Order order = pendingOrder();
    order.setStatus(OrderStatus.CONFIRMED);
    order.setPaymentStatus(PaymentStatus.COMPLETED);
    order.setPaymentId("payment-1");
    when(orderRepository.findById("order-1")).thenReturn(Optional.of(order));

    service.onPaymentResult("order-1", "payment-1", PaymentStatus.COMPLETED);

    verify(inventoryClient, never()).commit(any(), any());
    verify(couponClient, never()).redeem(any());
    verify(orderRepository, never()).save(any());
  }

  @Test
  void statusUpdateRejectsSkippingTheFulfillmentSequenceBeforeMutation() {
    Order order = pendingOrder();
    order.setStatus(OrderStatus.CONFIRMED);
    when(orderRepository.findById("order-1")).thenReturn(Optional.of(order));

    assertThatThrownBy(
            () ->
                service.updateOrderStatus(
                    "order-1",
                    new OrderStatusUpdateRequest(
                        OrderStatusUpdateRequest.StatusEnum.DELIVERED, "skip shipping")))
        .isInstanceOf(OrderValidationException.class)
        .hasMessageContaining("CONFIRMED to DELIVERED");

    assertThat(order.getStatus()).isEqualTo(OrderStatus.CONFIRMED);
    verify(orderRepository, never()).save(any());
  }

  @Test
  void paymentCompletionCommitsReservedCoupon() {
    Order order = pendingOrder();
    order.setCouponCode("SAVE10");
    order.setUserId(java.util.UUID.randomUUID());
    when(orderRepository.findById("order-1")).thenReturn(Optional.of(order));
    when(orderRepository.save(any(Order.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    service.onPaymentResult("order-1", "payment-1", PaymentStatus.COMPLETED);

    verify(couponClient)
        .commit(new CouponTransitionRequest("SAVE10", order.getUserId(), "order-1"));
    verify(couponClient, never()).redeem(any());
  }

  @Test
  void failedPaymentReleasesReservedCoupon() {
    Order order = pendingOrder();
    order.setCouponCode("SAVE10");
    order.setUserId(java.util.UUID.randomUUID());
    when(orderRepository.findById("order-1")).thenReturn(Optional.of(order));
    when(orderRepository.save(any(Order.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    service.onPaymentResult("order-1", "payment-1", PaymentStatus.FAILED);

    verify(couponClient)
        .release(new CouponTransitionRequest("SAVE10", order.getUserId(), "order-1"));
  }

  @Test
  void orderCreationReservesCouponForThePersistedOrder() {
    UUID userId = UUID.randomUUID();
    stubCheckoutDependencies(userId);

    service.createOrder(orderRequest(), userId, "customer@example.com", null);

    verify(couponClient)
        .reserve(
            new CouponReservationRequest(
                "SAVE10", userId, "order-1", new BigDecimal("100.00"), "INR"));
  }

  @Test
  void mapperMapsAnOrderWithoutExposingItsOutboxEvents() {
    Order order = pendingOrder();
    order.setOrderNumber("ORD-1");
    order.setUserId(UUID.randomUUID());
    order.setOutboxEvents(List.of());

    OrderResponse response = new OrderMapperImpl().toResponse(order);

    assertThat(response.getId()).isEqualTo(order.getId());
    assertThat(response.getOrderNumber()).isEqualTo(order.getOrderNumber());
    assertThat(response.getItems()).hasSize(1);
    assertThat(response.getItems().get(0).getProductId()).isEqualTo("product-1");
  }

  @Test
  void validatorRejectsInvalidAddressBeforePersistence() {
    OrderRequest request =
        new OrderRequest(
            List.of(new OrderItemRequest("product-1", 1)),
            null,
            new ShippingAddressRequest("", "phone", "street", "city", "state", "zip", "IN"),
            null,
            null,
            "CARD");

    assertThatThrownBy(() -> new OrderRequestValidator().validateCreate(request, UUID.randomUUID()))
        .isInstanceOf(OrderValidationException.class)
        .hasMessageContaining("Shipping address");
  }

  @Test
  void validatorRejectsInvalidItemQuantityBeforePersistence() {
    OrderRequest request =
        new OrderRequest(
            List.of(new OrderItemRequest("product-1", 0)), null, null, null, null, "CARD");

    assertThatThrownBy(() -> new OrderRequestValidator().validateCreate(request, UUID.randomUUID()))
        .isInstanceOf(OrderValidationException.class)
        .hasMessage("Quantity must be at least 1");
  }

  @Test
  void validatorRequiresAnAuthenticatedUserWhenRequestIsPresent() {
    assertThatThrownBy(() -> new OrderRequestValidator().validateCreate(new OrderRequest(), null))
        .isInstanceOf(OrderValidationException.class)
        .hasMessage("Order request and user are required");
  }

  @Test
  void orderUsesCentralPricingConstants() {
    assertThat(OrderPricing.DEFAULT_CURRENCY).isEqualTo("INR");
    assertThat(OrderPricing.TAX_RATE).isEqualByComparingTo("0.18");
    assertThat(OrderPricing.SHIPPING_COST).isEqualByComparingTo("49.00");
    assertThat(OrderPricing.FREE_SHIPPING_THRESHOLD).isEqualByComparingTo("499.00");
  }

  @Test
  void orderTimestampsAreMongoAudited() throws NoSuchFieldException {
    assertThat(Order.class.getDeclaredField("createdAt").isAnnotationPresent(CreatedDate.class))
        .isTrue();
    assertThat(
            Order.class.getDeclaredField("updatedAt").isAnnotationPresent(LastModifiedDate.class))
        .isTrue();
  }

  @Test
  void inventoryFailureRetainsCompletedCouponReservationForRetry() {
    UUID userId = UUID.randomUUID();
    AtomicReference<Order> persisted = stubCheckoutDependencies(userId);
    when(inventoryClient.reserve(
            "product-1",
            new com.project.order.generated.integration.inventory.model.StockReservationRequest(
                2, "order-1")))
        .thenThrow(new IllegalStateException("inventory unavailable"));
    assertThatThrownBy(
            () -> service.createOrder(orderRequest(), userId, "customer@example.com", null))
        .isInstanceOf(OrderValidationException.class)
        .hasMessage("Unable to reserve checkout resources");

    verify(couponClient, never()).release(any());
    assertThat(persisted.get().getSagaState().getStage())
        .isEqualTo(com.project.order.model.SagaState.Stage.RETRYABLE);
    assertThat(persisted.get().getSagaState().getOperations().get(0).getStatus())
        .isEqualTo(com.project.order.model.SagaState.OperationStatus.COMPLETED);
  }

  @Test
  void cancellationRejectsANonOwnerBeforeReleasingReservations() {
    Order order = pendingOrder();
    order.setUserId(UUID.randomUUID());
    when(orderRepository.findById("order-1")).thenReturn(Optional.of(order));

    assertThatThrownBy(() -> service.cancelOrder("order-1", UUID.randomUUID(), false))
        .isInstanceOf(ForbiddenOperationException.class)
        .hasMessage("Not your order");

    verify(inventoryClient, never()).release(any(), any());
    verify(couponClient, never()).release(any());
  }

  @Test
  void cancellationRejectsShippedOrdersBeforeReleasingReservations() {
    Order order = pendingOrder();
    order.setUserId(UUID.randomUUID());
    order.setStatus(OrderStatus.SHIPPED);
    when(orderRepository.findById("order-1")).thenReturn(Optional.of(order));

    assertThatThrownBy(() -> service.cancelOrder("order-1", order.getUserId(), false))
        .isInstanceOf(OrderValidationException.class)
        .hasMessage("Cannot cancel a shipped or delivered order");

    verify(inventoryClient, never()).release(any(), any());
  }

  @Test
  void cancellationPersistsAnEventAfterReleasingInventoryAndCoupon() {
    Order order = pendingOrder();
    order.setUserId(UUID.randomUUID());
    order.setCouponCode("SAVE10");
    order.setOutboxEvents(null);
    when(orderRepository.findById("order-1")).thenReturn(Optional.of(order));
    when(orderRepository.save(any(Order.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    OrderResponse response = service.cancelOrder("order-1", order.getUserId(), false);

    assertThat(response.getStatus())
        .isEqualTo(com.project.order.generated.model.OrderResponse.StatusEnum.CANCELLED);
    assertThat(order.getCancelledAt()).isNotNull();
    assertThat(order.getOutboxEvents())
        .singleElement()
        .satisfies(
            event -> {
              assertThat(event.getEventType()).isEqualTo("CANCELLED");
              assertThat(event.getPayload()).contains("\"type\":\"CANCELLED\"");
            });
    verify(inventoryClient)
        .release(
            "product-1",
            new com.project.order.generated.integration.inventory.model.StockReservationRequest(
                2, "order-1"));
    verify(couponClient)
        .release(new CouponTransitionRequest("SAVE10", order.getUserId(), "order-1"));
  }

  @Test
  void cancellationStillPersistsWhenCompensationCallsFail() {
    Order order = pendingOrder();
    order.setUserId(UUID.randomUUID());
    order.setCouponCode("SAVE10");
    when(orderRepository.findById("order-1")).thenReturn(Optional.of(order));
    when(orderRepository.save(any(Order.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));
    when(inventoryClient.release(any(), any()))
        .thenThrow(new IllegalStateException("inventory unavailable"));
    org.mockito.Mockito.doThrow(new IllegalStateException("coupon unavailable"))
        .when(couponClient)
        .release(any());

    service.cancelOrder("order-1", order.getUserId(), false);

    assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELLED);
    assertThat(order.getOutboxEvents())
        .singleElement()
        .extracting(event -> event.getEventType())
        .isEqualTo("CANCELLED");
  }

  @Test
  void shippingStatusAddsNotesTimestampAndStatusEvent() {
    Order order = pendingOrder();
    order.setStatus(OrderStatus.PROCESSING);
    when(orderRepository.findById("order-1")).thenReturn(Optional.of(order));
    when(orderRepository.save(any(Order.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    OrderResponse response =
        service.updateOrderStatus(
            "order-1",
            new OrderStatusUpdateRequest(
                OrderStatusUpdateRequest.StatusEnum.SHIPPED, "carrier collected"));

    assertThat(response.getStatus())
        .isEqualTo(com.project.order.generated.model.OrderResponse.StatusEnum.SHIPPED);
    assertThat(order.getNotes()).isEqualTo("carrier collected");
    assertThat(order.getShippedAt()).isNotNull();
    assertThat(order.getOutboxEvents())
        .singleElement()
        .extracting(event -> event.getEventType())
        .isEqualTo("STATUS_CHANGED");
  }

  @Test
  void processingStatusPersistsAndPublishesProcessingPayload() throws Exception {
    Order order = pendingOrder();
    order.setStatus(OrderStatus.CONFIRMED);
    when(orderRepository.findById("order-1")).thenReturn(Optional.of(order));
    when(orderRepository.save(any(Order.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    service.updateOrderStatus(
        "order-1",
        new OrderStatusUpdateRequest(OrderStatusUpdateRequest.StatusEnum.PROCESSING, null));

    assertThat(order.getStatus()).isEqualTo(OrderStatus.PROCESSING);
    assertThat(order.getOutboxEvents())
        .singleElement()
        .satisfies(
            event -> {
              assertThat(event.getEventType()).isEqualTo("STATUS_CHANGED");
              assertThat(new ObjectMapper().readTree(event.getPayload()).get("type").asText())
                  .isEqualTo("PROCESSING");
            });
    verify(orderRepository).save(order);
  }

  @Test
  void orderLookupsMapRepositoryResultsIntoResponses() {
    Order order = pendingOrder();
    order.setOrderNumber("ORD-1");
    when(orderRepository.findById("order-1")).thenReturn(Optional.of(order));
    when(orderRepository.findByOrderNumber("ORD-1")).thenReturn(Optional.of(order));

    assertThat(service.getOrder("order-1").getId()).isEqualTo("order-1");
    assertThat(service.getOrderByNumber("ORD-1").getOrderNumber()).isEqualTo("ORD-1");
  }

  @Test
  void deliveryStatusAddsDeliveryTimestampWithoutReplacingNotes() {
    Order order = pendingOrder();
    order.setStatus(OrderStatus.SHIPPED);
    order.setNotes("existing note");
    when(orderRepository.findById("order-1")).thenReturn(Optional.of(order));
    when(orderRepository.save(any(Order.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    service.updateOrderStatus(
        "order-1",
        new OrderStatusUpdateRequest(OrderStatusUpdateRequest.StatusEnum.DELIVERED, null));

    assertThat(order.getDeliveredAt()).isNotNull();
    assertThat(order.getNotes()).isEqualTo("existing note");
  }

  @Test
  void refundRecordsThePaymentOutcomeAndEmitsAStatusEvent() {
    Order order = pendingOrder();
    when(orderRepository.findById("order-1")).thenReturn(Optional.of(order));
    when(orderRepository.save(any(Order.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    service.onPaymentResult("order-1", "payment-1", PaymentStatus.REFUNDED);

    assertThat(order.getPaymentId()).isEqualTo("payment-1");
    assertThat(order.getPaymentStatus()).isEqualTo(PaymentStatus.REFUNDED);
    assertThat(order.getOutboxEvents())
        .singleElement()
        .extracting(event -> event.getEventType())
        .isEqualTo("STATUS_CHANGED");
  }

  @Test
  void pendingPaymentResultUpdatesOnlyThePaymentState() {
    Order order = pendingOrder();
    when(orderRepository.findById("order-1")).thenReturn(Optional.of(order));
    when(orderRepository.save(any(Order.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    service.onPaymentResult("order-1", "payment-1", PaymentStatus.PENDING);

    assertThat(order.getPaymentId()).isEqualTo("payment-1");
    assertThat(order.getPaymentStatus()).isEqualTo(PaymentStatus.PENDING);
    assertThat(order.getOutboxEvents()).isNull();
  }

  @Test
  void unknownPaymentResultLeavesDependenciesUntouched() {
    when(orderRepository.findById("missing")).thenReturn(Optional.empty());

    service.onPaymentResult("missing", "payment-1", PaymentStatus.COMPLETED);

    verify(orderRepository, never()).save(any());
    verify(inventoryClient, never()).commit(any(), any());
  }

  @Test
  void invalidCouponPreventsOrderPersistence() {
    UUID userId = UUID.randomUUID();
    ProductResponse product = new ProductResponse();
    product.setId("product-1");
    product.setSku("SKU-1");
    product.setName("Product");
    product.setPrice(new BigDecimal("50.00"));
    product.setActive(true);
    when(productClient.getProduct("product-1")).thenReturn(product);
    when(couponClient.validate(any()))
        .thenReturn(new ValidateCouponResponse().valid(false).reason("expired"));

    assertThatThrownBy(
            () -> service.createOrder(orderRequest(), userId, "customer@example.com", null))
        .isInstanceOf(OrderValidationException.class)
        .hasMessage("Invalid coupon: expired");

    verify(orderRepository, never()).insert(any(Order.class));
  }

  @Test
  void couponDiscountCannotMakeTheOrderTotalNegative() {
    UUID userId = UUID.randomUUID();
    stubCheckoutDependencies(userId);
    when(couponClient.validate(any()))
        .thenReturn(
            new ValidateCouponResponse().valid(true).discountAmount(new BigDecimal("1000.00")));

    OrderResponse response =
        service.createOrder(orderRequest(), userId, "customer@example.com", null);

    assertThat(response.getTotalAmount()).isEqualByComparingTo("0.00");
  }

  @Test
  void matchingInProgressPaymentSagaIsContinuedInsteadOfBeingReplaced() {
    Order order = pendingOrder();
    order.setSagaState(
        com.project.order.model.SagaState.builder()
            .workflow(com.project.order.model.SagaState.Workflow.PAYMENT_COMPLETION)
            .paymentId("payment-1")
            .stage(com.project.order.model.SagaState.Stage.RESERVING)
            .operations(List.of())
            .build());
    when(orderRepository.findById("order-1")).thenReturn(Optional.of(order));
    when(orderRepository.save(any(Order.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    service.onPaymentResult("order-1", "payment-1", PaymentStatus.COMPLETED);

    assertThat(order.getPaymentStatus()).isEqualTo(PaymentStatus.COMPLETED);
    assertThat(order.getStatus()).isEqualTo(OrderStatus.CONFIRMED);
    assertThat(order.getOutboxEvents())
        .singleElement()
        .extracting(event -> event.getEventType())
        .isEqualTo("PAYMENT_COMPLETED");
  }

  @Test
  void recoveryRepairsDuplicateLegacyOperationIdsBeforeReservingEachLine() {
    Order order = pendingOrder();
    order.setSagaState(
        com.project.order.model.SagaState.builder()
            .workflow(com.project.order.model.SagaState.Workflow.CHECKOUT)
            .stage(com.project.order.model.SagaState.Stage.RETRYABLE)
            .nextAttemptAt(java.time.LocalDateTime.now().minusSeconds(1))
            .operations(
                new java.util.ArrayList<>(List.of(operation("duplicate"), operation("duplicate"))))
            .build());
    when(orderRepository.findOrdersWithRecoverableSaga(any())).thenReturn(List.of(order));
    when(orderRepository.findById("order-1")).thenReturn(Optional.of(order));
    when(orderRepository.save(any(Order.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    service.recoverOrders();

    assertThat(order.getSagaState().getStage())
        .isEqualTo(com.project.order.model.SagaState.Stage.COMPLETED);
    assertThat(order.getSagaState().getOperations())
        .extracting(com.project.order.model.SagaState.Operation::getId)
        .doesNotHaveDuplicates();
    verify(inventoryClient, times(2))
        .reserve(
            "product-1",
            new com.project.order.generated.integration.inventory.model.StockReservationRequest(
                2, "order-1"));
  }

  @Test
  void idempotentReplayReturnsPersistedOrderWithoutRepeatingCheckoutCalls() {
    UUID userId = UUID.randomUUID();
    Order persisted = pendingOrder();
    when(orderRepository.findByUserIdAndIdempotencyKey(userId, "checkout-key"))
        .thenReturn(Optional.of(persisted));

    OrderResponse response =
        service.createOrder(orderRequest(), userId, "customer@example.com", "checkout-key");

    assertThat(response.getId()).isEqualTo("order-1");
    verify(productClient, never()).getProduct(any());
    verify(orderRepository, never()).insert(any(Order.class));
  }

  @Test
  void blankIdempotencyKeyDoesNotTriggerDurableReplayLookup() {
    UUID userId = UUID.randomUUID();
    stubCheckoutDependencies(userId);

    service.createOrder(orderRequest(), userId, "customer@example.com", "   ");

    verify(orderRepository, never()).findByUserIdAndIdempotencyKey(any(), any());
  }

  @Test
  void inactiveProductIsRejectedBeforeOrderIsPersisted() {
    ProductResponse product = new ProductResponse();
    product.setId("product-1");
    product.setActive(false);
    when(productClient.getProduct("product-1")).thenReturn(product);

    assertThatThrownBy(
            () ->
                service.createOrder(
                    orderRequest(), UUID.randomUUID(), "customer@example.com", null))
        .isInstanceOf(OrderValidationException.class)
        .hasMessage("Product not available: product-1");

    verify(orderRepository, never()).insert(any(Order.class));
  }

  @Test
  void unavailableProductIsReportedAsValidationFailure() {
    when(productClient.getProduct("product-1"))
        .thenThrow(new IllegalStateException("catalog unavailable"));

    assertThatThrownBy(
            () ->
                service.createOrder(
                    orderRequest(), UUID.randomUUID(), "customer@example.com", null))
        .isInstanceOf(OrderValidationException.class)
        .hasMessage("Product not found: product-1");

    verify(orderRepository, never()).insert(any(Order.class));
  }

  @Test
  void adminCanCancelAnotherUsersOrderWithoutAReservedCoupon() {
    Order order = pendingOrder();
    order.setUserId(UUID.randomUUID());
    when(orderRepository.findById("order-1")).thenReturn(Optional.of(order));
    when(orderRepository.save(any(Order.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    OrderResponse response = service.cancelOrder("order-1", UUID.randomUUID(), true);

    assertThat(response.getStatus())
        .isEqualTo(com.project.order.generated.model.OrderResponse.StatusEnum.CANCELLED);
    verify(inventoryClient)
        .release(
            "product-1",
            new com.project.order.generated.integration.inventory.model.StockReservationRequest(
                2, "order-1"));
    verify(couponClient, never()).release(any());
  }

  @Test
  void duplicatePaymentFailureDoesNotRepeatCompensation() {
    Order order = pendingOrder();
    order.setPaymentStatus(PaymentStatus.FAILED);
    when(orderRepository.findById("order-1")).thenReturn(Optional.of(order));

    service.onPaymentResult("order-1", "payment-1", PaymentStatus.FAILED);

    verify(inventoryClient, never()).release(any(), any());
    verify(orderRepository, never()).save(any());
  }

  @Test
  void checkoutMapsProvidedAddressesAndWaivesShippingAtTheFreeShippingThreshold() {
    UUID userId = UUID.randomUUID();
    AtomicReference<Order> persisted = new AtomicReference<>();
    ProductResponse product = new ProductResponse();
    product.setId("product-1");
    product.setSku("SKU-1");
    product.setName("Threshold product");
    product.setPrice(new BigDecimal("499.00"));
    product.setActive(true);
    when(productClient.getProduct("product-1")).thenReturn(product);
    when(orderRepository.save(any(Order.class)))
        .thenAnswer(
            invocation -> {
              Order order = invocation.getArgument(0);
              order.setId("order-1");
              persisted.set(order);
              return order;
            });
    when(orderRepository.insert(any(Order.class)))
        .thenAnswer(
            invocation -> {
              Order order = invocation.getArgument(0);
              order.setId("order-1");
              persisted.set(order);
              return order;
            });
    when(orderRepository.findById("order-1"))
        .thenAnswer(invocation -> Optional.ofNullable(persisted.get()));
    OrderRequest request =
        new OrderRequest(
            List.of(new OrderItemRequest("product-1", 1)),
            null,
            new ShippingAddressRequest("Customer", "555", "1 Main", "City", "State", "12345", "IN"),
            new com.project.order.generated.model.BillingAddressRequest(
                "Customer", "555", "1 Main", "City", "State", "12345", "IN"),
            null,
            "CARD");

    service.createOrder(request, userId, "customer@example.com", null);

    assertThat(persisted.get().getShippingCost()).isEqualByComparingTo("0.00");
    assertThat(persisted.get().getShippingAddress().getFullName()).isEqualTo("Customer");
    assertThat(persisted.get().getBillingAddress().getFullName()).isEqualTo("Customer");
    verify(couponClient, never()).validate(any());
  }

  private AtomicReference<Order> stubCheckoutDependencies(UUID userId) {
    ProductResponse product = new ProductResponse();
    product.setId("product-1");
    product.setSku("SKU-1");
    product.setName("Product");
    product.setPrice(new BigDecimal("50.00"));
    product.setActive(true);
    when(productClient.getProduct("product-1")).thenReturn(product);
    when(couponClient.validate(any()))
        .thenReturn(
            new ValidateCouponResponse().valid(true).discountAmount(new BigDecimal("10.00")));
    AtomicReference<Order> savedOrder = new AtomicReference<>();
    when(orderRepository.save(any(Order.class)))
        .thenAnswer(
            invocation -> {
              Order order = invocation.getArgument(0);
              order.setId("order-1");
              savedOrder.set(order);
              return order;
            });
    when(orderRepository.insert(any(Order.class)))
        .thenAnswer(
            invocation -> {
              Order order = invocation.getArgument(0);
              order.setId("order-1");
              savedOrder.set(order);
              return order;
            });
    when(orderRepository.findById("order-1"))
        .thenAnswer(invocation -> Optional.ofNullable(savedOrder.get()));
    return savedOrder;
  }

  private OrderRequest orderRequest() {
    return new OrderRequest(
        List.of(new OrderItemRequest("product-1", 2)), "SAVE10", null, null, null, "CARD");
  }

  private com.project.order.model.SagaState.Operation operation(String id) {
    return com.project.order.model.SagaState.Operation.builder()
        .id(id)
        .resourceType(com.project.order.model.SagaState.ResourceType.INVENTORY)
        .action(com.project.order.model.SagaState.Action.RESERVE)
        .resourceId("product-1")
        .quantity(2)
        .status(com.project.order.model.SagaState.OperationStatus.PENDING)
        .build();
  }

  private Order pendingOrder() {
    OrderItem item = OrderItem.builder().productId("product-1").quantity(2).build();
    Order order = new Order();
    order.setId("order-1");
    order.setStatus(OrderStatus.PENDING);
    order.setPaymentStatus(PaymentStatus.PENDING);
    order.setItems(List.of(item));
    return order;
  }
}
