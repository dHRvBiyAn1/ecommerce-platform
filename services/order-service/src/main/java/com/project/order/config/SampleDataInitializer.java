package com.project.order.config;

import com.project.common.sampledata.SampleIds;
import com.project.order.model.BillingAddress;
import com.project.order.model.Order;
import com.project.order.model.OrderItem;
import com.project.order.model.OrderStatus;
import com.project.order.model.PaymentStatus;
import com.project.order.model.ShippingAddress;
import com.project.order.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Seeds 15 orders across the full status range so the admin dashboard has
 * something to look at and several customers' "My orders" pages are populated.
 *
 * <p>Distribution: 3 PENDING, 3 CONFIRMED, 3 SHIPPED, 3 DELIVERED,
 * 2 CANCELLED, 1 REFUNDED.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SampleDataInitializer implements CommandLineRunner {

    private final OrderRepository orderRepository;

    @Value("${seed.enabled:false}")
    private boolean seedEnabled;

    private static final BigDecimal TAX_RATE = new BigDecimal("0.18");
    private static final BigDecimal SHIPPING = new BigDecimal("49");
    private static final BigDecimal FREE_THRESHOLD = new BigDecimal("499");

    private static final List<Spec> SPECS = List.of(
            new Spec(OrderStatus.PENDING,    PaymentStatus.PENDING,   0, new int[]{0, 4}),
            new Spec(OrderStatus.PENDING,    PaymentStatus.PENDING,   1, new int[]{8}),
            new Spec(OrderStatus.PENDING,    PaymentStatus.PENDING,   2, new int[]{16, 20}),

            new Spec(OrderStatus.CONFIRMED,  PaymentStatus.COMPLETED, 3, new int[]{1, 5}),
            new Spec(OrderStatus.CONFIRMED,  PaymentStatus.COMPLETED, 4, new int[]{12}),
            new Spec(OrderStatus.CONFIRMED,  PaymentStatus.COMPLETED, 5, new int[]{17, 21, 24}),

            new Spec(OrderStatus.SHIPPED,    PaymentStatus.COMPLETED, 6, new int[]{2}),
            new Spec(OrderStatus.SHIPPED,    PaymentStatus.COMPLETED, 7, new int[]{6, 13}),
            new Spec(OrderStatus.SHIPPED,    PaymentStatus.COMPLETED, 8, new int[]{18}),

            new Spec(OrderStatus.DELIVERED,  PaymentStatus.COMPLETED, 9,  new int[]{3, 9}),
            new Spec(OrderStatus.DELIVERED,  PaymentStatus.COMPLETED, 10, new int[]{14}),
            new Spec(OrderStatus.DELIVERED,  PaymentStatus.COMPLETED, 11, new int[]{22, 25}),

            new Spec(OrderStatus.CANCELLED,  PaymentStatus.FAILED,    12, new int[]{7}),
            new Spec(OrderStatus.CANCELLED,  PaymentStatus.PENDING,   13, new int[]{19, 26}),

            new Spec(OrderStatus.REFUNDED,   PaymentStatus.REFUNDED,  14, new int[]{15, 23, 28})
    );

    @Override
    public void run(String... args) {
        if (!seedEnabled) return;
        if (orderRepository.count() > 0) {
            log.info("Orders already populated ({}); skipping", orderRepository.count());
            return;
        }

        int seq = 1000;
        for (Spec s : SPECS) {
            Order order = buildOrder(s, seq++);
            orderRepository.save(order);
        }
        log.info("Seeded {} orders across {} status states", SPECS.size(),
                SPECS.stream().map(Spec::status).distinct().count());
    }

    private Order buildOrder(Spec s, int seq) {
        var customer = SampleIds.CUSTOMERS.get(s.customerIdx() % SampleIds.CUSTOMERS.size());

        List<OrderItem> items = new ArrayList<>();
        for (int idx : s.productIdx()) {
            var p = SampleIds.PRODUCTS.get(idx % SampleIds.PRODUCTS.size());
            BigDecimal unit = BigDecimal.valueOf(p.priceRupees());
            int qty = 1 + (idx % 2);
            BigDecimal lineTotal = unit.multiply(BigDecimal.valueOf(qty)).setScale(2, RoundingMode.HALF_UP);
            items.add(OrderItem.builder()
                    .productId(p.id())
                    .sku(p.sku())
                    .productName(p.name())
                    .imageUrl(null)
                    .quantity(qty)
                    .unitPrice(unit)
                    .discountAmount(BigDecimal.ZERO)
                    .totalPrice(lineTotal)
                    .build());
        }

        BigDecimal subtotal = items.stream().map(OrderItem::getTotalPrice)
                .reduce(BigDecimal.ZERO, BigDecimal::add).setScale(2, RoundingMode.HALF_UP);
        BigDecimal tax = subtotal.multiply(TAX_RATE).setScale(2, RoundingMode.HALF_UP);
        BigDecimal shipping = subtotal.compareTo(FREE_THRESHOLD) >= 0
                ? BigDecimal.ZERO.setScale(2) : SHIPPING;
        BigDecimal total = subtotal.add(tax).add(shipping);

        LocalDateTime created = LocalDateTime.now().minusDays(seq - 1000L);

        Order o = new Order();
        o.setId(UUID.randomUUID().toString());
        o.setOrderNumber("ORD-SEED-" + seq);
        o.setUserId(customer.id());
        o.setUserEmail(customer.email());
        o.setStatus(s.status());
        o.setPaymentStatus(s.payment());
        o.setPaymentMethod("card");
        o.setItems(items);
        o.setSubtotal(subtotal);
        o.setTaxAmount(tax);
        o.setShippingCost(shipping);
        o.setDiscountAmount(BigDecimal.ZERO);
        o.setTotalAmount(total);
        o.setCurrency("INR");
        o.setShippingAddress(sampleShipping(customer.displayName()));
        o.setBillingAddress(sampleBilling(customer.displayName()));
        o.setCreatedAt(created);
        o.setUpdatedAt(created);

        if (s.payment() == PaymentStatus.COMPLETED || s.payment() == PaymentStatus.REFUNDED) {
            o.setPaidAt(created.plusMinutes(2));
        }
        if (s.status() == OrderStatus.SHIPPED || s.status() == OrderStatus.DELIVERED) {
            o.setShippedAt(created.plusDays(1));
        }
        if (s.status() == OrderStatus.DELIVERED) {
            o.setDeliveredAt(created.plusDays(3));
        }
        if (s.status() == OrderStatus.CANCELLED) {
            o.setCancelledAt(created.plusHours(1));
        }
        return o;
    }

    private ShippingAddress sampleShipping(String name) {
        return ShippingAddress.builder()
                .fullName(name).phone("+919812345678")
                .street("42 Linking Road").city("Mumbai")
                .state("MH").zipCode("400050").country("IN")
                .build();
    }

    private BillingAddress sampleBilling(String name) {
        return BillingAddress.builder()
                .fullName(name).phone("+919812345678")
                .street("42 Linking Road").city("Mumbai")
                .state("MH").zipCode("400050").country("IN")
                .build();
    }

    private record Spec(OrderStatus status, PaymentStatus payment,
                        int customerIdx, int[] productIdx) {}
}
