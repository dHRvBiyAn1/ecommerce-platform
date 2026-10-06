package com.project.order.controller;

import com.project.common.constant.Permissions;
import com.project.common.constant.ServiceScopes;
import com.project.common.dto.ApiResponse;
import com.project.common.dto.ErrorResponse;
import com.project.common.security.CurrentUser;
import com.project.order.dto.OrderRequest;
import com.project.order.dto.OrderResponse;
import com.project.order.dto.OrderStatusUpdateRequest;
import com.project.order.application.mapper.OrderApiMapper;
import com.project.order.generated.api.OrdersApi;
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
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;
import java.time.ZoneOffset;
import java.time.OffsetDateTime;

@Slf4j
@RestController
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
public class OrderController implements OrdersApi {

    private final OrderService orderService;
    private final OrderAccessValidator orderAccessValidator;
    private final OrderApiMapper orderApiMapper;

    @Override
    @PreAuthorize("hasAuthority('" + Permissions.ORDERS_CREATE + "')")
    public ResponseEntity<com.project.order.generated.model.ApiResponseOrderResponse> createOrder(
            com.project.order.generated.model.OrderRequest request, String idempotencyKey) {
        UUID userId = CurrentUser.requireId();
        String email = CurrentUser.email().orElse(null);
        OrderResponse response = orderService.createOrder(orderApiMapper.toDomain(request), userId, email, idempotencyKey);
        return new ResponseEntity<>(envelope(ApiResponse.created(response)), HttpStatus.CREATED);
    }

    /**
     * List endpoint: admins see everything, customers see only their own orders.
     */
    @Override
    @PreAuthorize("hasAuthority('" + Permissions.ORDERS_READ + "')")
    public ResponseEntity<com.project.order.generated.model.ApiResponsePageOrderResponse> getOrders(
            Pageable pageable) {
        Page<OrderResponse> page = CurrentUser.isAdmin()
                ? orderService.getAllOrders(pageable)
                : orderService.getUserOrders(CurrentUser.requireId(), pageable);
        return ResponseEntity.ok(envelopePage(ApiResponse.success(page)));
    }

    @Override
    @PreAuthorize("T(com.project.common.security.CurrentUser).isService() ? hasAuthority('" + ServiceScopes.AUTHORITY_ORDERS_READ + "') : hasAuthority('" + Permissions.ORDERS_READ + "')")
    @Operation(summary = "Get an owned order")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Order not found",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    public ResponseEntity<com.project.order.generated.model.ApiResponseOrderResponse> getOrder(String orderId) {
        OrderResponse order = orderService.getOrder(orderId);
        orderAccessValidator.validateRead(order.userId());
        return ResponseEntity.ok(envelope(ApiResponse.success(order)));
    }

    @Override
    @PreAuthorize("T(com.project.common.security.CurrentUser).isService() ? hasAuthority('" + ServiceScopes.AUTHORITY_ORDERS_READ + "') : hasAuthority('" + Permissions.ORDERS_READ + "')")
    public ResponseEntity<com.project.order.generated.model.ApiResponseOrderResponse> getOrderByNumber(String orderNumber) {
        OrderResponse order = orderService.getOrderByNumber(orderNumber);
        orderAccessValidator.validateRead(order.userId());
        return ResponseEntity.ok(envelope(ApiResponse.success(order)));
    }

    /** Order status updates (shipped/delivered) are admin-only. */
    @Override
    @PreAuthorize("hasAuthority('" + Permissions.ORDERS_UPDATE + "') and hasRole('ADMIN')")
    @Operation(summary = "Transition an order status as an administrator")
    public ResponseEntity<com.project.order.generated.model.ApiResponseOrderResponse> updateOrderStatus(
            String orderId, com.project.order.generated.model.OrderStatusUpdateRequest request) {
        return ResponseEntity.ok(envelope(ApiResponse.success(orderService.updateOrderStatus(
                orderId, orderApiMapper.toDomain(request)))));
    }

    @Override
    @PreAuthorize("hasAuthority('" + Permissions.ORDERS_CANCEL + "')")
    public ResponseEntity<com.project.order.generated.model.ApiResponseOrderResponse> cancelOrder(String orderId) {
        return ResponseEntity.ok(envelope(ApiResponse.success(
                orderService.cancelOrder(orderId, CurrentUser.requireId(), CurrentUser.isAdmin()))));
    }

    @Override
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<com.project.order.generated.model.ApiResponsePageOrderResponse> getOrdersByStatus(
            com.project.order.generated.model.OrderStatus status, Pageable pageable) {
        OrderStatus orderStatus = OrderStatus.valueOf(status.getValue());
        return ResponseEntity.ok(envelopePage(ApiResponse.success(orderService.getOrdersByStatus(
                orderStatus, pageable))));
    }

    private com.project.order.generated.model.ApiResponseOrderResponse envelope(ApiResponse<OrderResponse> response) {
        return new com.project.order.generated.model.ApiResponseOrderResponse()
                .status(response.getStatus()).message(response.getMessage()).traceId(response.getTraceId())
                .timestamp(OffsetDateTime.ofInstant(response.getTimestamp(), ZoneOffset.UTC))
                .data(orderApiMapper.toApi(response.getData()));
    }

    private com.project.order.generated.model.ApiResponsePageOrderResponse envelopePage(
            ApiResponse<Page<OrderResponse>> response) {
        return new com.project.order.generated.model.ApiResponsePageOrderResponse()
                .status(response.getStatus()).message(response.getMessage()).traceId(response.getTraceId())
                .timestamp(OffsetDateTime.ofInstant(response.getTimestamp(), ZoneOffset.UTC))
                .data(orderApiMapper.toApi(response.getData()));
    }

}
