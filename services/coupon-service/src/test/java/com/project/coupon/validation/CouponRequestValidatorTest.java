package com.project.coupon.validation;

import com.project.coupon.dto.CouponRequest;
import com.project.coupon.entity.DiscountType;
import com.project.coupon.exception.InvalidCouponRequestException;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThatCode;

class CouponRequestValidatorTest {

    private final CouponRequestValidator validator = new CouponRequestValidator();

    @Test
    void customerCanOnlySubmitCouponLifecycleCommandsForThemself() {
        UUID caller = UUID.randomUUID();

        assertThatCode(() -> validator.validateActor(caller, caller, false))
                .doesNotThrowAnyException();
        assertThatThrownBy(() -> validator.validateActor(UUID.randomUUID(), caller, false))
                .isInstanceOf(com.project.common.exception.ForbiddenOperationException.class);
        assertThatCode(() -> validator.validateActor(UUID.randomUUID(), caller, true))
                .doesNotThrowAnyException();
    }

    @Test
    void rejectsValidityWindowThatEndsBeforeItStarts() {
        LocalDateTime starts = LocalDateTime.now().plusDays(2);
        CouponRequest request = request(DiscountType.FIXED, new BigDecimal("25.00"), starts, starts.minusHours(1));

        assertThatThrownBy(() -> validator.validateDefinition(request))
                .isInstanceOf(InvalidCouponRequestException.class)
                .hasMessageContaining("validUntil");
    }

    @Test
    void rejectsPercentageAboveOneHundred() {
        CouponRequest request = request(
                DiscountType.PERCENT,
                new BigDecimal("100.01"),
                LocalDateTime.now(),
                LocalDateTime.now().plusDays(2));

        assertThatThrownBy(() -> validator.validateDefinition(request))
                .isInstanceOf(InvalidCouponRequestException.class)
                .hasMessageContaining("100");
    }

    private CouponRequest request(
            DiscountType type, BigDecimal value, LocalDateTime validFrom, LocalDateTime validUntil) {
        return new CouponRequest(
                "SAVE10", "Description", type, value, null, null, "INR",
                validFrom, validUntil, null, null, true);
    }
}
