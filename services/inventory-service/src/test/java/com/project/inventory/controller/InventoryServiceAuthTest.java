package com.project.inventory.controller;

import com.project.inventory.application.validator.InventoryValidator;
import com.project.inventory.api.dto.request.StockReservationRequest;
import com.project.inventory.service.InventoryService;
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
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

@SpringJUnitConfig(InventoryServiceAuthTest.Config.class)
class InventoryServiceAuthTest {
    @Configuration
    @EnableMethodSecurity
    static class Config {
        @Bean InventoryController controller() {
            return new InventoryController(mock(InventoryService.class), new InventoryValidator());
        }
    }

    @Autowired InventoryController controller;

    @AfterEach void clearContext() { SecurityContextHolder.clearContext(); }

    static Stream<Object[]> cases() {
        return Stream.of("reserve", "commit", "release").flatMap(endpoint -> Stream.of(
                new Object[]{endpoint, "service", "SCOPE_inventory.write", true},
                new Object[]{endpoint, "service", "SCOPE_coupons.write", false},
                new Object[]{endpoint, "service", "", false},
                new Object[]{endpoint, "service", "ROLE_ADMIN inventory:write", false},
                new Object[]{endpoint, "user", "SCOPE_inventory.write ROLE_ADMIN inventory:write", false},
                new Object[]{endpoint, "user", "ROLE_CUSTOMER", false},
                new Object[]{endpoint, "anonymous", "", false}));
    }

    @ParameterizedTest(name = "{0}: {1} [{2}] allowed={3}")
    @MethodSource("cases")
    void reservationsRequireScopedService(String endpoint, String type, String authorities, boolean allowed) {
        assertThat(AopUtils.isAopProxy(controller)).isTrue();
        if (!type.equals("anonymous")) {
            Jwt jwt = Jwt.withTokenValue("test").header("alg", "RS256")
                    .subject("00000000-0000-0000-0000-000000000001").claim("token_type", type).build();
            SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt,
                    Arrays.stream(authorities.split(" ")).filter(s -> !s.isBlank())
                            .map(SimpleGrantedAuthority::new).toList()));
        }
        var request = new StockReservationRequest(1, "order-1");
        org.assertj.core.api.ThrowableAssert.ThrowingCallable call = () -> {
            var response = switch (endpoint) {
                case "reserve" -> controller.reserveStock("product-1", request);
                case "commit" -> controller.commitStock("product-1", request);
                default -> controller.releaseStock("product-1", request);
            };
            assertThat(response.getStatusCode().value()).isEqualTo(200);
        };
        if (allowed) org.assertj.core.api.Assertions.assertThatCode(call).doesNotThrowAnyException();
        else assertThatThrownBy(call).isInstanceOfAny(AccessDeniedException.class,
                AuthenticationCredentialsNotFoundException.class);
    }
}
