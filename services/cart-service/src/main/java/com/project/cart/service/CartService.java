package com.project.cart.service;

import com.project.cart.client.CouponClient;
import com.project.cart.client.CouponValidationRequest;
import com.project.cart.client.CouponValidationResponse;
import com.project.cart.client.ProductClient;
import com.project.cart.client.ProductSummary;
import com.project.cart.application.mapper.CartMapper;
import com.project.cart.exception.ProductServiceUnavailableException;
import feign.FeignException;
import com.project.cart.dto.AddCartItemRequest;
import com.project.cart.dto.ApplyCouponRequest;
import com.project.cart.dto.CartResponse;
import com.project.cart.dto.UpdateQuantityRequest;
import com.project.cart.model.Cart;
import com.project.cart.model.CartItem;
import com.project.cart.repository.CartRepository;
import com.project.common.exception.ResourceNotFoundException;
import com.project.common.exception.ValidationException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.UUID;

/**
 * Cart business logic. Each operation is idempotent at the request level —
 * adding the same productId twice updates the existing line; deleting a
 * non-existent productId is a no-op.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CartService {

    private final CartRepository cartRepository;
    private final CouponClient couponClient;
    private final ProductClient productClient;
    private final CartMapper cartMapper;

    @Transactional(readOnly = true)
    public CartResponse getMyCart(UUID userId) {
        Cart cart = cartRepository.findByUserId(userId).orElseGet(() -> emptyCart(userId));
        return toResponse(cart);
    }

    @Transactional
    public CartResponse addItem(UUID userId, AddCartItemRequest req) {
        ProductSummary product;
        try {
            product = productClient.getProduct(req.productId());
        } catch (ResourceNotFoundException | ProductServiceUnavailableException e) {
            throw e;
        } catch (FeignException.NotFound e) {
            throw new ResourceNotFoundException("Product", req.productId());
        } catch (RuntimeException e) {
            throw new ProductServiceUnavailableException(e);
        }
        if (product == null) throw new ProductServiceUnavailableException(
                new IllegalStateException("Product service returned no product"));
        if (!product.active()) throw new ResourceNotFoundException("Product", req.productId());
        Cart cart = cartRepository.findByUserId(userId).orElseGet(() -> emptyCart(userId));
        if (cart.getCurrency() == null) {
            cart.setCurrency("INR");
        }

        CartItem existing = findItem(cart, req.productId());
        if (existing != null) {
            existing.setQuantity(existing.getQuantity() + req.quantity());
            copyProductSnapshot(existing, product);
        } else {
            CartItem item = CartItem.builder().productId(product.id()).quantity(req.quantity()).build();
            copyProductSnapshot(item, product);
            cart.getItems().add(item);
        }
        invalidateAppliedCoupon(cart, "items changed");
        return toResponse(cartRepository.save(cart));
    }

    @Transactional
    public CartResponse updateQuantity(UUID userId, String productId, UpdateQuantityRequest req) {
        Cart cart = cartRepository.findByUserId(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Cart", userId.toString()));
        CartItem item = findItem(cart, productId);
        if (item == null) throw new ResourceNotFoundException("Cart item", productId);

        if (req.quantity() == 0) {
            cart.getItems().remove(item);
        } else {
            item.setQuantity(req.quantity());
        }
        invalidateAppliedCoupon(cart, "quantity updated");
        return toResponse(cartRepository.save(cart));
    }

    @Transactional
    public CartResponse removeItem(UUID userId, String productId) {
        Cart cart = cartRepository.findByUserId(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Cart", userId.toString()));
        boolean removed = cart.getItems().removeIf(i -> i.getProductId().equals(productId));
        if (!removed) {
            // Idempotent: 200 OK even if the item wasn't there.
            return toResponse(cart);
        }
        invalidateAppliedCoupon(cart, "item removed");
        return toResponse(cartRepository.save(cart));
    }

    @Transactional
    public CartResponse clear(UUID userId) {
        Cart cart = cartRepository.findByUserId(userId).orElseGet(() -> emptyCart(userId));
        cart.setItems(new ArrayList<>());
        cart.setAppliedCouponCode(null);
        cart.setAppliedDiscountAmount(null);
        return toResponse(cartRepository.save(cart));
    }

    /**
     * Apply (or replace) a coupon. Calls coupon-service to validate against
     * the current subtotal; if invalid, returns a 422 with the rejection
     * reason (caller-facing message).
     */
    @Transactional
    public CartResponse applyCoupon(UUID userId, ApplyCouponRequest req) {
        Cart cart = cartRepository.findByUserId(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Cart", userId.toString()));
        if (cart.getItems().isEmpty()) {
            throw new ValidationException("Cannot apply a coupon to an empty cart");
        }

        BigDecimal subtotal = subtotal(cart);
        CouponValidationResponse v;
        try {
            v = couponClient.validate(CouponValidationRequest.builder()
                    .code(req.code())
                    .userId(userId)
                    .subtotal(subtotal)
                    .currency(cart.getCurrency() != null ? cart.getCurrency() : "INR")
                    .build());
        } catch (Exception e) {
            log.warn("Coupon validation call failed for code {}: {}", req.code(), e.getMessage());
            throw new ValidationException("Coupon validation is currently unavailable. Try again shortly.");
        }
        if (!v.isValid()) {
            throw new ValidationException(v.getReason() != null ? v.getReason() : "Coupon is not valid");
        }

        cart.setAppliedCouponCode(v.getCode());
        cart.setAppliedDiscountAmount(v.getDiscountAmount());
        return toResponse(cartRepository.save(cart));
    }

    @Transactional
    public CartResponse removeCoupon(UUID userId) {
        Cart cart = cartRepository.findByUserId(userId).orElseGet(() -> emptyCart(userId));
        cart.setAppliedCouponCode(null);
        cart.setAppliedDiscountAmount(null);
        return toResponse(cartRepository.save(cart));
    }

    // ------------------------------------------------------------------
    // helpers
    // ------------------------------------------------------------------

    private Cart emptyCart(UUID userId) {
        return Cart.builder().userId(userId).items(new ArrayList<>()).build();
    }

    private CartItem findItem(Cart cart, String productId) {
        return cart.getItems().stream()
                .filter(i -> i.getProductId().equals(productId))
                .findFirst()
                .orElse(null);
    }

    private void invalidateAppliedCoupon(Cart cart, String reason) {
        if (cart.getAppliedCouponCode() != null) {
            log.debug("Invalidating coupon {} on cart {}: {}",
                    cart.getAppliedCouponCode(), cart.getId(), reason);
            cart.setAppliedCouponCode(null);
            cart.setAppliedDiscountAmount(null);
        }
    }

    private BigDecimal subtotal(Cart cart) {
        return cart.getItems().stream()
                .map(i -> i.getUnitPrice().multiply(BigDecimal.valueOf(i.getQuantity())))
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);
    }

    private CartResponse toResponse(Cart cart) {
        BigDecimal subtotal = subtotal(cart);
        BigDecimal discount = cart.getAppliedDiscountAmount() == null
                ? BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP)
                : cart.getAppliedDiscountAmount();
        BigDecimal total = subtotal.subtract(discount).max(BigDecimal.ZERO)
                .setScale(2, RoundingMode.HALF_UP);
        int itemCount = cart.getItems().stream().mapToInt(CartItem::getQuantity).sum();
        return cartMapper.toResponse(cart, subtotal, total, discount, itemCount);
    }

    private void copyProductSnapshot(CartItem item, ProductSummary product) {
        item.setSku(product.sku());
        item.setProductName(product.name());
        item.setImageUrl(product.imageUrls() == null || product.imageUrls().isEmpty()
                ? null : product.imageUrls().get(0));
        item.setUnitPrice(product.price());
    }
}
