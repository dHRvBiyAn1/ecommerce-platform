package com.project.order.client;

import com.project.order.generated.integration.coupon.model.CouponReservationRequest;
import com.project.order.generated.integration.coupon.model.CouponTransitionRequest;
import com.project.order.generated.integration.coupon.model.RedeemCouponRequest;
import com.project.order.generated.integration.coupon.model.ValidateCouponRequest;
import com.project.order.generated.integration.inventory.model.StockReservationRequest;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ClientFallbackFactoryTest {

    @Test
    void productOutageAbortsCheckoutInsteadOfReturningAnEmptyProduct() {
        ProductClient fallback = new ProductClientFallbackFactory()
                .create(new IllegalStateException("catalog unavailable"));

        assertThatThrownBy(() -> fallback.getProduct("product-1"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Product catalog is currently offline. Order checkout cannot proceed.");
    }

    @Test
    void inventoryOutageFailsReservationCommitAndReleaseOperations() {
        InventoryClient fallback = new InventoryClientFallbackFactory()
                .create(new IllegalStateException("inventory unavailable"));
        StockReservationRequest command = new StockReservationRequest(2, "order-1");

        assertThatThrownBy(() -> fallback.reserve("product-1", command))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Checkout aborted to prevent overselling");
        assertThatThrownBy(() -> fallback.commit("product-1", command))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Inventory commit failed due to inventory service outage.");
        assertThatThrownBy(() -> fallback.release("product-1", command))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Compensation release failed due to inventory service outage.");
    }

    @Test
    void couponOutageRejectsValidationAndFailsReservationTransitions() {
        CouponClient fallback = new CouponClientFallbackFactory()
                .create(new IllegalStateException("coupon unavailable"));
        ValidateCouponRequest validation = new ValidateCouponRequest();
        CouponTransitionRequest transition = new CouponTransitionRequest();

        assertThat(fallback.validate(validation).getValid()).isFalse();
        assertThat(fallback.validate(validation).getReason()).isEqualTo("Coupon service is currently unavailable");
        assertThat(fallback.redeem(new RedeemCouponRequest()).getValid()).isFalse();
        assertThatThrownBy(() -> fallback.reserve(new CouponReservationRequest()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Coupon service is currently unavailable")
                .hasCauseInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> fallback.commit(transition))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Coupon service is currently unavailable");
        assertThatThrownBy(() -> fallback.release(transition))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Coupon service is currently unavailable");
    }
}
