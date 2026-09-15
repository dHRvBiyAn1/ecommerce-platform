package com.project.cart.client;

import com.project.common.exception.BusinessException;
import com.project.common.exception.ValidationException;
import com.project.common.feign.FeignAuthForwardingConfig;
import com.project.common.feign.ServiceAuthProperties;
import com.project.common.feign.ServiceTokenClient;
import com.project.common.feign.ServiceTokenProvider;
import com.project.common.feign.ServiceTokenResponse;
import com.project.cart.application.mapper.CartMapper;
import com.project.cart.dto.ApplyCouponRequest;
import com.project.cart.model.Cart;
import com.project.cart.model.CartItem;
import com.project.cart.repository.CartRepository;
import com.project.cart.service.CartService;
import com.project.cart.service.CartCouponPersistenceService;
import com.sun.net.httpserver.HttpServer;
import feign.Feign;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.autoconfigure.http.HttpMessageConverters;
import org.springframework.cloud.openfeign.support.SpringDecoder;
import org.springframework.cloud.openfeign.support.SpringEncoder;
import org.springframework.cloud.openfeign.support.SpringMvcContract;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;

import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CouponClientIntegrationTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(FeignAuthForwardingConfig.class)
            .withPropertyValues(
                    "spring.application.name=cart-service",
                    "service.auth.enabled=true",
                    "service.auth.token-uri=http://auth.test/api/auth/token",
                    "service.auth.client-id=cart-service",
                    "service.auth.client-secret=test-secret",
                    "service.auth.scope=coupons.read");

    @Test
    void configuredCouponClientSendsCartServiceCredentialToCouponEndpoint() throws Exception {
        ServiceAuthProperties properties = new ServiceAuthProperties();
        properties.setScope("coupons.read");
        ServiceTokenClient tokenClient = ignored -> new ServiceTokenResponse(
                "cart-service-token", "Bearer", 300, "coupons.read");
        AtomicReference<String> authorization = new AtomicReference<>();
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/api/v1/coupons/validate", exchange -> {
            authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
            byte[] response = "{\"valid\":true,\"code\":\"SAVE10\",\"discountAmount\":\"5.00\"}"
                    .getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });
        server.start();

        try {
            runner.withBean("couponTokenProvider", ServiceTokenProvider.class,
                        () -> new ServiceTokenProvider(tokenClient, properties), definition -> definition.setPrimary(true))
                .run(context -> {
                    HttpMessageConverters converters = new HttpMessageConverters(new MappingJackson2HttpMessageConverter());
                    CouponClient client = Feign.builder()
                            .contract(new SpringMvcContract())
                            .encoder(new SpringEncoder(() -> converters))
                            .decoder(new SpringDecoder(() -> converters))
                            .requestInterceptor(context.getBean(feign.RequestInterceptor.class))
                            .target(CouponClient.class, "http://localhost:" + server.getAddress().getPort());

                    CouponValidationResponse response = client.validate(CouponValidationRequest.builder()
                            .code("SAVE10").subtotal(new BigDecimal("25.00")).currency("INR").build());

                    assertThat(response.isValid()).isTrue();
                    assertThat(authorization.get()).isEqualTo("Bearer cart-service-token");
                });
        } finally {
            server.stop(0);
        }
    }

    @Test
    void unavailableCouponGatewayRaisesTypedValidationFailureInsteadOfReturningDiscount() {
        CouponClient fallback = new CouponClientFallback();

        assertThatThrownBy(() -> fallback.validate(CouponValidationRequest.builder()
                        .code("SAVE10").subtotal(new BigDecimal("25.00")).currency("INR").build()))
                .isInstanceOf(BusinessException.class)
                .satisfies(error -> {
                    BusinessException business = (BusinessException) error;
                    assertThat(business.getStatus().value()).isEqualTo(503);
                    assertThat(business.getCode()).isEqualTo("COUPON_UNAVAILABLE");
                });
    }

    @Test
    void invalidEmptyCouponGatewayResultDoesNotApplyDiscount() {
        UUID userId = UUID.fromString("22222222-2222-2222-2222-222222222222");
        Cart cart = Cart.builder().userId(userId).currency("INR").items(new ArrayList<>(java.util.List.of(
                CartItem.builder().productId("product-1").quantity(1).unitPrice(new BigDecimal("25.00")).build())))
                .build();
        CartService service = serviceReturning(userId, cart, null);

        assertThatThrownBy(() -> service.applyCoupon(userId, new ApplyCouponRequest("SAVE10")))
                .isInstanceOf(BusinessException.class)
                .satisfies(error -> assertThat(((BusinessException) error).getStatus().value()).isEqualTo(503));

        assertThat(cart.getAppliedCouponCode()).isNull();
        assertThat(cart.getAppliedDiscountAmount()).isNull();
    }

    @Test
    void mismatchedSuccessfulCouponCodeDoesNotApplyDiscount() {
        Cart cart = cart();
        CartService service = serviceReturning(cart.getUserId(), cart, CouponValidationResponse.builder()
                .valid(true).code("OTHER").discountAmount(new BigDecimal("5.00")).build());

        assertThatThrownBy(() -> service.applyCoupon(cart.getUserId(), new ApplyCouponRequest("SAVE10")))
                .isInstanceOf(ValidationException.class);

        assertThat(cart.getAppliedCouponCode()).isNull();
        assertThat(cart.getAppliedDiscountAmount()).isNull();
    }

    @Test
    void normalizedCouponCodeAppliesDiscount() {
        Cart cart = cart();
        CartService service = serviceReturning(cart.getUserId(), cart, CouponValidationResponse.builder()
                .valid(true).code("SAVE10").discountAmount(new BigDecimal("5.00")).build());

        service.applyCoupon(cart.getUserId(), new ApplyCouponRequest("  save10 "));

        assertThat(cart.getAppliedCouponCode()).isEqualTo("SAVE10");
        assertThat(cart.getAppliedDiscountAmount()).isEqualByComparingTo("5.00");
    }

    @Test
    void outboundCouponRequestUsesNormalizedCode() {
        Cart cart = cart();
        CouponClient client = request -> {
            if (!"SAVE10".equals(request.getCode())) {
                throw new IllegalArgumentException("coupon endpoint rejects unnormalized code");
            }
            return CouponValidationResponse.builder().valid(true).code("SAVE10")
                    .discountAmount(new BigDecimal("5.00")).build();
        };
        CartRepository repository = mock(CartRepository.class);
        when(repository.findByUserId(cart.getUserId())).thenReturn(Optional.of(cart));
        when(repository.save(cart)).thenReturn(cart);
        CartService service = new CartService(repository, client, mock(ProductClient.class), Mappers.getMapper(CartMapper.class),
                new CartCouponPersistenceService(repository));

        service.applyCoupon(cart.getUserId(), new ApplyCouponRequest("  save10 "));

        assertThat(cart.getAppliedCouponCode()).isEqualTo("SAVE10");
    }

    @Test
    void nonpositiveOrMissingCouponDiscountDoesNotApplyDiscount() {
        for (BigDecimal discount : java.util.List.of(BigDecimal.ZERO, new BigDecimal("-1.00"))) {
            Cart cart = cart();
            CartService service = serviceReturning(cart.getUserId(), cart, CouponValidationResponse.builder()
                    .valid(true).code("SAVE10").discountAmount(discount).build());

            assertThatThrownBy(() -> service.applyCoupon(cart.getUserId(), new ApplyCouponRequest("SAVE10")))
                    .isInstanceOf(ValidationException.class);
            assertThat(cart.getAppliedCouponCode()).isNull();
        }
    }

    @Test
    void missingCouponDiscountDoesNotApplyDiscount() {
        Cart cart = cart();
        CartService service = serviceReturning(cart.getUserId(), cart, CouponValidationResponse.builder()
                .valid(true).code("SAVE10").discountAmount(null).build());

        assertThatThrownBy(() -> service.applyCoupon(cart.getUserId(), new ApplyCouponRequest("SAVE10")))
                .isInstanceOf(ValidationException.class);
        assertThat(cart.getAppliedDiscountAmount()).isNull();
    }

    @Test
    void couponDiscountGreaterThanSubtotalDoesNotApplyDiscount() {
        Cart cart = cart();
        CartService service = serviceReturning(cart.getUserId(), cart, CouponValidationResponse.builder()
                .valid(true).code("SAVE10").discountAmount(new BigDecimal("25.01")).build());

        assertThatThrownBy(() -> service.applyCoupon(cart.getUserId(), new ApplyCouponRequest("SAVE10")))
                .isInstanceOf(ValidationException.class);
        assertThat(cart.getAppliedDiscountAmount()).isNull();
    }

    private static CartService serviceReturning(UUID userId, Cart cart, CouponValidationResponse response) {
        CartRepository repository = mock(CartRepository.class);
        when(repository.findByUserId(userId)).thenReturn(Optional.of(cart));
        when(repository.save(cart)).thenReturn(cart);
        CouponClient client = request -> response;
        return new CartService(repository, client, mock(ProductClient.class), Mappers.getMapper(CartMapper.class),
                new CartCouponPersistenceService(repository));
    }

    private static Cart cart() {
        UUID userId = UUID.fromString("22222222-2222-2222-2222-222222222222");
        return Cart.builder().userId(userId).currency("INR").items(new ArrayList<>(java.util.List.of(
                CartItem.builder().productId("product-1").quantity(1).unitPrice(new BigDecimal("25.00")).build())))
                .build();
    }
}
