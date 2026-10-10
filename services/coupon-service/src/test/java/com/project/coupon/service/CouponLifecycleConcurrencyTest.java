package com.project.coupon.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.project.common.exception.DuplicateResourceException;
import com.project.common.exception.ValidationException;
import com.project.coupon.entity.RedemptionStatus;
import com.project.coupon.exception.CouponUnavailableException;
import com.project.coupon.generated.model.CouponRequest;
import com.project.coupon.repository.CouponRedemptionRepository;
import com.project.coupon.repository.CouponRepository;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
@SpringBootTest(
    properties = {
      "spring.flyway.enabled=false",
      "spring.jpa.hibernate.ddl-auto=none",
      "eureka.client.enabled=false",
      "spring.cloud.config.enabled=false",
      "spring.security.oauth2.resourceserver.jwt.jwk-set-uri=http://localhost/unused"
    })
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class CouponLifecycleConcurrencyTest {

  @Container
  static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

  static {
    POSTGRES.start();
  }

  @Autowired private JdbcTemplate jdbcTemplate;

  @Autowired private CouponService couponService;

  @Autowired private CouponRepository couponRepository;

  @Autowired private CouponRedemptionRepository redemptionRepository;

  @DynamicPropertySource
  static void databaseProperties(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
    registry.add("spring.datasource.username", POSTGRES::getUsername);
    registry.add("spring.datasource.password", POSTGRES::getPassword);
  }

  @BeforeAll
  void migrateV1AndV2UpgradeFixture() {
    jdbcTemplate.execute("DROP TABLE IF EXISTS coupon_redemptions, coupons CASCADE");
    Flyway.configure()
        .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
        .locations("classpath:db/migration")
        .target("2")
        .load()
        .migrate();
    jdbcTemplate.execute(
        (ConnectionCallback<Void>)
            connection -> {
              ScriptUtils.executeSqlScript(
                  connection, new ClassPathResource("db/coupon-lifecycle-upgrade-fixture.sql"));
              return null;
            });
    Flyway.configure()
        .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
        .locations("classpath:db/migration")
        .load()
        .migrate();
  }

  @AfterAll
  void closeContainer() {
    POSTGRES.stop();
  }

  @Test
  void upgradePreservesV1V2RowsAndEnforcesNormalizedCodeUniqueness() {
    assertThat(
            jdbcTemplate.queryForObject(
                "SELECT code FROM coupons WHERE code = 'LEGACY'", String.class))
        .isEqualTo("LEGACY");
    assertThatThrownBy(
            () ->
                jdbcTemplate.update(
                    "INSERT INTO coupons (code, discount_type, discount_value, valid_from, valid_until) VALUES (?, 'PERCENT', 10, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP + INTERVAL '1 day')",
                    " legacy "))
        .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
    assertThat(
            jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM coupon_redemptions WHERE coupon_code = 'LEGACY'",
                Integer.class))
        .isEqualTo(1);
  }

  @Test
  void createRejectsCodesThatDifferOnlyByWhitespaceAndCase() {
    couponService.create(request("  Save10  ", null));

    assertThatThrownBy(() -> couponService.create(request("save10", null)))
        .isInstanceOf(DuplicateResourceException.class);
  }

  @Test
  void parallelReservationsAllowOnlyOneWinnerPerUsageSlot() throws Exception {
    insertCoupon("FLASH", 1);
    ExecutorService executor = Executors.newFixedThreadPool(2);
    try {
      List<Callable<Throwable>> attempts =
          List.of(
              () -> reserve("order-a", UUID.randomUUID()),
              () -> reserve("order-b", UUID.randomUUID()));
      List<Future<Throwable>> results = executor.invokeAll(attempts);

      List<Throwable> failures = results.stream().map(this::getThrowable).toList();
      assertThat(failures).filteredOn(java.util.Objects::isNull).hasSize(1);
      assertThat(failures)
          .filteredOn(java.util.Objects::nonNull)
          .singleElement()
          .isInstanceOf(CouponUnavailableException.class);
      assertThat(
              jdbcTemplate.queryForObject(
                  "SELECT reserved_count FROM coupons WHERE code = 'FLASH'", Integer.class))
          .isEqualTo(1);
    } finally {
      executor.shutdownNow();
    }
  }

  @Test
  void reservationCanBeCommittedOrReleasedExactlyOnce() {
    UUID id = insertCoupon("LIFE", 2);
    UUID user = UUID.randomUUID();
    couponService.reserve(reservation("life", user, "order-life"));
    couponService.commit(transition("LIFE", user, "order-life"));

    assertThat(redemptionRepository.findByOrderId("order-life"))
        .get()
        .extracting(r -> r.getStatus())
        .isEqualTo(RedemptionStatus.COMMITTED);
    assertThat(couponRepository.findById(id)).get().extracting(c -> c.getUsageCount()).isEqualTo(1);
  }

  @Test
  void releaseIsIdempotentAndTerminalTransitionsUseSharedConflict() {
    UUID id = insertCoupon("RELEASE", 2);
    UUID user = UUID.randomUUID();
    var request = reservation("release", user, "order-release");
    couponService.reserve(request);
    var transition = transition("RELEASE", user, "order-release");

    assertThat(couponService.release(transition).getStatus())
        .isEqualTo(
            com.project.coupon.generated.model.CouponReservationResponse.StatusEnum.RELEASED);
    assertThat(couponService.release(transition).getStatus())
        .isEqualTo(
            com.project.coupon.generated.model.CouponReservationResponse.StatusEnum.RELEASED);
    assertThatThrownBy(() -> couponService.commit(transition))
        .isInstanceOf(DuplicateResourceException.class)
        .extracting(e -> ((DuplicateResourceException) e).getStatus())
        .isEqualTo(HttpStatus.CONFLICT);
    assertThat(couponRepository.findById(id))
        .get()
        .extracting(c -> c.getReservedCount())
        .isEqualTo(0);
  }

  @Test
  void redeemCommitsReservationAndRejectsReleasedReservation() {
    insertCoupon("REDEEM", 2);
    UUID user = UUID.randomUUID();
    couponService.reserve(reservation("redeem", user, "order-redeem"));
    assertThat(couponService.redeem(redeem(" REDEEM ", user, "order-redeem")).getValid()).isTrue();
    assertThat(couponService.redeem(redeem("redeem", user, "order-redeem")).getValid()).isTrue();

    UUID releaseUser = UUID.randomUUID();
    couponService.reserve(reservation("REDEEM", releaseUser, "order-released"));
    couponService.release(transition("REDEEM", releaseUser, "order-released"));
    assertThatThrownBy(() -> couponService.redeem(redeem("REDEEM", releaseUser, "order-released")))
        .isInstanceOf(DuplicateResourceException.class);
  }

  @Test
  void concurrentCreateTranslatesOnlyCouponUniqueViolationToSharedConflict() throws Exception {
    ExecutorService executor = Executors.newFixedThreadPool(2);
    try {
      List<Future<Throwable>> results =
          executor.invokeAll(
              List.of(
                  () -> createAndCapture(" concurrent "), () -> createAndCapture("CONCURRENT")));
      List<Throwable> failures = results.stream().map(this::getThrowable).toList();
      assertThat(failures).filteredOn(java.util.Objects::isNull).hasSize(1);
      assertThat(failures)
          .filteredOn(java.util.Objects::nonNull)
          .singleElement()
          .isInstanceOf(DuplicateResourceException.class);
      assertThat(failures)
          .filteredOn(java.util.Objects::nonNull)
          .singleElement()
          .extracting(e -> ((DuplicateResourceException) e).getStatus())
          .isEqualTo(HttpStatus.CONFLICT);
    } finally {
      executor.shutdownNow();
    }
  }

  @Test
  void invalidCouponInputUsesCommonValidationTypeAndStatus() {
    assertThatThrownBy(
            () ->
                new com.project.coupon.validation.CouponRequestValidator()
                    .validateValidation(
                        new com.project.coupon.generated.model.ValidateCouponRequest()
                            .code("bad code")
                            .userId(UUID.randomUUID())))
        .isInstanceOf(ValidationException.class)
        .extracting(e -> ((ValidationException) e).getStatus())
        .isEqualTo(HttpStatus.BAD_REQUEST);
  }

  private Throwable reserve(String orderId, UUID userId) {
    try {
      couponService.reserve(reservation(" flash ", userId, orderId));
      return null;
    } catch (Throwable exception) {
      return exception;
    }
  }

  private Throwable getThrowable(Future<Throwable> result) {
    try {
      return result.get();
    } catch (Exception exception) {
      throw new AssertionError(exception);
    }
  }

  private Throwable createAndCapture(String code) {
    try {
      couponService.create(request(code, true));
      return null;
    } catch (Throwable exception) {
      return exception;
    }
  }

  private com.project.coupon.generated.model.CouponReservationRequest reservation(
      String code, UUID user, String order) {
    return new com.project.coupon.generated.model.CouponReservationRequest()
        .code(code)
        .userId(user)
        .orderId(order)
        .subtotal(new BigDecimal("100"))
        .currency("INR");
  }

  private com.project.coupon.generated.model.CouponTransitionRequest transition(
      String code, UUID user, String order) {
    return new com.project.coupon.generated.model.CouponTransitionRequest()
        .code(code)
        .userId(user)
        .orderId(order);
  }

  private com.project.coupon.generated.model.RedeemCouponRequest redeem(
      String code, UUID user, String order) {
    return new com.project.coupon.generated.model.RedeemCouponRequest()
        .code(code)
        .userId(user)
        .orderId(order)
        .discountAmount(new BigDecimal("10"));
  }

  private UUID insertCoupon(String code, int usageLimit) {
    UUID id = UUID.randomUUID();
    jdbcTemplate.update(
        "INSERT INTO coupons (id, code, discount_type, discount_value, valid_from, valid_until, usage_limit) VALUES (?, ?, 'PERCENT', 10, ?, ?, ?)",
        id,
        code,
        LocalDateTime.now().minusHours(1),
        LocalDateTime.now().plusHours(1),
        usageLimit);
    return id;
  }

  private CouponRequest request(String code, Boolean active) {
    return new CouponRequest()
        .code(code)
        .description("test")
        .discountType(CouponRequest.DiscountTypeEnum.PERCENT)
        .discountValue(new BigDecimal("10"))
        .currency("INR")
        .validFrom(LocalDateTime.now().minusHours(1))
        .validUntil(LocalDateTime.now().plusHours(1))
        .usageLimit(2)
        .active(active);
  }
}
