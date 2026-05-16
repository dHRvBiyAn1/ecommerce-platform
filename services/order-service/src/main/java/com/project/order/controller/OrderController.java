package com.project.order.controller;

import com.project.order.dto.OrderRequest;
import com.project.order.dto.OrderResponse;
import com.project.order.dto.OrderStatusUpdateRequest;
import com.project.order.model.OrderStatus;
import com.project.order.service.OrderService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/v1/orders")
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;

    @PostMapping
    public ResponseEntity<OrderResponse> createOrder(
            @Valid @RequestBody OrderRequest request,
            Authentication authentication) {
        UUID userId = getUserId(authentication);
        String userEmail = getUserEmail(authentication);
        log.info("Creating order for user: {}", userId);
        OrderResponse response = orderService.createOrder(request, userId, userEmail);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping
    public ResponseEntity<Page<OrderResponse>> getOrders(
            Authentication authentication,
            Pageable pageable) {
        if (isAdmin(authentication)) {
            log.debug("Admin fetching all orders");
            return ResponseEntity.ok(orderService.getAllOrders(pageable));
        }
        UUID userId = getUserId(authentication);
        log.debug("Fetching orders for user: {}", userId);
        return ResponseEntity.ok(orderService.getUserOrders(userId, pageable));
    }

    @GetMapping("/{orderId}")
    public ResponseEntity<OrderResponse> getOrder(@PathVariable String orderId) {
        log.debug("Fetching order: {}", orderId);
        OrderResponse response = orderService.getOrder(orderId);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/number/{orderNumber}")
    public ResponseEntity<OrderResponse> getOrderByNumber(@PathVariable String orderNumber) {
        log.debug("Fetching order by number: {}", orderNumber);
        OrderResponse response = orderService.getOrderByNumber(orderNumber);
        return ResponseEntity.ok(response);
    }

    @PutMapping("/{orderId}/status")
    public ResponseEntity<OrderResponse> updateOrderStatus(
            @PathVariable String orderId,
            @Valid @RequestBody OrderStatusUpdateRequest request) {
        log.info("Updating status for order: {} to {}", orderId, request.getStatus());
        OrderResponse response = orderService.updateOrderStatus(orderId, request);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{orderId}/cancel")
    public ResponseEntity<OrderResponse> cancelOrder(
            @PathVariable String orderId,
            Authentication authentication) {
        UUID userId = getUserId(authentication);
        log.info("Cancelling order: {} by user: {}", orderId, userId);
        OrderResponse response = orderService.cancelOrder(orderId, userId);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/status/{status}")
    public ResponseEntity<Page<OrderResponse>> getOrdersByStatus(
            @PathVariable OrderStatus status,
            Pageable pageable) {
        log.debug("Fetching orders by status: {}", status);
        return ResponseEntity.ok(orderService.getOrdersByStatus(status, pageable));
    }

    private UUID getUserId(Authentication authentication) {
        if (authentication == null || authentication.getPrincipal() == null) {
            return null;
        }
        try {
            return UUID.fromString(authentication.getPrincipal().toString());
        } catch (IllegalArgumentException e) {
            log.warn("Invalid user ID format: {}", authentication.getPrincipal());
            return null;
        }
    }

    private String getUserEmail(Authentication authentication) {
        if (authentication == null || authentication.getCredentials() == null) {
            return null;
        }
        return authentication.getCredentials().toString();
    }

    private boolean isAdmin(Authentication authentication) {
        if (authentication == null) {
            return false;
        }
        return authentication.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
    }
}
