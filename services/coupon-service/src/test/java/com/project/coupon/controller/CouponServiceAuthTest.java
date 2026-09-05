package com.project.coupon.controller;

import com.project.common.exception.ForbiddenOperationException;
import com.project.coupon.dto.*;
import com.project.coupon.service.CouponService;
import com.project.coupon.validation.CouponRequestValidator;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.UUID;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.mock;

@SpringJUnitConfig(CouponServiceAuthTest.Config.class)
class CouponServiceAuthTest {
    private static final UUID OWNER = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final String OTHER = "00000000-0000-0000-0000-000000000002";

    @Configuration
    @EnableMethodSecurity
    static class Config {
        @Bean CouponController controller() {
            return new CouponController(mock(CouponService.class), new CouponRequestValidator());
        }
    }

    @Autowired CouponController controller;
    @AfterEach void clearContext() { SecurityContextHolder.clearContext(); }

    static Stream<Object[]> cases() {
        return Stream.of("validate", "reserve", "commit", "release", "redeem").flatMap(endpoint -> {
            String scope = endpoint.equals("validate") ? "SCOPE_coupons.read" : "SCOPE_coupons.write";
            String wrong = endpoint.equals("validate") ? "SCOPE_coupons.write" : "SCOPE_coupons.read";
            return Stream.of(
                    new Object[]{endpoint, "service", "order-service", scope, true},
                    new Object[]{endpoint, "service", "order-service", wrong, false},
                    new Object[]{endpoint, "service", OWNER.toString(), "ROLE_ADMIN coupons:read coupons:write " + wrong, false},
                    new Object[]{endpoint, "service", OWNER.toString(), "", false},
                    new Object[]{endpoint, "user", OTHER, scope, false},
                    new Object[]{endpoint, "user", OWNER.toString(), "ROLE_CUSTOMER", true},
                    new Object[]{endpoint, "user", OTHER, "ROLE_ADMIN", true},
                    new Object[]{endpoint, "user", OTHER, "ROLE_CUSTOMER", false},
                    new Object[]{endpoint, "anonymous", OTHER, "", false});
        });
    }

    @ParameterizedTest(name = "{0}: {1} {2} [{3}] allowed={4}")
    @MethodSource("cases")
    void checkoutSeparatesServiceScopesFromUserOwnership(String endpoint, String type, String subject,
                                                        String authorities, boolean allowed) {
        assertThat(AopUtils.isAopProxy(controller)).isTrue();
        if (!type.equals("anonymous")) {
            Jwt jwt = Jwt.withTokenValue("test").header("alg", "RS256").subject(subject)
                    .claim("token_type", type).build();
            SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt,
                    Arrays.stream(authorities.split(" ")).filter(s -> !s.isBlank())
                            .map(SimpleGrantedAuthority::new).toList()));
        }
        org.assertj.core.api.ThrowableAssert.ThrowingCallable call = () -> {
            var response = switch (endpoint) {
                case "validate" -> controller.validate(new ValidateCouponRequest("SAVE10", OWNER, BigDecimal.TEN, "INR"));
                case "reserve" -> controller.reserve(new CouponReservationRequest("SAVE10", OWNER, "order-1", BigDecimal.TEN, "INR"));
                case "commit" -> controller.commit(new CouponTransitionRequest("SAVE10", OWNER, "order-1"));
                case "release" -> controller.release(new CouponTransitionRequest("SAVE10", OWNER, "order-1"));
                default -> controller.redeem(new RedeemCouponRequest("SAVE10", OWNER, "order-1", BigDecimal.ONE));
            };
            assertThat(response.getStatusCode().value()).isEqualTo(200);
        };
        if (allowed) assertThatCode(call).doesNotThrowAnyException();
        else assertThatThrownBy(call).isInstanceOfAny(AccessDeniedException.class,
                AuthenticationCredentialsNotFoundException.class, ForbiddenOperationException.class);
    }
}
