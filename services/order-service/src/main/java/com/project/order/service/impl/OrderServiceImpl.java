package com.project.order.service.impl;

import com.project.common.exception.ForbiddenOperationException;
import com.project.common.exception.ResourceNotFoundException;
import com.project.order.client.InventoryClient;
import com.project.order.client.ProductClient;
import com.project.order.client.dto.ProductSummary;
import com.project.order.client.dto.StockReservationCommand;
import com.project.order.dto.BillingAddressRequest;
import com.project.order.dto.OrderItemRequest;
import com.project.order.dto.OrderRequest;
import com.project.order.dto.OrderResponse;
import com.project.order.dto.OrderStatusUpdateRequest;
import com.project.order.dto.ShippingAddressRequest;
import com.project.order.exception.OrderValidationException;
import com.project.order.kafka.OrderEventPublisher;
import com.project.order.model.BillingAddress;
import com.project.order.model.Order;
import com.project.order.model.OrderItem;
import com.project.order.model.OrderStatus;
import com.project.order.model.PaymentStatus;
import com.project.order.model.ShippingAddress;
import com.project.order.repository.OrderRepository;
import com.project.order.service.OrderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.UUID;

/**
 * Saga coordinator for the order lifecycle.
 *
 * <p>Order creation flow:
 * <ol>
 *   <li>Idempotency check against Redis</li>
 *   <li>Fetch every product via {@link ProductClient}; snapshot price/sku/sellerId</li>
 *   <li>Compute totals (subtotal, GST, shipping, discount). Tax engine TBD.</li>
 *   <li>Reserve stock for every item via {@link InventoryClient}; on failure, release
 *       any already-reserved items and abort.</li>
 *   <li>Persist the order with status PENDING / PAYMENT_PENDING.</li>
 *   <li>Emit {@code OrderEvent.CREATED} so payment-service can create the PaymentIntent.</li>
 * </ol>
 *
 * <p>Subsequent state transitions are driven by the {@code payment-events} Kafka
 * consumer ({@code PaymentEventListener}) which calls {@link #onPaymentResult}.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OrderServiceImpl implements OrderService {

    private final OrderRepository orderRepository;
    private final OrderEventPublisher eventPublisher;
    private final ProductClient productClient;
    private final InventoryClient inventoryClient;
    private final StringRedisTemplate redis;

    // GST is computed in tax-service in the production flow; for now use a placeholder
    // 18% rate that the tax-service will later replace.
    private static final BigDecimal TAX_RATE = new BigDecimal("0.18");
    private static final BigDecimal SHIPPING_COST = new BigDecimal("49.00");
    private static final BigDecimal FREE_SHIPPING_THRESHOLD = new BigDecimal("499.00");
    private static final String DEFAULT_CURRENCY = "INR";

    @Override
    @Transactional
    public OrderResponse createOrder(OrderRequest request, UUID userId, String userEmail, String idempotencyKey) {
        if (request.getItems() == null || request.getItems().isEmpty()) {
            throw new OrderValidationException("Order must contain at least one item");
        }

        // Idempotency: same idempotency key from same user returns the existing order.
        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
            String key = "order:idemp:" + userId + ":" + idempotencyKey;
            String existingId = redis.opsForValue().get(key);
            if (existingId != null) {
                return mapToResponse(orderRepository.findById(existingId).orElseThrow(
                        () -> new ResourceNotFoundException("Order", existingId)));
            }
            // Acquire idempotency lock for 10 minutes
            Boolean acquired = redis.opsForValue().setIfAbsent(key, "PENDING", Duration.ofMinutes(10));
            if (!Boolean.TRUE.equals(acquired)) {
                throw new OrderValidationException("Duplicate order request in flight");
            }
        }

        // 1. Fetch product snapshots, validate active and stock available
        List<OrderItem> items = new ArrayList<>();
        for (OrderItemRequest item : request.getItems()) {
            ProductSummary p;
            try {
                p = productClient.getProduct(item.getProductId());
            } catch (Exception e) {
                throw new OrderValidationException(
                        "Product not found: " + item.getProductId());
            }
            if (!p.isActive()) {
                throw new OrderValidationException("Product not available: " + p.getId());
            }
            BigDecimal unitPrice = p.getPrice();
            BigDecimal lineTotal = unitPrice.multiply(BigDecimal.valueOf(item.getQuantity()))
                    .setScale(2, RoundingMode.HALF_UP);
            items.add(OrderItem.builder()
                    .productId(p.getId())
                    .sku(p.getSku())
                    .productName(p.getName())
                    .imageUrl(null)
                    .quantity(item.getQuantity())
                    .unitPrice(unitPrice)
                    .discountAmount(BigDecimal.ZERO)
                    .totalPrice(lineTotal)
                    .build());
        }

        BigDecimal subtotal = items.stream().map(OrderItem::getTotalPrice)
                .reduce(BigDecimal.ZERO, BigDecimal::add).setScale(2, RoundingMode.HALF_UP);
        BigDecimal taxAmount = subtotal.multiply(TAX_RATE).setScale(2, RoundingMode.HALF_UP);
        BigDecimal shippingCost = subtotal.compareTo(FREE_SHIPPING_THRESHOLD) >= 0
                ? BigDecimal.ZERO.setScale(2) : SHIPPING_COST;
        BigDecimal discount = BigDecimal.ZERO.setScale(2);
        // Coupons resolved by coupon-service (not yet wired); pass through code only.
        BigDecimal totalAmount = subtotal.add(taxAmount).add(shippingCost).subtract(discount);

        Order order = new Order();
        order.setOrderNumber(generateOrderNumber());
        order.setUserId(userId);
        order.setUserEmail(userEmail);
        order.setStatus(OrderStatus.PENDING);
        order.setPaymentStatus(PaymentStatus.PENDING);
        order.setItems(items);
        order.setSubtotal(subtotal);
        order.setTaxAmount(taxAmount);
        order.setShippingCost(shippingCost);
        order.setDiscountAmount(discount);
        order.setTotalAmount(totalAmount);
        order.setCurrency(DEFAULT_CURRENCY);
        order.setPaymentMethod(request.getPaymentMethod());
        order.setCouponCode(request.getCouponCode());
        order.setNotes(request.getNotes());
        order.setShippingAddress(map(request.getShippingAddress()));
        order.setBillingAddress(map(request.getBillingAddress()));
        order.setCreatedAt(LocalDateTime.now());
        order.setUpdatedAt(LocalDateTime.now());

        // Persist BEFORE reservation so we have a stable orderId for the inventory call.
        order = orderRepository.save(order);
        String orderId = order.getId();

        // 2. Reserve stock for every item, with rollback on failure.
        List<OrderItem> reserved = new ArrayList<>();
        try {
            for (OrderItem item : items) {
                inventoryClient.reserve(item.getProductId(),
                        new StockReservationCommand(item.getQuantity(), orderId));
                reserved.add(item);
            }
        } catch (Exception e) {
            log.warn("Reservation failed for order {}: {}. Rolling back already-reserved items.",
                    orderId, e.getMessage());
            for (OrderItem item : reserved) {
                try {
                    inventoryClient.release(item.getProductId(),
                            new StockReservationCommand(item.getQuantity(), orderId));
                } catch (Exception ex) {
                    log.error("Compensation release failed for {} qty {}: {}",
                            item.getProductId(), item.getQuantity(), ex.getMessage());
                }
            }
            order.setStatus(OrderStatus.CANCELLED);
            order.setCancelledAt(LocalDateTime.now());
            order.setUpdatedAt(LocalDateTime.now());
            orderRepository.save(order);
            throw new OrderValidationException("Insufficient stock to fulfil this order");
        }

        // 3. Emit OrderCreated event so payment-service can initiate the PaymentIntent.
        eventPublisher.publishCreated(order);

        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
            redis.opsForValue().set("order:idemp:" + userId + ":" + idempotencyKey, orderId, Duration.ofHours(24));
        }

        return mapToResponse(order);
    }

    @Override
    public OrderResponse getOrder(String orderId) {
        return mapToResponse(orderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order", orderId)));
    }

    @Override
    public OrderResponse getOrderByNumber(String orderNumber) {
        return mapToResponse(orderRepository.findByOrderNumber(orderNumber)
                .orElseThrow(() -> new ResourceNotFoundException("Order", orderNumber)));
    }

    @Override
    public Page<OrderResponse> getUserOrders(UUID userId, Pageable pageable) {
        return orderRepository.findByUserId(userId, pageable).map(this::mapToResponse);
    }

    @Override
    public Page<OrderResponse> getAllOrders(Pageable pageable) {
        return orderRepository.findAll(pageable).map(this::mapToResponse);
    }

    @Override
    public Page<OrderResponse> getOrdersByStatus(OrderStatus status, Pageable pageable) {
        return orderRepository.findByStatus(status, pageable).map(this::mapToResponse);
    }

    @Override
    @Transactional
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
        order = orderRepository.save(order);
        eventPublisher.publishStatusChanged(order);
        return mapToResponse(order);
    }

    @Override
    @Transactional
    public OrderResponse cancelOrder(String orderId, UUID userId, boolean isAdmin) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order", orderId));
        if (!isAdmin && !order.getUserId().equals(userId)) {
            throw new ForbiddenOperationException("Not your order");
        }
        if (order.getStatus() == OrderStatus.SHIPPED || order.getStatus() == OrderStatus.DELIVERED) {
            throw new OrderValidationException("Cannot cancel a shipped or delivered order");
        }
        // Compensation: release any reserved stock.
        for (OrderItem item : order.getItems()) {
            try {
                inventoryClient.release(item.getProductId(),
                        new StockReservationCommand(item.getQuantity(), orderId));
            } catch (Exception e) {
                log.error("Stock release on cancel failed for {} qty {}: {}",
                        item.getProductId(), item.getQuantity(), e.getMessage());
            }
        }
        order.setStatus(OrderStatus.CANCELLED);
        order.setCancelledAt(LocalDateTime.now());
        order.setUpdatedAt(LocalDateTime.now());
        order = orderRepository.save(order);
        eventPublisher.publishCancelled(order);
        return mapToResponse(order);
    }

    @Override
    @Transactional
    public void onPaymentResult(String orderId, String paymentId, PaymentStatus paymentStatus) {
        Order order = orderRepository.findById(orderId).orElse(null);
        if (order == null) {
            log.warn("Received payment result for unknown order {}", orderId);
            return;
        }
        order.setPaymentId(paymentId);
        order.setPaymentStatus(paymentStatus);
        order.setUpdatedAt(LocalDateTime.now());
        switch (paymentStatus) {
            case COMPLETED -> {
                order.setStatus(OrderStatus.CONFIRMED);
                order.setPaidAt(LocalDateTime.now());
                eventPublisher.publishPaymentCompleted(order);
            }
            case FAILED -> {
                // Compensation: release reserved stock and cancel
                for (OrderItem item : order.getItems()) {
                    try {
                        inventoryClient.release(item.getProductId(),
                                new StockReservationCommand(item.getQuantity(), orderId));
                    } catch (Exception e) {
                        log.error("Stock release on payment-failure failed: {}", e.getMessage());
                    }
                }
                order.setStatus(OrderStatus.CANCELLED);
                order.setCancelledAt(LocalDateTime.now());
                eventPublisher.publishPaymentFailed(order);
            }
            case REFUNDED, PARTIALLY_REFUNDED -> {
                eventPublisher.publishStatusChanged(order);
            }
            default -> { /* PENDING — no transition */ }
        }
        orderRepository.save(order);
    }

    private String generateOrderNumber() {
        return "ORD-" + System.currentTimeMillis() + "-" + (1000 + new Random().nextInt(9000));
    }

    private ShippingAddress map(ShippingAddressRequest r) {
        if (r == null) return null;
        return ShippingAddress.builder()
                .fullName(r.getFullName()).phone(r.getPhone()).street(r.getStreet())
                .city(r.getCity()).state(r.getState()).zipCode(r.getZipCode()).country(r.getCountry())
                .build();
    }

    private BillingAddress map(BillingAddressRequest r) {
        if (r == null) return null;
        return BillingAddress.builder()
                .fullName(r.getFullName()).phone(r.getPhone()).street(r.getStreet())
                .city(r.getCity()).state(r.getState()).zipCode(r.getZipCode()).country(r.getCountry())
                .build();
    }

    private OrderResponse mapToResponse(Order order) {
        return OrderResponse.builder()
                .id(order.getId()).orderNumber(order.getOrderNumber())
                .userId(order.getUserId()).userEmail(order.getUserEmail())
                .status(order.getStatus()).items(order.getItems())
                .subtotal(order.getSubtotal()).taxAmount(order.getTaxAmount())
                .shippingCost(order.getShippingCost()).discountAmount(order.getDiscountAmount())
                .totalAmount(order.getTotalAmount()).currency(order.getCurrency())
                .shippingAddress(order.getShippingAddress()).billingAddress(order.getBillingAddress())
                .paymentId(order.getPaymentId()).paymentMethod(order.getPaymentMethod())
                .paymentStatus(order.getPaymentStatus()).couponCode(order.getCouponCode())
                .notes(order.getNotes())
                .createdAt(order.getCreatedAt()).updatedAt(order.getUpdatedAt())
                .paidAt(order.getPaidAt()).shippedAt(order.getShippedAt())
                .deliveredAt(order.getDeliveredAt()).cancelledAt(order.getCancelledAt())
                .build();
    }
}
