package com.project.order.service.impl;

import com.project.order.dto.*;
import com.project.order.event.OrderEvent;
import com.project.order.exception.OrderValidationException;
import com.project.order.exception.ResourceNotFoundException;
import com.project.order.kafka.OrderEventPublisher;
import com.project.order.model.*;
import com.project.order.repository.OrderRepository;
import com.project.order.service.OrderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Random;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class OrderServiceImpl implements OrderService {

    private final OrderRepository orderRepository;
    private final OrderEventPublisher eventPublisher;

    private static final BigDecimal TAX_RATE = new BigDecimal("0.10");
    private static final BigDecimal SHIPPING_COST = new BigDecimal("5.99");
    private static final BigDecimal FREE_SHIPPING_THRESHOLD = new BigDecimal("50.00");
    private static final String DEFAULT_CURRENCY = "USD";

    @Override
    @Transactional
    public OrderResponse createOrder(OrderRequest request, UUID userId, String userEmail) {
        log.info("Creating order for user: {}", userId);

        if (request.getItems() == null || request.getItems().isEmpty()) {
            throw new OrderValidationException("Order must contain at least one item");
        }

        Order order = new Order();
        order.setOrderNumber(generateOrderNumber());
        order.setUserId(userId);
        order.setUserEmail(userEmail);
        order.setStatus(OrderStatus.PENDING);
        order.setPaymentStatus(PaymentStatus.PENDING);
        order.setCurrency(DEFAULT_CURRENCY);
        order.setPaymentMethod(request.getPaymentMethod());
        order.setCouponCode(request.getCouponCode());
        order.setNotes(request.getNotes());
        order.setCreatedAt(LocalDateTime.now());
        order.setUpdatedAt(LocalDateTime.now());

        if (request.getShippingAddress() != null) {
            order.setShippingAddress(mapShippingAddress(request.getShippingAddress()));
        }
        if (request.getBillingAddress() != null) {
            order.setBillingAddress(mapBillingAddress(request.getBillingAddress()));
        }

        List<OrderItem> orderItems = request.getItems().stream()
                .map(this::mapToOrderItem)
                .collect(Collectors.toList());
        order.setItems(orderItems);

        BigDecimal subtotal = calculateSubtotal(orderItems);
        order.setSubtotal(subtotal);

        BigDecimal taxAmount = calculateTax(subtotal);
        order.setTaxAmount(taxAmount);

        BigDecimal shippingCost = calculateShipping(subtotal);
        order.setShippingCost(shippingCost);

        BigDecimal discountAmount = calculateDiscount(subtotal, request.getCouponCode());
        order.setDiscountAmount(discountAmount);

        BigDecimal totalAmount = subtotal.add(taxAmount).add(shippingCost).subtract(discountAmount);
        order.setTotalAmount(totalAmount);

        Order savedOrder = orderRepository.save(order);
        log.info("Order created successfully: {}", savedOrder.getOrderNumber());

        publishOrderEvent(savedOrder, "ORDER_CREATED");

        return mapToResponse(savedOrder);
    }

    @Override
    public OrderResponse getOrder(String orderId) {
        log.debug("Fetching order by id: {}", orderId);
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found with id: " + orderId));
        return mapToResponse(order);
    }

    @Override
    public OrderResponse getOrderByNumber(String orderNumber) {
        log.debug("Fetching order by number: {}", orderNumber);
        Order order = orderRepository.findByOrderNumber(orderNumber)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found with number: " + orderNumber));
        return mapToResponse(order);
    }

    @Override
    public Page<OrderResponse> getUserOrders(UUID userId, Pageable pageable) {
        log.debug("Fetching orders for user: {}", userId);
        return orderRepository.findByUserId(userId, pageable)
                .map(this::mapToResponse);
    }

    @Override
    public Page<OrderResponse> getAllOrders(Pageable pageable) {
        log.debug("Fetching all orders");
        return orderRepository.findAll(pageable)
                .map(this::mapToResponse);
    }

    @Override
    @Transactional
    public OrderResponse updateOrderStatus(String orderId, OrderStatusUpdateRequest request) {
        log.info("Updating status for order: {} to {}", orderId, request.getStatus());
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found with id: " + orderId));

        OrderStatus newStatus = request.getStatus();
        order.setStatus(newStatus);
        order.setUpdatedAt(LocalDateTime.now());

        if (request.getNotes() != null) {
            order.setNotes(request.getNotes());
        }

        switch (newStatus) {
            case SHIPPED -> order.setShippedAt(LocalDateTime.now());
            case DELIVERED -> order.setDeliveredAt(LocalDateTime.now());
            case CANCELLED -> order.setCancelledAt(LocalDateTime.now());
            default -> {}
        }

        Order savedOrder = orderRepository.save(order);
        log.info("Order status updated: {} -> {}", savedOrder.getOrderNumber(), newStatus);

        publishOrderEvent(savedOrder, "ORDER_STATUS_UPDATED");

        return mapToResponse(savedOrder);
    }

    @Override
    @Transactional
    public OrderResponse cancelOrder(String orderId, UUID userId) {
        log.info("Cancelling order: {} by user: {}", orderId, userId);
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found with id: " + orderId));

        if (!order.getUserId().equals(userId)) {
            throw new OrderValidationException("Not authorized to cancel this order");
        }

        if (order.getStatus() == OrderStatus.SHIPPED || order.getStatus() == OrderStatus.DELIVERED) {
            throw new OrderValidationException("Cannot cancel order that has already been shipped or delivered");
        }

        order.setStatus(OrderStatus.CANCELLED);
        order.setCancelledAt(LocalDateTime.now());
        order.setUpdatedAt(LocalDateTime.now());

        Order savedOrder = orderRepository.save(order);
        log.info("Order cancelled: {}", savedOrder.getOrderNumber());

        publishOrderEvent(savedOrder, "ORDER_CANCELLED");

        return mapToResponse(savedOrder);
    }

    @Override
    @Transactional
    public void processOrderPayment(String orderId, String paymentId, PaymentStatus paymentStatus) {
        log.info("Processing payment for order: {}, paymentId: {}, status: {}", orderId, paymentId, paymentStatus);
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found with id: " + orderId));

        order.setPaymentId(paymentId);
        order.setPaymentStatus(paymentStatus);
        order.setUpdatedAt(LocalDateTime.now());

        if (paymentStatus == PaymentStatus.COMPLETED) {
            order.setStatus(OrderStatus.CONFIRMED);
            order.setPaidAt(LocalDateTime.now());
        } else if (paymentStatus == PaymentStatus.FAILED) {
            order.setStatus(OrderStatus.CANCELLED);
        }

        Order savedOrder = orderRepository.save(order);
        log.info("Payment processed for order: {}, status: {}", savedOrder.getOrderNumber(), paymentStatus);

        publishOrderEvent(savedOrder, "ORDER_PAYMENT_" + paymentStatus.name());
    }

    @Override
    public Page<OrderResponse> getOrdersByStatus(OrderStatus status, Pageable pageable) {
        log.debug("Fetching orders by status: {}", status);
        return orderRepository.findByStatus(status, pageable)
                .map(this::mapToResponse);
    }

    private String generateOrderNumber() {
        Random random = new Random();
        int randomDigits = 1000 + random.nextInt(9000);
        return "ORD-" + System.currentTimeMillis() + "-" + randomDigits;
    }

    private BigDecimal calculateSubtotal(List<OrderItem> items) {
        return items.stream()
                .map(OrderItem::getTotalPrice)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal calculateTax(BigDecimal subtotal) {
        return subtotal.multiply(TAX_RATE).setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal calculateShipping(BigDecimal subtotal) {
        if (subtotal.compareTo(FREE_SHIPPING_THRESHOLD) >= 0) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        return SHIPPING_COST;
    }

    private BigDecimal calculateDiscount(BigDecimal subtotal, String couponCode) {
        if (couponCode == null || couponCode.isBlank()) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        if ("WELCOME10".equalsIgnoreCase(couponCode)) {
            return subtotal.multiply(new BigDecimal("0.10")).setScale(2, RoundingMode.HALF_UP);
        }
        return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
    }

    private OrderItem mapToOrderItem(OrderItemRequest itemRequest) {
        return OrderItem.builder()
                .productId(itemRequest.getProductId())
                .quantity(itemRequest.getQuantity())
                .unitPrice(BigDecimal.ZERO)
                .totalPrice(BigDecimal.ZERO)
                .build();
    }

    private ShippingAddress mapShippingAddress(ShippingAddressRequest request) {
        return ShippingAddress.builder()
                .fullName(request.getFullName())
                .phone(request.getPhone())
                .street(request.getStreet())
                .city(request.getCity())
                .state(request.getState())
                .zipCode(request.getZipCode())
                .country(request.getCountry())
                .build();
    }

    private BillingAddress mapBillingAddress(BillingAddressRequest request) {
        return BillingAddress.builder()
                .fullName(request.getFullName())
                .phone(request.getPhone())
                .street(request.getStreet())
                .city(request.getCity())
                .state(request.getState())
                .zipCode(request.getZipCode())
                .country(request.getCountry())
                .build();
    }

    private void publishOrderEvent(Order order, String eventType) {
        OrderEvent event = OrderEvent.builder()
                .eventType(eventType)
                .orderId(order.getId())
                .orderNumber(order.getOrderNumber())
                .userId(order.getUserId())
                .userEmail(order.getUserEmail())
                .status(order.getStatus())
                .paymentStatus(order.getPaymentStatus())
                .totalAmount(order.getTotalAmount())
                .currency(order.getCurrency())
                .timestamp(LocalDateTime.now())
                .build();
        eventPublisher.publishOrderEvent(event);
    }

    private OrderResponse mapToResponse(Order order) {
        return OrderResponse.builder()
                .id(order.getId())
                .orderNumber(order.getOrderNumber())
                .userId(order.getUserId())
                .userEmail(order.getUserEmail())
                .status(order.getStatus())
                .items(order.getItems())
                .subtotal(order.getSubtotal())
                .taxAmount(order.getTaxAmount())
                .shippingCost(order.getShippingCost())
                .discountAmount(order.getDiscountAmount())
                .totalAmount(order.getTotalAmount())
                .currency(order.getCurrency())
                .shippingAddress(order.getShippingAddress())
                .billingAddress(order.getBillingAddress())
                .paymentId(order.getPaymentId())
                .paymentMethod(order.getPaymentMethod())
                .paymentStatus(order.getPaymentStatus())
                .couponCode(order.getCouponCode())
                .notes(order.getNotes())
                .createdAt(order.getCreatedAt())
                .updatedAt(order.getUpdatedAt())
                .paidAt(order.getPaidAt())
                .shippedAt(order.getShippedAt())
                .deliveredAt(order.getDeliveredAt())
                .cancelledAt(order.getCancelledAt())
                .build();
    }
}
