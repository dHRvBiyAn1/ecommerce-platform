package com.project.cart.config;

import com.project.cart.model.Cart;
import com.project.cart.model.CartItem;
import com.project.cart.repository.CartRepository;
import com.project.common.sampledata.SampleIds;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * Seeds 4 pre-filled carts so the storefront's "your cart" view isn't empty
 * for the first few sample customers. Idempotent on collection count.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SampleDataInitializer implements CommandLineRunner {

    private final CartRepository cartRepository;

    @Value("${seed.enabled:false}")
    private boolean seedEnabled;

    @Override
    public void run(String... args) {
        if (!seedEnabled) return;
        if (cartRepository.count() > 0) {
            log.info("Carts already populated ({}); skipping", cartRepository.count());
            return;
        }

        record Spec(int customerIdx, int[] productIdx, int[] qty) {}

        List<Spec> specs = List.of(
                new Spec(0, new int[]{4, 0},      new int[]{1, 2}),   // Amelia: tote + tee×2
                new Spec(1, new int[]{8, 13},     new int[]{1, 1}),   // Noah: throw + paring knife
                new Spec(2, new int[]{16, 21, 27}, new int[]{2, 1, 3}), // Olivia: 3 items
                new Spec(3, new int[]{2},          new int[]{1})       // Liam: linen overshirt
        );

        for (Spec s : specs) {
            var customer = SampleIds.CUSTOMERS.get(s.customerIdx());
            List<CartItem> items = new ArrayList<>();
            for (int j = 0; j < s.productIdx().length; j++) {
                var p = SampleIds.PRODUCTS.get(s.productIdx()[j]);
                items.add(CartItem.builder()
                        .productId(p.id()).sku(p.sku()).productName(p.name())
                        .imageUrl(null)
                        .unitPrice(BigDecimal.valueOf(p.priceRupees()))
                        .quantity(s.qty()[j])
                        .build());
            }
            Cart cart = Cart.builder()
                    .userId(customer.id())
                    .items(items)
                    .currency("INR")
                    .build();
            cartRepository.save(cart);
        }
        log.info("Seeded {} sample carts", specs.size());
    }
}
