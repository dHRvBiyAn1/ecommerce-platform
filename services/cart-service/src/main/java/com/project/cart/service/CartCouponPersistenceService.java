package com.project.cart.service;

import com.project.cart.model.Cart;
import com.project.cart.repository.CartRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

@Service
@RequiredArgsConstructor
public class CartCouponPersistenceService {

    private final CartRepository cartRepository;

    @Transactional
    public Cart applyValidatedCoupon(Cart cart, String code, BigDecimal discountAmount) {
        cart.setAppliedCouponCode(code);
        cart.setAppliedDiscountAmount(discountAmount);
        return cartRepository.save(cart);
    }
}
