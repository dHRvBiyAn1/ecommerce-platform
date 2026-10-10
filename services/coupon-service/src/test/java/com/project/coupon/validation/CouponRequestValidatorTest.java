package com.project.coupon.validation;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.project.coupon.exception.InvalidCouponRequestException;
import com.project.coupon.generated.model.CouponRequest;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class CouponRequestValidatorTest {

  private final CouponRequestValidator validator = new CouponRequestValidator();

  @Test
  void customerCanOnlySubmitCouponLifecycleCommandsForThemself() {
    UUID caller = UUID.randomUUID();

    assertThatCode(() -> validator.validateActor(caller, caller, false)).doesNotThrowAnyException();
    assertThatThrownBy(() -> validator.validateActor(UUID.randomUUID(), caller, false))
        .isInstanceOf(com.project.common.exception.ForbiddenOperationException.class);
    assertThatCode(() -> validator.validateActor(UUID.randomUUID(), caller, true))
        .doesNotThrowAnyException();
  }

  @Test
  void rejectsValidityWindowThatEndsBeforeItStarts() {
    LocalDateTime starts = LocalDateTime.now().plusDays(2);
    CouponRequest request =
        request(
            CouponRequest.DiscountTypeEnum.FIXED,
            new BigDecimal("25.00"),
            starts,
            starts.minusHours(1));

    assertThatThrownBy(() -> validator.validateDefinition(request))
        .isInstanceOf(InvalidCouponRequestException.class)
        .hasMessageContaining("validUntil");
  }

  @Test
  void rejectsPercentageAboveOneHundred() {
    CouponRequest request =
        request(
            CouponRequest.DiscountTypeEnum.PERCENT,
            new BigDecimal("100.01"),
            LocalDateTime.now(),
            LocalDateTime.now().plusDays(2));

    assertThatThrownBy(() -> validator.validateDefinition(request))
        .isInstanceOf(InvalidCouponRequestException.class)
        .hasMessageContaining("100");
  }

  private CouponRequest request(
      CouponRequest.DiscountTypeEnum type,
      BigDecimal value,
      LocalDateTime validFrom,
      LocalDateTime validUntil) {
    return new CouponRequest()
        .code("SAVE10")
        .description("Description")
        .discountType(type)
        .discountValue(value)
        .currency("INR")
        .validFrom(validFrom)
        .validUntil(validUntil)
        .active(true);
  }
}
