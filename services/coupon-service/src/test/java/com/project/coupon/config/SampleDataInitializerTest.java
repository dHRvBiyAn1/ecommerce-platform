package com.project.coupon.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.project.coupon.entity.Coupon;
import com.project.coupon.entity.DiscountType;
import com.project.coupon.repository.CouponRepository;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class SampleDataInitializerTest {
  private final CouponRepository repository = mock(CouponRepository.class);
  private final SampleDataInitializer initializer = new SampleDataInitializer(repository);

  @Test
  void seedingIsDisabledByDefaultAndPreservesExistingData() {
    initializer.run();
    verifyNoInteractions(repository);
    ReflectionTestUtils.setField(initializer, "seedEnabled", true);
    when(repository.count()).thenReturn(1L);
    initializer.run();
    org.mockito.Mockito.verify(repository, org.mockito.Mockito.never()).saveAll(any());
  }

  @Test
  void optInSeedingProvidesUsableAndUnavailableCouponScenarios() {
    ReflectionTestUtils.setField(initializer, "seedEnabled", true);
    when(repository.saveAll(any()))
        .thenAnswer(
            invocation -> {
              List<Coupon> coupons = invocation.getArgument(0);
              LocalDateTime now = LocalDateTime.now();
              assertThat(coupons).hasSize(10).extracting(Coupon::getCode).doesNotHaveDuplicates();
              assertThat(coupons)
                  .allSatisfy(
                      coupon -> {
                        assertThat(coupon.getCurrency()).isEqualTo("INR");
                        assertThat(coupon.getDiscountValue()).isPositive();
                        assertThat(coupon.getValidUntil()).isAfter(coupon.getValidFrom());
                      });
              assertThat(coupons)
                  .anyMatch(c -> c.isActive() && c.getDiscountType() == DiscountType.FIXED);
              assertThat(coupons)
                  .anyMatch(c -> c.isActive() && c.getDiscountType() == DiscountType.PERCENT);
              assertThat(coupons).anyMatch(c -> !c.isActive());
              assertThat(coupons).anyMatch(c -> c.getValidUntil().isBefore(now));
              assertThat(coupons).anyMatch(c -> c.getValidFrom().isAfter(now));
              assertThat(coupons)
                  .anyMatch(c -> c.getUsageLimit() != null && c.getPerUserLimit() != null);
              return coupons;
            });
    initializer.run();
    verify(repository).saveAll(any());
  }
}
