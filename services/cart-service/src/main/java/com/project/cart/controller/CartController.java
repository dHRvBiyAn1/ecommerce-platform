package com.project.cart.controller;

import com.project.cart.application.mapper.CartApiMapper;
import com.project.cart.generated.api.CartApi;
import com.project.cart.generated.model.AddCartItemRequest;
import com.project.cart.generated.model.ApplyCouponRequest;
import com.project.cart.generated.model.CartResponse;
import com.project.cart.generated.model.UpdateQuantityRequest;
import com.project.cart.service.CartService;
import com.project.common.security.CurrentUser;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.headers.Header;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@PreAuthorize("isAuthenticated()")
@SecurityScheme(name = "bearerAuth", type = SecuritySchemeType.HTTP, scheme = "bearer", bearerFormat = "JWT")
@ApiResponse(responseCode = "401", description = "Authentication is required",
        headers = @Header(name = "WWW-Authenticate", description = "Bearer authentication challenge",
                schema = @Schema(type = "string")),
        content = @Content)
public class CartController implements CartApi {

    private final CartService cartService;
    private final CartApiMapper cartApiMapper;

    @Override
    public ResponseEntity<CartResponse> getMyCart() {
        return ResponseEntity.ok(cartApiMapper.toApi(cartService.getMyCart(CurrentUser.requireId())));
    }

    @Override
    public ResponseEntity<CartResponse> addCartItem(AddCartItemRequest request) {
        CartResponse cart = cartApiMapper.toApi(cartService.addItem(
                CurrentUser.requireId(), cartApiMapper.toDomain(request)));
        return ResponseEntity.status(HttpStatus.CREATED).body(cart);
    }

    @Override
    public ResponseEntity<CartResponse> updateCartItemQuantity(String productId, UpdateQuantityRequest request) {
        return ResponseEntity.ok(cartApiMapper.toApi(cartService.updateQuantity(
                CurrentUser.requireId(), productId, cartApiMapper.toDomain(request))));
    }

    @Override
    public ResponseEntity<CartResponse> removeCartItem(String productId) {
        return ResponseEntity.ok(cartApiMapper.toApi(cartService.removeItem(CurrentUser.requireId(), productId)));
    }

    @Override
    public ResponseEntity<CartResponse> clearCart() {
        return ResponseEntity.ok(cartApiMapper.toApi(cartService.clear(CurrentUser.requireId())));
    }

    @Override
    public ResponseEntity<CartResponse> applyCartCoupon(ApplyCouponRequest request) {
        return ResponseEntity.ok(cartApiMapper.toApi(cartService.applyCoupon(
                CurrentUser.requireId(), cartApiMapper.toDomain(request))));
    }

    @Override
    public ResponseEntity<CartResponse> removeCartCoupon() {
        return ResponseEntity.ok(cartApiMapper.toApi(cartService.removeCoupon(CurrentUser.requireId())));
    }
}
