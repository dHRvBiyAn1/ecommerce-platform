package com.project.cart.service;

import com.project.cart.application.mapper.CartMapper;
import com.project.cart.client.CouponClient;
import com.project.cart.client.ProductClient;
import com.project.cart.exception.ProductServiceUnavailableException;
import com.project.cart.generated.integration.coupon.model.ValidateCouponRequest;
import com.project.cart.generated.integration.coupon.model.ValidateCouponResponse;
import com.project.cart.generated.integration.product.model.ProductResponse;
import com.project.cart.generated.model.AddCartItemRequest;
import com.project.cart.generated.model.ApplyCouponRequest;
import com.project.cart.generated.model.CartResponse;
import com.project.cart.generated.model.UpdateQuantityRequest;
import com.project.cart.model.Cart;
import com.project.cart.model.CartItem;
import com.project.cart.repository.CartRepository;
import com.project.common.exception.BusinessException;
import com.project.common.exception.ResourceNotFoundException;
import com.project.common.exception.ValidationException;
import feign.FeignException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Locale;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Cart business logic. Each operation is idempotent at the request level — adding the same
 * productId twice updates the existing line; deleting a non-existent productId is a no-op.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CartService {

  private final CartRepository cartRepository;
  private final CouponClient couponClient;
  private final ProductClient productClient;
  private final CartMapper cartMapper;
  private final CartCouponPersistenceService cartCouponPersistenceService;

  @Transactional(readOnly = true)
  public CartResponse getMyCart(UUID userId) {
    Cart cart = cartRepository.findByUserId(userId).orElseGet(() -> emptyCart(userId));
    return toResponse(cart);
  }

  @Transactional
  public CartResponse addItem(UUID userId, AddCartItemRequest req) {
    ProductResponse product;
    try {
      product = productClient.getProduct(req.getProductId());
    } catch (ResourceNotFoundException | ProductServiceUnavailableException e) {
      throw e;
    } catch (FeignException.NotFound e) {
      throw new ResourceNotFoundException("Product", req.getProductId());
    } catch (RuntimeException e) {
      throw new ProductServiceUnavailableException(e);
    }
    if (product == null)
      throw new ProductServiceUnavailableException(
          new IllegalStateException("Product service returned no product"));
    if (!Boolean.TRUE.equals(product.getActive()))
      throw new ResourceNotFoundException("Product", req.getProductId());
    Cart cart = cartRepository.findByUserId(userId).orElseGet(() -> emptyCart(userId));
    if (cart.getCurrency() == null) {
      cart.setCurrency("INR");
    }

    CartItem existing = findItem(cart, req.getProductId());
    if (existing != null) {
      existing.setQuantity(
          existing.getQuantity() + (req.getQuantity() == null ? 0 : req.getQuantity()));
      copyProductSnapshot(existing, product);
    } else {
      CartItem item =
          CartItem.builder()
              .productId(product.getId())
              .quantity(req.getQuantity() == null ? 0 : req.getQuantity())
              .build();
      copyProductSnapshot(item, product);
      cart.getItems().add(item);
    }
    invalidateAppliedCoupon(cart, "items changed");
    return toResponse(cartRepository.save(cart));
  }

  @Transactional
  public CartResponse updateQuantity(UUID userId, String productId, UpdateQuantityRequest req) {
    Cart cart =
        cartRepository
            .findByUserId(userId)
            .orElseThrow(() -> new ResourceNotFoundException("Cart", userId.toString()));
    CartItem item = findItem(cart, productId);
    if (item == null) throw new ResourceNotFoundException("Cart item", productId);

    if (req.getQuantity() == null || req.getQuantity() == 0) {
      cart.getItems().remove(item);
    } else {
      item.setQuantity(req.getQuantity());
    }
    invalidateAppliedCoupon(cart, "quantity updated");
    return toResponse(cartRepository.save(cart));
  }

  @Transactional
  public CartResponse removeItem(UUID userId, String productId) {
    Cart cart =
        cartRepository
            .findByUserId(userId)
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
   * Apply (or replace) a coupon. Calls coupon-service to validate against the current subtotal; if
   * invalid, returns a 422 with the rejection reason (caller-facing message).
   */
  public CartResponse applyCoupon(UUID userId, ApplyCouponRequest req) {
    Cart cart =
        cartRepository
            .findByUserId(userId)
            .orElseThrow(() -> new ResourceNotFoundException("Cart", userId.toString()));
    if (cart.getItems().isEmpty()) {
      throw new ValidationException("Cannot apply a coupon to an empty cart");
    }

    BigDecimal subtotal = subtotal(cart);
    String normalizedCode =
        req.getCode() == null ? null : req.getCode().trim().toUpperCase(Locale.ROOT);
    ValidateCouponResponse v;
    try {
      v =
          couponClient.validate(
              new ValidateCouponRequest(
                  normalizedCode,
                  userId,
                  subtotal,
                  cart.getCurrency() != null ? cart.getCurrency() : "INR"));
    } catch (BusinessException e) {
      throw e;
    } catch (Exception e) {
      log.warn("Coupon validation call failed for code {}: {}", req.getCode(), e.getMessage());
      throw new BusinessException(
          org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE,
          "COUPON_UNAVAILABLE",
          "Coupon validation is currently unavailable. Try again shortly.");
    }
    if (v == null) {
      throw new BusinessException(
          org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE,
          "COUPON_UNAVAILABLE",
          "Coupon validation is currently unavailable. Try again shortly.");
    }
    if (!Boolean.TRUE.equals(v.getValid())) {
      throw new ValidationException(v.getReason() != null ? v.getReason() : "Coupon is not valid");
    }
    String responseCode = v.getCode() == null ? null : v.getCode().trim().toUpperCase(Locale.ROOT);
    if (normalizedCode == null
        || responseCode == null
        || !normalizedCode.equals(responseCode)
        || v.getDiscountAmount() == null
        || v.getDiscountAmount().signum() <= 0
        || v.getDiscountAmount().compareTo(subtotal) > 0) {
      throw new ValidationException("Coupon validation response is invalid");
    }
    return toResponse(
        cartCouponPersistenceService.applyValidatedCoupon(
            cart, v.getCode(), v.getDiscountAmount()));
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
      log.debug(
          "Invalidating coupon {} on cart {}: {}",
          cart.getAppliedCouponCode(),
          cart.getId(),
          reason);
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
    BigDecimal discount =
        cart.getAppliedDiscountAmount() == null
            ? BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP)
            : cart.getAppliedDiscountAmount();
    BigDecimal total =
        subtotal.subtract(discount).max(BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP);
    int itemCount = cart.getItems().stream().mapToInt(CartItem::getQuantity).sum();
    return cartMapper.toResponse(cart, subtotal, total, discount, itemCount);
  }

  private void copyProductSnapshot(CartItem item, ProductResponse product) {
    item.setSku(product.getSku());
    item.setProductName(product.getName());
    item.setImageUrl(
        product.getImageUrls() == null || product.getImageUrls().isEmpty()
            ? null
            : product.getImageUrls().getFirst());
    item.setUnitPrice(product.getPrice());
  }
}
