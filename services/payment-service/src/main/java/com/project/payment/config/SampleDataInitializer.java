package com.project.payment.config;

import com.project.common.sampledata.SampleIds;
import com.project.payment.model.Payment;
import com.project.payment.model.PaymentStatus;
import com.project.payment.repository.PaymentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Seeds payments that mirror the order-service seed (same ORD-SEED-* numbers,
 * same totals, same customers). Status mapping:
 *
 * <pre>
 *   Order PENDING   → Payment PENDING
 *   Order CONFIRMED → Payment COMPLETED
 *   Order SHIPPED   → Payment COMPLETED
 *   Order DELIVERED → Payment COMPLETED
 *   Order CANCELLED → Payment FAILED  (or none for some)
 *   Order REFUNDED  → Payment REFUNDED
 * </pre>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SampleDataInitializer implements CommandLineRunner {

    private final PaymentRepository paymentRepository;

    @Value("${seed.enabled:false}")
    private boolean seedEnabled;

    private static final BigDecimal TAX_RATE = new BigDecimal("0.18");
    private static final BigDecimal SHIPPING = new BigDecimal("49");
    private static final BigDecimal FREE_THRESHOLD = new BigDecimal("499");

    /** Mirrors the SPECS in order-service SampleDataInitializer (must stay in sync). */
    private static final List<Spec> SPECS = List.of(
            new Spec(1000, PaymentStatus.PENDING,    0, new int[]{0, 4}),
            new Spec(1001, PaymentStatus.PENDING,    1, new int[]{8}),
            new Spec(1002, PaymentStatus.PENDING,    2, new int[]{16, 20}),

            new Spec(1003, PaymentStatus.COMPLETED,  3, new int[]{1, 5}),
            new Spec(1004, PaymentStatus.COMPLETED,  4, new int[]{12}),
            new Spec(1005, PaymentStatus.COMPLETED,  5, new int[]{17, 21, 24}),

            new Spec(1006, PaymentStatus.COMPLETED,  6, new int[]{2}),
            new Spec(1007, PaymentStatus.COMPLETED,  7, new int[]{6, 13}),
            new Spec(1008, PaymentStatus.COMPLETED,  8, new int[]{18}),

            new Spec(1009, PaymentStatus.COMPLETED,  9,  new int[]{3, 9}),
            new Spec(1010, PaymentStatus.COMPLETED,  10, new int[]{14}),
            new Spec(1011, PaymentStatus.COMPLETED,  11, new int[]{22, 25}),

            new Spec(1012, PaymentStatus.FAILED,     12, new int[]{7}),
            // 1013 (CANCELLED w/ PENDING payment) — skip; never charged.

            new Spec(1014, PaymentStatus.REFUNDED,   14, new int[]{15, 23, 28})
    );

    @Override
    public void run(String... args) {
        if (!seedEnabled) return;
        if (paymentRepository.count() > 0) {
            log.info("Payments already populated ({}); skipping", paymentRepository.count());
            return;
        }

        for (Spec s : SPECS) {
            paymentRepository.save(buildPayment(s));
        }
        log.info("Seeded {} payments", SPECS.size());
    }

    private Payment buildPayment(Spec s) {
        var customer = SampleIds.CUSTOMERS.get(s.customerIdx() % SampleIds.CUSTOMERS.size());

        BigDecimal subtotal = BigDecimal.ZERO;
        for (int idx : s.productIdx()) {
            var p = SampleIds.PRODUCTS.get(idx % SampleIds.PRODUCTS.size());
            int qty = 1 + (idx % 2);
            subtotal = subtotal.add(BigDecimal.valueOf(p.priceRupees())
                    .multiply(BigDecimal.valueOf(qty)));
        }
        subtotal = subtotal.setScale(2, RoundingMode.HALF_UP);
        BigDecimal tax = subtotal.multiply(TAX_RATE).setScale(2, RoundingMode.HALF_UP);
        BigDecimal shipping = subtotal.compareTo(FREE_THRESHOLD) >= 0
                ? BigDecimal.ZERO.setScale(2) : SHIPPING;
        BigDecimal total = subtotal.add(tax).add(shipping);

        LocalDateTime created = LocalDateTime.now().minusDays(s.seq() - 1000L);

        Payment.PaymentBuilder b = Payment.builder()
                .id(UUID.randomUUID().toString())
                .paymentReference("PAY-SEED-" + s.seq())
                .orderId("ORD-SEED-" + s.seq())
                .orderNumber("ORD-SEED-" + s.seq())
                .userId(customer.id())
                .userEmail(customer.email())
                .status(s.status())
                .paymentMethod("card")
                .amount(total)
                .currency("INR")
                .description("Seed payment for order ORD-SEED-" + s.seq())
                .retryCount(0)
                .createdAt(created)
                .updatedAt(created);

        if (s.status() == PaymentStatus.COMPLETED || s.status() == PaymentStatus.REFUNDED) {
            b.transactionId("txn_seed_" + s.seq())
                    .gatewayResponse("{\"sandbox\":true,\"seed\":true}")
                    .completedAt(created.plusMinutes(2));
        } else if (s.status() == PaymentStatus.FAILED) {
            b.failureReason("Insufficient funds (sandbox)");
        }

        return b.build();
    }

    private record Spec(int seq, PaymentStatus status, int customerIdx, int[] productIdx) {}
}
