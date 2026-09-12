package com.project.order.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.project.common.event.OrderEvent;
import com.project.common.exception.ForbiddenOperationException;
import com.project.common.exception.ResourceNotFoundException;
import com.project.order.client.InventoryClient;
import com.project.order.client.ProductClient;
import com.project.order.client.CouponClient;
import com.project.order.client.dto.ProductSummary;
import com.project.order.client.dto.StockReservationCommand;
import com.project.order.client.dto.CouponValidationRequest;
import com.project.order.client.dto.CouponValidationResponse;
import com.project.order.client.dto.CouponReservationCommand;
import com.project.order.client.dto.CouponTransitionCommand;
import com.project.order.application.mapper.OrderMapper;
import com.project.order.application.validator.OrderRequestValidator;
import com.project.order.constant.OrderPricing;
import com.project.order.dto.BillingAddressRequest;
import com.project.order.dto.OrderItemRequest;
import com.project.order.dto.OrderRequest;
import com.project.order.dto.OrderResponse;
import com.project.order.dto.OrderStatusUpdateRequest;
import com.project.order.dto.ShippingAddressRequest;
import com.project.order.exception.OrderValidationException;
import com.project.order.model.*;
import com.project.order.repository.OrderRepository;
import com.project.order.service.OrderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.UUID;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

@Slf4j
@Service
@RequiredArgsConstructor
public class OrderServiceImpl implements OrderService {

    private static final int MAX_OPTIMISTIC_ATTEMPTS = 3;

    private final OrderRepository orderRepository;
    private final ProductClient productClient;
    private final InventoryClient inventoryClient;
    private final CouponClient couponClient;
    private final OrderMapper orderMapper;
    private final OrderRequestValidator orderRequestValidator;
    private final ObjectMapper objectMapper;

    @Override
    public OrderResponse createOrder(OrderRequest request, UUID userId, String userEmail, String idempotencyKey) {
        orderRequestValidator.validateCreate(request, userId);

        String durableKey = idempotencyKey == null || idempotencyKey.isBlank() ? null : idempotencyKey;
        if (durableKey != null) {
            Order existing = orderRepository.findByUserIdAndIdempotencyKey(userId, durableKey).orElse(null);
            if (existing != null) {
                return orderMapper.toResponse(existing);
            }
        }

        List<OrderItem> items = new ArrayList<>();
        for (OrderItemRequest item : request.items()) {
            ProductSummary p;
            try {
                p = productClient.getProduct(item.productId());
            } catch (Exception e) {
                throw new OrderValidationException("Product not found: " + item.productId());
            }
            if (!p.isActive()) {
                throw new OrderValidationException("Product not available: " + p.getId());
            }
            BigDecimal unitPrice = p.getPrice();
            BigDecimal lineTotal = unitPrice.multiply(BigDecimal.valueOf(item.quantity()))
                    .setScale(2, RoundingMode.HALF_UP);
            items.add(OrderItem.builder()
                    .lineId(UUID.randomUUID().toString())
                    .productId(p.getId())
                    .sku(p.getSku())
                    .productName(p.getName())
                    .imageUrl(null)
                    .quantity(item.quantity())
                    .unitPrice(unitPrice)
                    .discountAmount(BigDecimal.ZERO)
                    .totalPrice(lineTotal)
                    .build());
        }

        BigDecimal subtotal = items.stream().map(OrderItem::getTotalPrice)
                .reduce(BigDecimal.ZERO, BigDecimal::add).setScale(2, RoundingMode.HALF_UP);
        BigDecimal taxAmount = subtotal.multiply(OrderPricing.TAX_RATE).setScale(2, RoundingMode.HALF_UP);
        BigDecimal shippingCost = subtotal.compareTo(OrderPricing.FREE_SHIPPING_THRESHOLD) >= 0
                ? BigDecimal.ZERO.setScale(2) : OrderPricing.SHIPPING_COST;
        BigDecimal discount = BigDecimal.ZERO.setScale(2);
        
        if (request.couponCode() != null && !request.couponCode().isBlank()) {
            CouponValidationResponse couponRes = couponClient.validate(
                    CouponValidationRequest.builder()
                            .code(request.couponCode())
                            .userId(userId)
                            .subtotal(subtotal)
                            .currency(OrderPricing.DEFAULT_CURRENCY)
                            .build()
            );
            if (!couponRes.isValid()) {
                throw new OrderValidationException("Invalid coupon: " + couponRes.getReason());
            }
            discount = couponRes.getDiscountAmount();
        }

        BigDecimal totalAmount = subtotal.add(taxAmount).add(shippingCost).subtract(discount);
        if (totalAmount.compareTo(BigDecimal.ZERO) < 0) totalAmount = BigDecimal.ZERO;

        Order order = new Order();
        order.setOrderNumber(generateOrderNumber());
        order.setUserId(userId);
        order.setIdempotencyKey(durableKey);
        order.setUserEmail(userEmail);
        order.setStatus(OrderStatus.PENDING);
        order.setPaymentStatus(PaymentStatus.PENDING);
        order.setItems(items);
        order.setSubtotal(subtotal);
        order.setTaxAmount(taxAmount);
        order.setShippingCost(shippingCost);
        order.setDiscountAmount(discount);
        order.setTotalAmount(totalAmount);
        order.setCurrency(OrderPricing.DEFAULT_CURRENCY);
        order.setPaymentMethod(request.paymentMethod());
        order.setCouponCode(request.couponCode());
        order.setNotes(request.notes());
        order.setShippingAddress(map(request.shippingAddress()));
        order.setBillingAddress(map(request.billingAddress()));
        order.setCreatedAt(LocalDateTime.now());
        order.setUpdatedAt(LocalDateTime.now());
        order.setOutboxEvents(new ArrayList<>());
        order.setSagaState(checkoutSaga(order));

        try {
            order = orderRepository.insert(order);
        } catch (DuplicateKeyException exception) {
            if (durableKey == null) {
                throw exception;
            }
            Order winner = orderRepository.findByUserIdAndIdempotencyKey(userId, durableKey)
                    .orElseThrow(() -> exception);
            return orderMapper.toResponse(winner);
        }

        try {
            order = executeSaga(order.getId(), true);
        } catch (RuntimeException exception) {
            throw new OrderValidationException("Unable to reserve checkout resources");
        }
        return orderMapper.toResponse(order);
    }

    private void saveOutboxEvent(Order order, String eventType) {
        if (order.getOutboxEvents() == null) {
            order.setOutboxEvents(new ArrayList<>());
        }
        OutboxEvent event = OutboxEvent.builder()
                .id(UUID.randomUUID().toString())
                .deliveryId(UUID.randomUUID().toString())
                .aggregateType("ORDER")
                .aggregateId(order.getId())
                .eventType(eventType)
                .payload(snapshotPayload(order, eventType))
                .status("PENDING")
                .attempts(0)
                .nextAttemptAt(LocalDateTime.now())
                .createdAt(LocalDateTime.now())
                .build();
        order.getOutboxEvents().add(event);
    }

    @Scheduled(fixedDelayString = "${order.saga.recovery-delay-ms:5000}")
    public void recoverOrders() {
        for (Order order : orderRepository.findOrdersWithRecoverableSaga(LocalDateTime.now())) {
            try {
                executeSaga(order.getId(), false);
            } catch (RuntimeException exception) {
                log.warn("Saga recovery attempt failed for order {}: {}", order.getId(), exception.getMessage());
            }
        }
    }

    private Order executeSaga(String orderId, boolean propagateFailure) {
        ensureUniqueOperationIds(orderId);
        while (true) {
            Order current = orderRepository.findById(orderId).orElseThrow(
                    () -> new ResourceNotFoundException("Order", orderId));
            SagaState saga = current.getSagaState();
            if (saga == null || saga.getStage() == SagaState.Stage.COMPLETED) {
                return current;
            }
            SagaState.Operation next = saga.getOperations().stream()
                    .filter(operation -> operation.getStatus() != SagaState.OperationStatus.COMPLETED)
                    .findFirst()
                    .orElse(null);
            if (next == null) {
                return completeSaga(orderId);
            }

            Order claimed = updateOrderWithRetry(orderId, order -> {
                SagaState.Operation operation = operation(order.getSagaState(), next.getId());
                operation.setStatus(SagaState.OperationStatus.IN_PROGRESS);
                order.getSagaState().setStage(SagaState.Stage.RESERVING);
                order.getSagaState().setNextAttemptAt(LocalDateTime.now().plusSeconds(30));
                order.getSagaState().setLastError(null);
            });
            SagaState.Operation claimedOperation = operation(claimed.getSagaState(), next.getId());
            try {
                executeOperation(claimed, claimedOperation);
            } catch (RuntimeException exception) {
                updateOrderWithRetry(orderId, order -> {
                    SagaState failed = order.getSagaState();
                    failed.setStage(SagaState.Stage.RETRYABLE);
                    failed.setAttempts(failed.getAttempts() + 1);
                    failed.setLastError(exception.getMessage());
                    failed.setNextAttemptAt(LocalDateTime.now().plus(retryDelay(failed.getAttempts())));
                });
                if (propagateFailure) {
                    throw exception;
                }
                return orderRepository.findById(orderId).orElse(claimed);
            }
            updateOrderWithRetry(orderId, order -> {
                operation(order.getSagaState(), next.getId()).setStatus(SagaState.OperationStatus.COMPLETED);
                order.getSagaState().setStage(SagaState.Stage.RESERVING);
                order.getSagaState().setNextAttemptAt(LocalDateTime.now().plusSeconds(30));
            });
        }
    }

    private void executeOperation(Order order, SagaState.Operation operation) {
        if (operation.getResourceType() == SagaState.ResourceType.COUPON) {
            switch (operation.getAction()) {
                case RESERVE -> couponClient.reserve(new CouponReservationCommand(
                        order.getCouponCode(), order.getUserId(), order.getId(),
                        order.getSubtotal(), order.getCurrency()));
                case COMMIT -> couponClient.commit(couponTransition(order));
                case RELEASE -> couponClient.release(couponTransition(order));
            }
            return;
        }
        StockReservationCommand command = new StockReservationCommand(operation.getQuantity(), order.getId());
        switch (operation.getAction()) {
            case RESERVE -> inventoryClient.reserve(operation.getResourceId(), command);
            case COMMIT -> inventoryClient.commit(operation.getResourceId(), command);
            case RELEASE -> inventoryClient.release(operation.getResourceId(), command);
        }
    }

    private Order completeSaga(String orderId) {
        return updateOrderWithRetry(orderId, order -> {
            SagaState saga = order.getSagaState();
            if (saga.getStage() == SagaState.Stage.COMPLETED) {
                return;
            }
            switch (saga.getWorkflow()) {
                case CHECKOUT -> saveOutboxEvent(order, "CREATED");
                case PAYMENT_COMPLETION -> {
                    order.setPaymentId(saga.getPaymentId());
                    order.setPaymentStatus(PaymentStatus.COMPLETED);
                    order.setStatus(OrderStatus.CONFIRMED);
                    order.setPaidAt(LocalDateTime.now());
                    saveOutboxEvent(order, "PAYMENT_COMPLETED");
                }
                case PAYMENT_FAILURE -> {
                    order.setPaymentId(saga.getPaymentId());
                    order.setPaymentStatus(PaymentStatus.FAILED);
                    order.setStatus(OrderStatus.CANCELLED);
                    order.setCancelledAt(LocalDateTime.now());
                    saveOutboxEvent(order, "PAYMENT_FAILED");
                }
            }
            order.setUpdatedAt(LocalDateTime.now());
            saga.setStage(SagaState.Stage.COMPLETED);
            saga.setNextAttemptAt(null);
            saga.setLastError(null);
        });
    }

    private Order updateOrderWithRetry(String orderId, Consumer<Order> change) {
        OptimisticLockingFailureException lastFailure = null;
        for (int attempt = 0; attempt < MAX_OPTIMISTIC_ATTEMPTS; attempt++) {
            Order current = orderRepository.findById(orderId).orElseThrow(
                    () -> new ResourceNotFoundException("Order", orderId));
            change.accept(current);
            try {
                return orderRepository.save(current);
            } catch (OptimisticLockingFailureException exception) {
                lastFailure = exception;
            }
        }
        throw lastFailure;
    }

    private SagaState checkoutSaga(Order order) {
        return SagaState.builder()
                .workflow(SagaState.Workflow.CHECKOUT)
                .stage(SagaState.Stage.RESERVING)
                .nextAttemptAt(LocalDateTime.now().plusSeconds(30))
                .operations(operations(order, SagaState.Action.RESERVE))
                .build();
    }

    private SagaState paymentSaga(Order order, String paymentId, SagaState.Workflow workflow) {
        SagaState.Action action = workflow == SagaState.Workflow.PAYMENT_COMPLETION
                ? SagaState.Action.COMMIT : SagaState.Action.RELEASE;
        return SagaState.builder()
                .workflow(workflow)
                .paymentId(paymentId)
                .stage(SagaState.Stage.RESERVING)
                .nextAttemptAt(LocalDateTime.now().plusSeconds(30))
                .operations(operations(order, action))
                .build();
    }

    private List<SagaState.Operation> operations(Order order, SagaState.Action action) {
        List<SagaState.Operation> operations = new ArrayList<>();
        if (hasCoupon(order)) {
            operations.add(sagaOperation(SagaState.ResourceType.COUPON, action, order.getCouponCode(), 0));
        }
        Map<String, Integer> legacyOccurrences = new HashMap<>();
        for (OrderItem item : order.getItems()) {
            if (item.getLineId() == null || item.getLineId().isBlank()) {
                String fingerprint = item.getProductId() + ":" + item.getSku() + ":"
                        + item.getQuantity() + ":" + item.getUnitPrice();
                int occurrence = legacyOccurrences.merge(fingerprint, 1, Integer::sum);
                item.setLineId(UUID.nameUUIDFromBytes(
                        (fingerprint + ":" + occurrence).getBytes(java.nio.charset.StandardCharsets.UTF_8)).toString());
            }
            operations.add(sagaOperation(
                    SagaState.ResourceType.INVENTORY, action, item.getProductId(), item.getQuantity(), item.getLineId()));
        }
        return operations;
    }

    private SagaState.Operation sagaOperation(
            SagaState.ResourceType resourceType, SagaState.Action action, String resourceId, int quantity) {
        return sagaOperation(resourceType, action, resourceId, quantity, resourceId);
    }

    private SagaState.Operation sagaOperation(
            SagaState.ResourceType resourceType, SagaState.Action action,
            String resourceId, int quantity, String identity) {
        return SagaState.Operation.builder()
                .id(action + ":" + resourceType + ":" + resourceId + ":" + identity)
                .resourceType(resourceType)
                .action(action)
                .resourceId(resourceId)
                .quantity(quantity)
                .status(SagaState.OperationStatus.PENDING)
                .build();
    }

    private void ensureUniqueOperationIds(String orderId) {
        Order current = orderRepository.findById(orderId).orElse(null);
        if (current == null || current.getSagaState() == null || current.getSagaState().getOperations() == null) {
            return;
        }
        Set<String> ids = new HashSet<>();
        boolean duplicate = current.getSagaState().getOperations().stream()
                .anyMatch(operation -> !ids.add(operation.getId()));
        if (!duplicate) {
            return;
        }
        updateOrderWithRetry(orderId, order -> {
            Map<String, Integer> occurrences = new HashMap<>();
            for (SagaState.Operation operation : order.getSagaState().getOperations()) {
                int occurrence = occurrences.merge(operation.getId(), 1, Integer::sum);
                operation.setId(operation.getId() + ":legacy:" + occurrence);
            }
        });
    }

    private SagaState.Operation operation(SagaState saga, String operationId) {
        return saga.getOperations().stream()
                .filter(operation -> operationId.equals(operation.getId()))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Missing saga operation " + operationId));
    }

    private Duration retryDelay(int attempts) {
        return Duration.ofSeconds(Math.min(60, Math.max(1, attempts) * 5L));
    }

    private String snapshotPayload(Order order, String eventType) {
        List<OrderEvent.Item> items = order.getItems() == null ? List.of() : order.getItems().stream()
                .map(item -> OrderEvent.Item.builder()
                        .productId(item.getProductId())
                        .sku(item.getSku())
                        .quantity(item.getQuantity())
                        .unitPrice(item.getUnitPrice())
                        .build())
                .toList();
        OrderEvent event = OrderEvent.orderEventBuilder()
                .type(eventType(eventType, order.getStatus()))
                .orderId(order.getId())
                .orderNumber(order.getOrderNumber())
                .userId(order.getUserId())
                .userEmail(order.getUserEmail())
                .totalAmount(order.getTotalAmount())
                .currency(order.getCurrency())
                .items(items)
                .build();
        try {
            return objectMapper.writeValueAsString(event);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Unable to snapshot order event", exception);
        }
    }

    private OrderEvent.Type eventType(String eventType, OrderStatus status) {
        return switch (eventType) {
            case "CANCELLED" -> OrderEvent.Type.CANCELLED;
            case "PAYMENT_COMPLETED" -> OrderEvent.Type.PAYMENT_COMPLETED;
            case "PAYMENT_FAILED" -> OrderEvent.Type.PAYMENT_FAILED;
            case "STATUS_CHANGED" -> switch (status) {
                case CONFIRMED -> OrderEvent.Type.CONFIRMED;
                case PROCESSING -> OrderEvent.Type.PROCESSING;
                case SHIPPED -> OrderEvent.Type.SHIPPED;
                case DELIVERED -> OrderEvent.Type.DELIVERED;
                case CANCELLED -> OrderEvent.Type.CANCELLED;
                case REFUNDED -> OrderEvent.Type.REFUNDED;
                default -> OrderEvent.Type.CREATED;
            };
            default -> OrderEvent.Type.CREATED;
        };
    }

    public void cancelOrderInternal(String orderId) {
        Order order = orderRepository.findById(orderId).orElse(null);
        if (order != null) {
            order.setStatus(OrderStatus.CANCELLED);
            order.setCancelledAt(LocalDateTime.now());
            order.setUpdatedAt(LocalDateTime.now());
            saveOutboxEvent(order, "CANCELLED");
            orderRepository.save(order);
        }
    }

    @Override
    public OrderResponse getOrder(String orderId) {
        return orderMapper.toResponse(orderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order", orderId)));
    }

    @Override
    public OrderResponse getOrderByNumber(String orderNumber) {
        return orderMapper.toResponse(orderRepository.findByOrderNumber(orderNumber)
                .orElseThrow(() -> new ResourceNotFoundException("Order", orderNumber)));
    }

    @Override
    public Page<OrderResponse> getUserOrders(UUID userId, Pageable pageable) {
        return orderRepository.findByUserId(userId, pageable).map(orderMapper::toResponse);
    }

    @Override
    public Page<OrderResponse> getAllOrders(Pageable pageable) {
        return orderRepository.findAll(pageable).map(orderMapper::toResponse);
    }

    @Override
    public Page<OrderResponse> getOrdersByStatus(OrderStatus status, Pageable pageable) {
        return orderRepository.findByStatus(status, pageable).map(orderMapper::toResponse);
    }

    @Override
    public OrderResponse updateOrderStatus(String orderId, OrderStatusUpdateRequest request) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order", orderId));
        OrderStatus newStatus = request.getStatus();
        order.setStatus(newStatus);
        order.setUpdatedAt(LocalDateTime.now());
        if (request.getNotes() != null) order.setNotes(request.getNotes());
        switch (newStatus) {
            case SHIPPED -> order.setShippedAt(LocalDateTime.now());
            case DELIVERED -> order.setDeliveredAt(LocalDateTime.now());
            case CANCELLED -> order.setCancelledAt(LocalDateTime.now());
            default -> { /* no-op */ }
        }
        saveOutboxEvent(order, "STATUS_CHANGED");
        order = orderRepository.save(order);
        return orderMapper.toResponse(order);
    }

    @Override
    public OrderResponse cancelOrder(String orderId, UUID userId, boolean isAdmin) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order", orderId));
        if (!isAdmin && !order.getUserId().equals(userId)) {
            throw new ForbiddenOperationException("Not your order");
        }
        if (order.getStatus() == OrderStatus.SHIPPED || order.getStatus() == OrderStatus.DELIVERED) {
            throw new OrderValidationException("Cannot cancel a shipped or delivered order");
        }

        // Compensation: release any reserved stock (Feign HTTP I/O - OUTSIDE TRANSACTION)
        for (OrderItem item : order.getItems()) {
            try {
                inventoryClient.release(item.getProductId(),
                        new StockReservationCommand(item.getQuantity(), orderId));
            } catch (Exception e) {
                log.error("Stock release on cancel failed for {} qty {}: {}",
                        item.getProductId(), item.getQuantity(), e.getMessage());
            }
        }
        releaseCoupon(order);

        cancelOrderInternal(orderId);
        return orderMapper.toResponse(orderRepository.findById(orderId).orElse(order));
    }

    @Override
    public void onPaymentResult(String orderId, String paymentId, PaymentStatus paymentStatus) {
        Order order = orderRepository.findById(orderId).orElse(null);
        if (order == null) {
            log.warn("Received payment result for unknown order {}", orderId);
            return;
        }

        switch (paymentStatus) {
            case COMPLETED -> {
                if (order.getPaymentStatus() == PaymentStatus.COMPLETED) {
                    log.info("Ignoring duplicate payment completion for order {}", orderId);
                    return;
                }
                startPaymentSaga(orderId, paymentId, SagaState.Workflow.PAYMENT_COMPLETION);
            }
            case FAILED -> {
                if (order.getPaymentStatus() == PaymentStatus.FAILED) {
                    log.info("Ignoring duplicate payment failure for order {}", orderId);
                    return;
                }
                startPaymentSaga(orderId, paymentId, SagaState.Workflow.PAYMENT_FAILURE);
            }
            case REFUNDED, PARTIALLY_REFUNDED -> {
                onPaymentRefundedInternal(orderId, paymentId, paymentStatus);
            }
            default -> { 
                updatePaymentStatusInternal(orderId, paymentId, paymentStatus);
            }
        }
    }

    private void startPaymentSaga(String orderId, String paymentId, SagaState.Workflow workflow) {
        updateOrderWithRetry(orderId, order -> {
            SagaState current = order.getSagaState();
            if (current != null && current.getWorkflow() == workflow
                    && java.util.Objects.equals(current.getPaymentId(), paymentId)
                    && current.getStage() != SagaState.Stage.COMPLETED) {
                return;
            }
            order.setSagaState(paymentSaga(order, paymentId, workflow));
        });
        try {
            executeSaga(orderId, false);
        } catch (RuntimeException exception) {
            log.warn("Payment saga deferred for order {}: {}", orderId, exception.getMessage());
        }
    }

    public void onPaymentRefundedInternal(String orderId, String paymentId, PaymentStatus paymentStatus) {
        if (orderRepository.findById(orderId).isPresent()) {
            updateOrderWithRetry(orderId, order -> {
            order.setPaymentId(paymentId);
            order.setPaymentStatus(paymentStatus);
            order.setUpdatedAt(LocalDateTime.now());
            saveOutboxEvent(order, "STATUS_CHANGED");
            });
        }
    }

    public void updatePaymentStatusInternal(String orderId, String paymentId, PaymentStatus paymentStatus) {
        if (orderRepository.findById(orderId).isPresent()) {
            updateOrderWithRetry(orderId, order -> {
            order.setPaymentId(paymentId);
            order.setPaymentStatus(paymentStatus);
            order.setUpdatedAt(LocalDateTime.now());
            });
        }
    }

    private boolean hasCoupon(Order order) {
        return order.getCouponCode() != null && !order.getCouponCode().isBlank();
    }

    private CouponTransitionCommand couponTransition(Order order) {
        return new CouponTransitionCommand(order.getCouponCode(), order.getUserId(), order.getId());
    }

    private void releaseCoupon(Order order) {
        if (!hasCoupon(order)) {
            return;
        }
        try {
            couponClient.release(couponTransition(order));
        } catch (Exception exception) {
            log.error("Coupon release failed for order {}: {}", order.getId(), exception.getMessage());
        }
    }

    private String generateOrderNumber() {
        return "ORD-" + System.currentTimeMillis() + "-" + (1000 + new Random().nextInt(9000));
    }

    private ShippingAddress map(ShippingAddressRequest r) {
        if (r == null) return null;
        return ShippingAddress.builder()
                .fullName(r.fullName()).phone(r.phone()).street(r.street())
                .city(r.city()).state(r.state()).zipCode(r.zipCode()).country(r.country())
                .build();
    }

    private BillingAddress map(BillingAddressRequest r) {
        if (r == null) return null;
        return BillingAddress.builder()
                .fullName(r.fullName()).phone(r.phone()).street(r.street())
                .city(r.city()).state(r.state()).zipCode(r.zipCode()).country(r.country())
                .build();
    }
}
