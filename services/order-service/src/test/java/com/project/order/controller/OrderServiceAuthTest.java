package com.project.order.controller;

import com.project.common.exception.ForbiddenOperationException;
import com.project.order.dto.OrderResponse;
import com.project.order.service.OrderService;
import com.project.order.validation.OrderAccessValidator;
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

import java.util.Arrays;
import java.util.UUID;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@SpringJUnitConfig(OrderServiceAuthTest.Config.class)
class OrderServiceAuthTest {
    private static final UUID OWNER = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final String OTHER = "00000000-0000-0000-0000-000000000002";

    @Configuration
    @EnableMethodSecurity
    static class Config {
        @Bean OrderController controller() {
            OrderService service = mock(OrderService.class);
            OrderResponse order = OrderResponse.builder().id("order-1").orderNumber("ORD-1").userId(OWNER).build();
            when(service.getOrder("order-1")).thenReturn(order);
            when(service.getOrderByNumber("ORD-1")).thenReturn(order);
            return new OrderController(service, new OrderAccessValidator());
        }
    }

    @Autowired OrderController controller;
    @AfterEach void clearContext() { SecurityContextHolder.clearContext(); }

    static Stream<Object[]> cases() {
        return Stream.of("id", "number").flatMap(endpoint -> Stream.of(
                new Object[]{endpoint, "service", "payment-service", "SCOPE_orders.read", true},
                new Object[]{endpoint, "service", "payment-service", "SCOPE_coupons.read", false},
                new Object[]{endpoint, "service", OWNER.toString(), "orders:read ROLE_ADMIN SCOPE_coupons.read", false},
                new Object[]{endpoint, "service", OWNER.toString(), "orders:read", false},
                new Object[]{endpoint, "service", OWNER.toString(), "", false},
                new Object[]{endpoint, "user", OTHER, "orders:read SCOPE_orders.read", false},
                new Object[]{endpoint, "user", OWNER.toString(), "SCOPE_orders.read", false},
                new Object[]{endpoint, "user", OWNER.toString(), "orders:read", true},
                new Object[]{endpoint, "user", OTHER, "orders:read ROLE_ADMIN", true},
                new Object[]{endpoint, "user", OTHER, "ROLE_ADMIN", false},
                new Object[]{endpoint, "user", OTHER, "orders:read", false},
                new Object[]{endpoint, "anonymous", OTHER, "", false}));
    }

    @ParameterizedTest(name = "{0}: {1} {2} [{3}] allowed={4}")
    @MethodSource("cases")
    void detailRequiresScopedServiceOrPermittedOwner(String endpoint, String type, String subject,
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
            var response = endpoint.equals("id") ? controller.getOrder("order-1") : controller.getOrderByNumber("ORD-1");
            assertThat(response.getStatusCode().value()).isEqualTo(200);
            assertThat(response.getBody().getData().getId()).isEqualTo("order-1");
        };
        if (allowed) assertThatCode(call).doesNotThrowAnyException();
        else assertThatThrownBy(call).isInstanceOfAny(AccessDeniedException.class,
                AuthenticationCredentialsNotFoundException.class, ForbiddenOperationException.class);
    }
}
