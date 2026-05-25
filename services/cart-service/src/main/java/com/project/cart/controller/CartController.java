package com.project.cart.controller;

import com.project.cart.dto.AddCartItemRequest;
import com.project.cart.dto.ApplyCouponRequest;
import com.project.cart.dto.CartResponse;
import com.project.cart.dto.UpdateQuantityRequest;
import com.project.cart.service.CartService;
import com.project.common.security.CurrentUser;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/cart")
@RequiredArgsConstructor
@PreAuthorize("isAuthenticated()")
public class CartController {

    private final CartService cartService;

    @GetMapping
    public ResponseEntity<CartResponse> getMyCart() {
        return ResponseEntity.ok(cartService.getMyCart(CurrentUser.requireId()));
    }

    @PostMapping("/items")
    public ResponseEntity<CartResponse> addItem(@Valid @RequestBody AddCartItemRequest req) {
        CartResponse cart = cartService.addItem(CurrentUser.requireId(), req);
        return ResponseEntity.status(HttpStatus.CREATED).body(cart);
    }

    @PatchMapping("/items/{productId}")
    public ResponseEntity<CartResponse> updateQuantity(@PathVariable String productId,
                                                       @Valid @RequestBody UpdateQuantityRequest req) {
        return ResponseEntity.ok(cartService.updateQuantity(CurrentUser.requireId(), productId, req));
    }

    @DeleteMapping("/items/{productId}")
    public ResponseEntity<CartResponse> removeItem(@PathVariable String productId) {
        return ResponseEntity.ok(cartService.removeItem(CurrentUser.requireId(), productId));
    }

    @DeleteMapping
    public ResponseEntity<CartResponse> clear() {
        return ResponseEntity.ok(cartService.clear(CurrentUser.requireId()));
    }

    @PostMapping("/coupon")
    public ResponseEntity<CartResponse> applyCoupon(@Valid @RequestBody ApplyCouponRequest req) {
        return ResponseEntity.ok(cartService.applyCoupon(CurrentUser.requireId(), req));
    }

    @DeleteMapping("/coupon")
    public ResponseEntity<CartResponse> removeCoupon() {
        return ResponseEntity.ok(cartService.removeCoupon(CurrentUser.requireId()));
    }
}
