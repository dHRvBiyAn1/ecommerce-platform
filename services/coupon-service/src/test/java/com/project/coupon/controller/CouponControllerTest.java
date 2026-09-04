package com.project.coupon.controller;

import com.project.coupon.dto.CouponReservationRequest;
import com.project.coupon.dto.CouponReservationResponse;
import com.project.coupon.entity.RedemptionStatus;
import com.project.coupon.service.CouponService;
import com.project.coupon.validation.CouponRequestValidator;
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
import static org.mockito.Mockito.inOrder;
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
        CouponController controller = new CouponController(couponService, validator);
        UUID userId = UUID.randomUUID();
        Jwt jwt = Jwt.withTokenValue("token")
                .header("alg", "none")
                .subject(userId.toString())
                .claim("email", "customer@example.com")
                .build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(
                jwt, List.of(new SimpleGrantedAuthority("ROLE_CUSTOMER"))));
        CouponReservationRequest request = new CouponReservationRequest(
                "SAVE10", userId, "order-1", new BigDecimal("500.00"), "INR");
        CouponReservationResponse response = new CouponReservationResponse(
                UUID.randomUUID(), "SAVE10", userId, "order-1", new BigDecimal("50.00"),
                RedemptionStatus.RESERVED);
        when(couponService.reserve(request)).thenReturn(response);

        var entity = controller.reserve(request);

        assertThat(entity.getBody()).isEqualTo(response);
        InOrder calls = inOrder(validator, couponService);
        calls.verify(validator).validateReservation(request);
        calls.verify(validator).validateActor(userId, userId, false);
        calls.verify(couponService).reserve(request);
    }
}
