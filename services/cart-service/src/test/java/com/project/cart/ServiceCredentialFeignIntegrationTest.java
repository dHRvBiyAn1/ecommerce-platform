package com.project.cart;

import com.project.cart.client.CouponClient;
import com.project.cart.client.CouponValidationRequest;
import com.project.cart.client.CouponValidationResponse;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.net.URLDecoder;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
@SpringBootTest(properties = {
        "spring.cloud.config.enabled=false",
        "eureka.client.enabled=false",
        "spring.security.oauth2.resourceserver.jwt.jwk-set-uri=http://localhost/unused",
        "spring.application.name=cart-service",
        "service.auth.enabled=true",
        "service.auth.client-id=cart-service",
        "service.auth.client-secret=cart-secret",
        "service.auth.scope=coupons.read"
})
class ServiceCredentialFeignIntegrationTest {

    @Container
    static final MongoDBContainer MONGO = new MongoDBContainer("mongo:7.0");

    private static final AtomicReference<String> AUTH_FORM = new AtomicReference<>();
    private static final AtomicReference<String> COUPON_AUTHORIZATION = new AtomicReference<>();
    private static final HttpServer AUTH_SERVER = server();
    private static final HttpServer COUPON_SERVER = server();

    static {
        AUTH_SERVER.createContext("/api/auth/token", exchange -> {
            AUTH_FORM.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            byte[] response = "{\"access_token\":\"cart-service-token\",\"token_type\":\"Bearer\",\"expires_in\":300,\"scope\":\"coupons.read\"}"
                    .getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });
        COUPON_SERVER.createContext("/api/v1/coupons/validate", exchange -> {
            COUPON_AUTHORIZATION.set(exchange.getRequestHeaders().getFirst("Authorization"));
            byte[] response = "{\"valid\":true,\"code\":\"SAVE10\",\"discountAmount\":\"5.00\"}"
                    .getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });
        AUTH_SERVER.start();
        COUPON_SERVER.start();
    }

    @Autowired
    private CouponClient couponClient;

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.mongodb.uri", MONGO::getReplicaSetUrl);
        registry.add("service.auth.token-uri", () -> "http://localhost:" + AUTH_SERVER.getAddress().getPort() + "/api/auth/token");
        registry.add("spring.cloud.openfeign.client.config.coupon-service.url",
                () -> "http://localhost:" + COUPON_SERVER.getAddress().getPort());
    }

    @AfterAll
    static void stopServers() {
        AUTH_SERVER.stop(0);
        COUPON_SERVER.stop(0);
    }

    @Test
    void couponClientObtainsConfiguredServiceTokenAndForwardsBearer() {
        CouponValidationResponse response = couponClient.validate(CouponValidationRequest.builder()
                .code("SAVE10")
                .userId(UUID.fromString("33333333-3333-3333-3333-333333333333"))
                .subtotal(new BigDecimal("25.00"))
                .currency("INR")
                .build());

        assertThat(response.isValid()).isTrue();
        assertThat(parseForm(AUTH_FORM.get())).containsExactlyInAnyOrderEntriesOf(Map.of(
                "grant_type", "client_credentials",
                "client_id", "cart-service",
                "client_secret", "cart-secret",
                "scope", "coupons.read"));
        assertThat(COUPON_AUTHORIZATION.get()).isEqualTo("Bearer cart-service-token");
    }

    private static Map<String, String> parseForm(String form) {
        Map<String, String> parameters = new HashMap<>();
        for (String pair : form.split("&")) {
            String[] keyValue = pair.split("=", 2);
            parameters.put(URLDecoder.decode(keyValue[0], StandardCharsets.UTF_8),
                    URLDecoder.decode(keyValue[1], StandardCharsets.UTF_8));
        }
        return parameters;
    }

    private static HttpServer server() {
        try {
            return HttpServer.create(new InetSocketAddress(0), 0);
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }
}
