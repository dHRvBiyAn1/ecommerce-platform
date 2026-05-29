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
import com.project.order.model.*;
import com.project.order.repository.OrderRepository;
import com.project.order.repository.OutboxEventRepository;
import com.project.order.service.OrderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
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

@Slf4j
@Service
@RequiredArgsConstructor
public class OrderServiceImpl implements OrderService {

    private final OrderRepository orderRepository;
    private final OutboxEventRepository outboxEventRepository;
    private final ProductClient productClient;
    private final InventoryClient inventoryClient;
    private final StringRedisTemplate redis;

    @Autowired
    @Lazy
    private OrderServiceImpl self;

    private static final BigDecimal TAX_RATE = new BigDecimal("0.18");
    private static final BigDecimal SHIPPING_COST = new BigDecimal("49.00");
    private static final BigDecimal FREE_SHIPPING_THRESHOLD = new BigDecimal("499.00");
    private static final String DEFAULT_CURRENCY = "INR";

    @Override
    public OrderResponse createOrder(OrderRequest request, UUID userId, String userEmail, String idempotencyKey) {
        if (request.getItems() == null || request.getItems().isEmpty()) {
            throw new OrderValidationException("Order must contain at least one item");
        }

        // Idempotency: same idempotency key from same user returns the existing order.
        String lockKey = null;
        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
            lockKey = "order:idemp:" + userId + ":" + idempotencyKey;
            String existingId = redis.opsForValue().get(lockKey);
            if (existingId != null) {
                return mapToResponse(orderRepository.findById(existingId).orElseThrow(
                        () -> new ResourceNotFoundException("Order", existingId)));
            }
            // Acquire idempotency lock for 10 minutes
            Boolean acquired = redis.opsForValue().setIfAbsent(lockKey, "PENDING", Duration.ofMinutes(10));
            if (!Boolean.TRUE.equals(acquired)) {
                throw new OrderValidationException("Duplicate order request in flight");
            }
        }

        // 1. Fetch product snapshots (Feign HTTP I/O - OUTSIDE TRANSACTION)
        List<OrderItem> items = new ArrayList<>();
        for (OrderItemRequest item : request.getItems()) {
            ProductSummary p;
            try {
                p = productClient.getProduct(item.getProductId());
            } catch (Exception e) {
                if (lockKey != null) redis.delete(lockKey);
                throw new OrderValidationException("Product not found: " + item.getProductId());
            }
            if (!p.isActive()) {
                if (lockKey != null) redis.delete(lockKey);
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

        // 2. Persist initially (IN TRANSACTION)
        order = self.saveInitialOrder(order, idempotencyKey, userId);
        String orderId = order.getId();

        // 3. Reserve stock for every item (Feign HTTP I/O - OUTSIDE TRANSACTION)
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
            // Compensate
            for (OrderItem item : reserved) {
                try {
                    inventoryClient.release(item.getProductId(),
                            new StockReservationCommand(item.getQuantity(), orderId));
                } catch (Exception ex) {
                    log.error("Compensation release failed for {} qty {}: {}",
                            item.getProductId(), item.getQuantity(), ex.getMessage());
                }
            }
            // Update status to CANCELLED in a separate transaction
            self.cancelOrderInternal(orderId);
            throw new OrderValidationException("Insufficient stock to fulfil this order");
        }

        // 4. Stock reservation succeeded - trigger outbox event (IN TRANSACTION)
        self.saveOutboxEvent(orderId, "CREATED");

        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
            redis.opsForValue().set("order:idemp:" + userId + ":" + idempotencyKey, orderId, Duration.ofHours(24));
        }

        return mapToResponse(orderRepository.findById(orderId).orElse(order));
    }

    @Transactional
    public Order saveInitialOrder(Order order, String idempotencyKey, UUID userId) {
        return orderRepository.save(order);
    }

    @Transactional
    public void saveOutboxEvent(String aggregateId, String eventType) {
        OutboxEvent event = OutboxEvent.builder()
                .aggregateType("ORDER")
                .aggregateId(aggregateId)
                .eventType(eventType)
                .status("PENDING")
                .createdAt(LocalDateTime.now())
                .build();
        outboxEventRepository.save(event);
    }

    @Transactional
    public void cancelOrderInternal(String orderId) {
        Order order = orderRepository.findById(orderId).orElse(null);
        if (order != null) {
            order.setStatus(OrderStatus.CANCELLED);
            order.setCancelledAt(LocalDateTime.now());
            order.setUpdatedAt(LocalDateTime.now());
            orderRepository.save(order);
            saveOutboxEvent(orderId, "CANCELLED");
        }
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
        saveOutboxEvent(orderId, "STATUS_CHANGED");
        return mapToResponse(order);
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

        self.cancelOrderInternal(orderId);
        return mapToResponse(orderRepository.findById(orderId).orElse(order));
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
                self.onPaymentCompletedInternal(orderId, paymentId);
            }
            case FAILED -> {
                // Compensation: release reserved stock (Feign HTTP I/O - OUTSIDE TRANSACTION)
                for (OrderItem item : order.getItems()) {
                    try {
                        inventoryClient.release(item.getProductId(),
                                new StockReservationCommand(item.getQuantity(), orderId));
                    } catch (Exception e) {
                        log.error("Stock release on payment-failure failed: {}", e.getMessage());
                    }
                }
                self.onPaymentFailedInternal(orderId, paymentId);
            }
            case REFUNDED, PARTIALLY_REFUNDED -> {
                self.onPaymentRefundedInternal(orderId, paymentId, paymentStatus);
            }
            default -> { 
                self.updatePaymentStatusInternal(orderId, paymentId, paymentStatus);
            }
        }
    }

    @Transactional
    public void onPaymentCompletedInternal(String orderId, String paymentId) {
        Order order = orderRepository.findById(orderId).orElse(null);
        if (order != null) {
            order.setPaymentId(paymentId);
            order.setPaymentStatus(PaymentStatus.COMPLETED);
            order.setStatus(OrderStatus.CONFIRMED);
            order.setPaidAt(LocalDateTime.now());
            order.setUpdatedAt(LocalDateTime.now());
            orderRepository.save(order);
            saveOutboxEvent(orderId, "PAYMENT_COMPLETED");
        }
    }

    @Transactional
    public void onPaymentFailedInternal(String orderId, String paymentId) {
        Order order = orderRepository.findById(orderId).orElse(null);
        if (order != null) {
            order.setPaymentId(paymentId);
            order.setPaymentStatus(PaymentStatus.FAILED);
            order.setStatus(OrderStatus.CANCELLED);
            order.setCancelledAt(LocalDateTime.now());
            order.setUpdatedAt(LocalDateTime.now());
            orderRepository.save(order);
            saveOutboxEvent(orderId, "PAYMENT_FAILED");
        }
    }

    @Transactional
    public void onPaymentRefundedInternal(String orderId, String paymentId, PaymentStatus paymentStatus) {
        Order order = orderRepository.findById(orderId).orElse(null);
        if (order != null) {
            order.setPaymentId(paymentId);
            order.setPaymentStatus(paymentStatus);
            order.setUpdatedAt(LocalDateTime.now());
            orderRepository.save(order);
            saveOutboxEvent(orderId, "STATUS_CHANGED");
        }
    }

    @Transactional
    public void updatePaymentStatusInternal(String orderId, String paymentId, PaymentStatus paymentStatus) {
        Order order = orderRepository.findById(orderId).orElse(null);
        if (order != null) {
            order.setPaymentId(paymentId);
            order.setPaymentStatus(paymentStatus);
            order.setUpdatedAt(LocalDateTime.now());
            orderRepository.save(order);
        }
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
