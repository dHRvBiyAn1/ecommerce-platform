package com.project.coupon.controller;

import com.project.coupon.generated.model.CouponReservationRequest;
import com.project.coupon.generated.model.CouponReservationResponse;
import com.project.coupon.service.CouponService;
import com.project.coupon.validation.CouponRequestValidator;
import com.project.common.exception.ForbiddenOperationException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.math.BigDecimal;
import java.util.UUID;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CouponControllerTest {

    @Mock
    private CouponService couponService;
    @Mock
    private CouponRequestValidator validator;

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void reservePassesThroughValidationBeforeCallingService() {
        CouponController controller = new CouponController(couponService, validator,
                new com.project.coupon.generated.mapper.CouponApiMapperImpl());
        UUID userId = UUID.randomUUID();
        Jwt jwt = Jwt.withTokenValue("token")
                .header("alg", "none")
                .subject(userId.toString())
                .claim("email", "customer@example.com")
                .build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(
                jwt, List.of(new SimpleGrantedAuthority("ROLE_CUSTOMER"))));
        var request = new com.project.coupon.generated.model.CouponReservationRequest()
                .code("SAVE10").userId(userId).orderId("order-1").subtotal(new BigDecimal("500.00")).currency("INR");
        CouponReservationRequest serviceRequest = request;
        CouponReservationResponse response = new CouponReservationResponse()
                .reservationId(UUID.randomUUID()).code("SAVE10").userId(userId).orderId("order-1")
                .discountAmount(new BigDecimal("50.00")).status(CouponReservationResponse.StatusEnum.RESERVED);
        when(couponService.reserve(serviceRequest)).thenReturn(response);

        var entity = controller.reserveCoupon(request);

        assertThat(entity.getBody().getStatus().getValue()).isEqualTo("RESERVED");
        assertThat(entity.getBody().getDiscountAmount()).isEqualByComparingTo("50.00");
        InOrder calls = inOrder(validator, couponService);
        calls.verify(validator).validateReservation(serviceRequest);
        calls.verify(validator).validateActor(userId, userId, false);
        calls.verify(couponService).reserve(serviceRequest);
    }

    @Test
    void serviceClientWithCouponWriteScopeDoesNotRequireUuidUserSubject() {
        CouponController controller = new CouponController(couponService, validator,
                new com.project.coupon.generated.mapper.CouponApiMapperImpl());
        UUID userId = UUID.randomUUID();
        Jwt jwt = Jwt.withTokenValue("token")
                .header("alg", "none")
                .subject("order-service")
                .claim("token_type", "service")
                .claim("scope", "coupons.write")
                .build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(
                jwt, List.of(new SimpleGrantedAuthority("SCOPE_coupons.write"))));
        var request = new com.project.coupon.generated.model.CouponReservationRequest()
                .code("SAVE10").userId(userId).orderId("order-1").subtotal(new BigDecimal("500.00")).currency("INR");
        CouponReservationRequest serviceRequest = request;
        CouponReservationResponse response = new CouponReservationResponse()
                .reservationId(UUID.randomUUID()).code("SAVE10").userId(userId).orderId("order-1")
                .discountAmount(new BigDecimal("50.00")).status(CouponReservationResponse.StatusEnum.RESERVED);
        when(couponService.reserve(serviceRequest)).thenReturn(response);

        var entity = controller.reserveCoupon(request);

        assertThat(entity.getBody().getStatus().getValue()).isEqualTo("RESERVED");
        verify(validator, never()).validateActor(userId, userId, false);
        verify(couponService).reserve(serviceRequest);
    }

    @Test
    void serviceClientWithoutCouponScopeCannotActForCustomer() {
        CouponController controller = new CouponController(couponService, validator,
                new com.project.coupon.generated.mapper.CouponApiMapperImpl());
        Jwt jwt = Jwt.withTokenValue("token")
                .header("alg", "none")
                .subject("untrusted-service")
                .claim("token_type", "service")
                .build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt));
        var request = new com.project.coupon.generated.model.CouponReservationRequest()
                .code("SAVE10").userId(UUID.randomUUID()).orderId("order-1")
                .subtotal(new BigDecimal("500.00")).currency("INR");

        assertThatThrownBy(() -> controller.reserveCoupon(request))
                .isInstanceOf(ForbiddenOperationException.class);
    }
}
