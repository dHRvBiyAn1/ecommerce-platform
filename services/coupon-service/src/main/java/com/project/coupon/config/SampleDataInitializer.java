package com.project.coupon.config;

import com.project.coupon.entity.Coupon;
import com.project.coupon.entity.DiscountType;
import com.project.coupon.repository.CouponRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Seeds 10 sample coupons covering the realistic range:
 * percent and flat, with/without min-order, with/without per-user-limit,
 * one inactive, one expired, one not-yet-active.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SampleDataInitializer implements CommandLineRunner {

    private final CouponRepository couponRepository;

    @Value("${seed.enabled:false}")
    private boolean seedEnabled;

    @Override
    public void run(String... args) {
        if (!seedEnabled) return;
        if (couponRepository.count() > 0) {
            log.info("Coupons already populated ({}); skipping", couponRepository.count());
            return;
        }

        LocalDateTime now = LocalDateTime.now();
        LocalDateTime monthAgo = now.minusMonths(1);
        LocalDateTime monthOut = now.plusMonths(1);
        LocalDateTime yearOut  = now.plusYears(1);
        LocalDateTime weekOut  = now.plusWeeks(1);

        List<Coupon> seed = List.of(
                // 1. Welcome coupon — small flat discount, per-user-limit 1
                Coupon.builder().code("WELCOME50").description("Rs.50 off your first order")
                        .discountType(DiscountType.FIXED).discountValue(new BigDecimal("50.00"))
                        .minOrderAmount(new BigDecimal("499.00"))
                        .currency("INR").validFrom(monthAgo).validUntil(yearOut)
                        .usageLimit(null).perUserLimit(1).active(true).build(),

                // 2. Sitewide 10% off, capped at Rs.500
                Coupon.builder().code("SAVE10").description("10% off, up to Rs.500")
                        .discountType(DiscountType.PERCENT).discountValue(new BigDecimal("10.00"))
                        .maxDiscountAmount(new BigDecimal("500.00"))
                        .currency("INR").validFrom(monthAgo).validUntil(monthOut)
                        .usageLimit(1000).perUserLimit(3).active(true).build(),

                // 3. Big-spender 20% off (min Rs.5000)
                Coupon.builder().code("SAVE20").description("20% off orders above Rs.5,000")
                        .discountType(DiscountType.PERCENT).discountValue(new BigDecimal("20.00"))
                        .maxDiscountAmount(new BigDecimal("2000.00"))
                        .minOrderAmount(new BigDecimal("5000.00"))
                        .currency("INR").validFrom(monthAgo).validUntil(monthOut)
                        .usageLimit(500).perUserLimit(2).active(true).build(),

                // 4. Festive flat off, short window
                Coupon.builder().code("FESTIVE100").description("Rs.100 off festive offers")
                        .discountType(DiscountType.FIXED).discountValue(new BigDecimal("100.00"))
                        .minOrderAmount(new BigDecimal("999.00"))
                        .currency("INR").validFrom(now).validUntil(weekOut)
                        .usageLimit(200).perUserLimit(2).active(true).build(),

                // 5. Free-shipping equivalent
                Coupon.builder().code("FREESHIP49").description("Rs.49 off — equivalent to free shipping")
                        .discountType(DiscountType.FIXED).discountValue(new BigDecimal("49.00"))
                        .currency("INR").validFrom(monthAgo).validUntil(yearOut)
                        .usageLimit(null).perUserLimit(null).active(true).build(),

                // 6. Loyalty 15% off, limited
                Coupon.builder().code("LOYALTY15").description("15% off for returning customers")
                        .discountType(DiscountType.PERCENT).discountValue(new BigDecimal("15.00"))
                        .maxDiscountAmount(new BigDecimal("1000.00"))
                        .currency("INR").validFrom(monthAgo).validUntil(monthOut)
                        .usageLimit(300).perUserLimit(5).active(true).build(),

                // 7. Inactive coupon
                Coupon.builder().code("PAUSED5").description("Paused coupon (admin disabled)")
                        .discountType(DiscountType.FIXED).discountValue(new BigDecimal("5.00"))
                        .currency("INR").validFrom(monthAgo).validUntil(yearOut)
                        .active(false).build(),

                // 8. Expired coupon
                Coupon.builder().code("LASTYEAR25").description("Last year's offer (expired)")
                        .discountType(DiscountType.PERCENT).discountValue(new BigDecimal("25.00"))
                        .currency("INR").validFrom(now.minusYears(1)).validUntil(now.minusDays(1))
                        .active(true).build(),

                // 9. Not yet active
                Coupon.builder().code("NEXTWEEK10").description("Starts next week")
                        .discountType(DiscountType.PERCENT).discountValue(new BigDecimal("10.00"))
                        .currency("INR").validFrom(now.plusDays(7)).validUntil(monthOut)
                        .active(true).build(),

                // 10. One-shot deep discount
                Coupon.builder().code("FLASH75").description("Flash 75% off — first 50 only")
                        .discountType(DiscountType.PERCENT).discountValue(new BigDecimal("75.00"))
                        .maxDiscountAmount(new BigDecimal("3000.00"))
                        .currency("INR").validFrom(now).validUntil(weekOut)
                        .usageLimit(50).perUserLimit(1).active(true).build()
        );

        couponRepository.saveAll(seed);
        log.info("Seeded {} sample coupons", seed.size());
    }
}
