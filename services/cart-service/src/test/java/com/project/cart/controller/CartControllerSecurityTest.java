package com.project.cart.controller;

import com.project.cart.config.SecurityConfig;
import com.project.cart.dto.CartResponse;
import com.project.cart.service.CartService;
import com.project.common.exception.BusinessException;
import com.project.common.exception.GlobalExceptionHandler;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.data.mongodb.core.mapping.MongoMappingContext;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(value = CartController.class, properties = {
        "spring.cloud.config.enabled=false",
        "spring.config.import=optional:file:/dev/null"
})
@Import({SecurityConfig.class, GlobalExceptionHandler.class})
class CartControllerSecurityTest {

    private static final UUID CART_OWNER = UUID.fromString("11111111-1111-1111-1111-111111111111");

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private CartService cartService;

    @MockBean
    private JwtDecoder jwtDecoder;

    @MockBean(name = "mongoMappingContext")
    private MongoMappingContext mongoMappingContext;

    @Test
    void anonymousCartRequestReceivesBearerChallenge() throws Exception {
        mockMvc.perform(get("/api/v1/cart"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("WWW-Authenticate", org.hamcrest.Matchers.containsString("Bearer")));
    }

    @Test
    void cartResponseUsesAuthenticatedSubjectInsteadOfAnyClientSuppliedIdentity() throws Exception {
        when(cartService.getMyCart(CART_OWNER)).thenReturn(cart(CART_OWNER));

        mockMvc.perform(get("/api/v1/cart")
                        .with(jwt().jwt(jwt -> jwt.subject(CART_OWNER.toString()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value(CART_OWNER.toString()))
                .andExpect(jsonPath("$.items[0].productId").value("product-1"));
    }

    @Test
    void couponGatewayOutageUsesSafeRetryableHttpError() throws Exception {
        when(cartService.applyCoupon(eq(CART_OWNER), any())).thenThrow(new BusinessException(
                org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE, "COUPON_UNAVAILABLE", "coupon detail"));

        mockMvc.perform(post("/api/v1/cart/coupon")
                        .contentType("application/json")
                        .content("{\"code\":\"SAVE10\"}")
                        .with(jwt().jwt(jwt -> jwt.subject(CART_OWNER.toString()))))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("COUPON_UNAVAILABLE"))
                .andExpect(jsonPath("$.message").value("Upstream service temporarily unavailable"));
    }

    private static CartResponse cart(UUID userId) {
        return new CartResponse("cart-1", userId, List.of(new CartResponse.Item(
                "product-1", "SKU-1", "Desk", null, new BigDecimal("12.00"), 1)), "INR",
                null, BigDecimal.ZERO, new BigDecimal("12.00"), new BigDecimal("12.00"), 1, null);
    }
}
