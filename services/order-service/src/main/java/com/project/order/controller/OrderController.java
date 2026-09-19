package com.project.order.controller;

import com.project.common.constant.Permissions;
import com.project.common.constant.ServiceScopes;
import com.project.common.dto.ApiResponse;
import com.project.common.dto.ErrorResponse;
import com.project.common.security.CurrentUser;
import com.project.order.dto.OrderRequest;
import com.project.order.dto.OrderResponse;
import com.project.order.dto.OrderStatusUpdateRequest;
import com.project.order.model.OrderStatus;
import com.project.order.service.OrderService;
import com.project.order.validation.OrderAccessValidator;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/v1/orders")
@RequiredArgsConstructor
@Tag(name = "Orders", description = "Customer order lifecycle and administrative status management")
@SecurityScheme(name = "bearerAuth", type = SecuritySchemeType.HTTP, scheme = "bearer", bearerFormat = "JWT")
@SecurityRequirement(name = "bearerAuth")
@ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Validation failed",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Authentication required",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Operation forbidden",
                content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
})
public class OrderController {

    private final OrderService orderService;
    private final OrderAccessValidator orderAccessValidator;

    @PostMapping
    @PreAuthorize("hasAuthority('" + Permissions.ORDERS_CREATE + "')")
    @Operation(summary = "Create or replay an order")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "Order created or replayed")
    public ResponseEntity<ApiResponse<OrderResponse>> createOrder(
            @Valid @RequestBody OrderRequest request,
            @Parameter(description = "Optional replay key scoped to the authenticated customer; reuse returns the durable order")
            @RequestHeader(value = "X-Idempotency-Key", required = false) String idempotencyKey) {
        UUID userId = CurrentUser.requireId();
        String email = CurrentUser.email().orElse(null);
        OrderResponse response = orderService.createOrder(request, userId, email, idempotencyKey);
        return new ResponseEntity<>(ApiResponse.created(response), HttpStatus.CREATED);
    }

    /**
     * List endpoint: admins see everything, customers see only their own orders.
     */
    @GetMapping
    @PreAuthorize("hasAuthority('" + Permissions.ORDERS_READ + "')")
    public ResponseEntity<ApiResponse<Page<OrderResponse>>> getOrders(Pageable pageable) {
        Page<OrderResponse> page = CurrentUser.isAdmin()
                ? orderService.getAllOrders(pageable)
                : orderService.getUserOrders(CurrentUser.requireId(), pageable);
        return ResponseEntity.ok(ApiResponse.success(page));
    }

    @GetMapping("/{orderId}")
    @PreAuthorize("T(com.project.common.security.CurrentUser).isService() ? hasAuthority('" + ServiceScopes.AUTHORITY_ORDERS_READ + "') : hasAuthority('" + Permissions.ORDERS_READ + "')")
    @Operation(summary = "Get an owned order")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Order not found",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    public ResponseEntity<ApiResponse<OrderResponse>> getOrder(@PathVariable String orderId) {
        OrderResponse order = orderService.getOrder(orderId);
        orderAccessValidator.validateRead(order.userId());
        return ResponseEntity.ok(ApiResponse.success(order));
    }

    @GetMapping("/number/{orderNumber}")
    @PreAuthorize("T(com.project.common.security.CurrentUser).isService() ? hasAuthority('" + ServiceScopes.AUTHORITY_ORDERS_READ + "') : hasAuthority('" + Permissions.ORDERS_READ + "')")
    public ResponseEntity<ApiResponse<OrderResponse>> getOrderByNumber(@PathVariable String orderNumber) {
        OrderResponse order = orderService.getOrderByNumber(orderNumber);
        orderAccessValidator.validateRead(order.userId());
        return ResponseEntity.ok(ApiResponse.success(order));
    }

    /** Order status updates (shipped/delivered) are admin-only. */
    @PutMapping("/{orderId}/status")
    @PreAuthorize("hasAuthority('" + Permissions.ORDERS_UPDATE + "') and hasRole('ADMIN')")
    @Operation(summary = "Transition an order status as an administrator")
    public ResponseEntity<ApiResponse<OrderResponse>> updateOrderStatus(
            @PathVariable String orderId,
            @Valid @RequestBody OrderStatusUpdateRequest request) {
        return ResponseEntity.ok(ApiResponse.success(orderService.updateOrderStatus(orderId, request)));
    }

    @PostMapping("/{orderId}/cancel")
    @PreAuthorize("hasAuthority('" + Permissions.ORDERS_CANCEL + "')")
    public ResponseEntity<ApiResponse<OrderResponse>> cancelOrder(@PathVariable String orderId) {
        return ResponseEntity.ok(ApiResponse.success(
                orderService.cancelOrder(orderId, CurrentUser.requireId(), CurrentUser.isAdmin())));
    }

    @GetMapping("/status/{status}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Page<OrderResponse>>> getOrdersByStatus(
            @PathVariable OrderStatus status, Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.success(orderService.getOrdersByStatus(status, pageable)));
    }
}
