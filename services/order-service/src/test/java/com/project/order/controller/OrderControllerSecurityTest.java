package com.project.order.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.project.common.constant.Permissions;
import com.project.common.exception.GlobalExceptionHandler;
import com.project.order.OrderServiceApplication;
import com.project.order.config.SecurityConfig;
import com.project.order.generated.model.OrderResponse;
import com.project.order.generated.model.OrderStatusUpdateRequest;
import com.project.order.model.OrderStatus;
import com.project.order.service.OrderService;
import com.project.order.validation.OrderAccessValidator;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.mongodb.core.mapping.MongoMappingContext;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(
    value = OrderController.class,
    properties = {
      "spring.cloud.config.enabled=false",
      "spring.config.import=optional:file:/dev/null"
    })
@ContextConfiguration(classes = OrderServiceApplication.class)
@Import({
  SecurityConfig.class,
  GlobalExceptionHandler.class,
  OrderAccessValidator.class,
  com.project.order.generated.mapper.OrderApiMapperImpl.class
})
class OrderControllerSecurityTest {

  private static final UUID OWNER = UUID.fromString("11111111-1111-1111-1111-111111111111");
  private static final UUID OTHER = UUID.fromString("22222222-2222-2222-2222-222222222222");

  @Autowired private MockMvc mockMvc;

  @MockitoBean private OrderService orderService;

  @MockitoBean private JwtDecoder jwtDecoder;

  @MockitoBean(name = "mongoMappingContext")
  private MongoMappingContext mongoMappingContext;

  @Autowired private org.springframework.security.web.FilterChainProxy securityFilters;

  @Test
  void cookiesAndSessionsCannotSupplyBearerIdentity() throws Exception {
    var token =
        org.springframework.security.oauth2.jwt.Jwt.withTokenValue("owner-token")
            .header("alg", "none")
            .subject(OWNER.toString())
            .claim("token_type", "user")
            .claim("roles", java.util.List.of("ROLE_CUSTOMER"))
            .claim("permissions", java.util.List.of(Permissions.ORDERS_READ))
            .build();
    when(jwtDecoder.decode("owner-token")).thenReturn(token);
    when(orderService.getOrder("order-1"))
        .thenReturn(response("order-1", OWNER, OrderStatus.PENDING));
    var session = new org.springframework.mock.web.MockHttpSession();
    session.setAttribute(
        org.springframework.security.web.context.HttpSessionSecurityContextRepository
            .SPRING_SECURITY_CONTEXT_KEY,
        new org.springframework.security.core.context.SecurityContextImpl(
            new com.project.common.security.JwtAuthenticationConverter().convert(token)));

    mockMvc
        .perform(
            get("/api/v1/orders/order-1")
                .cookie(new jakarta.servlet.http.Cookie("access_token", "owner-token")))
        .andExpect(status().isUnauthorized());
    mockMvc
        .perform(get("/api/v1/orders/order-1").session(session))
        .andExpect(status().isUnauthorized());
    mockMvc
        .perform(get("/api/v1/orders/order-1").header("Authorization", "Bearer owner-token"))
        .andExpect(status().isOk());
    org.assertj.core.api.Assertions.assertThat(
            securityFilters.getFilterChains().stream()
                .flatMap(chain -> chain.getFilters().stream()))
        .noneMatch(
            filter ->
                filter
                    instanceof
                    org.springframework.security.web.authentication.www.BasicAuthenticationFilter);
  }

  @Test
  void customerCannotReadAnotherCustomersOrder() throws Exception {
    when(orderService.getOrder("order-1"))
        .thenReturn(response("order-1", OWNER, OrderStatus.CONFIRMED));

    mockMvc
        .perform(get("/api/v1/orders/order-1").with(customer(OTHER, Permissions.ORDERS_READ)))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("FORBIDDEN"));
  }

  @Test
  void customerCannotTransitionOrderStatusEvenWithUpdatePermission() throws Exception {
    mockMvc
        .perform(
            put("/api/v1/orders/order-1/status")
                .with(customer(OWNER, Permissions.ORDERS_UPDATE))
                .contentType("application/json")
                .content("{\"status\":\"SHIPPED\"}"))
        .andExpect(status().isForbidden());
  }

  @Test
  void adminWithUpdatePermissionCanTransitionOrderStatus() throws Exception {
    when(orderService.updateOrderStatus(eq("order-1"), any(OrderStatusUpdateRequest.class)))
        .thenReturn(response("order-1", OWNER, OrderStatus.SHIPPED));

    mockMvc
        .perform(
            put("/api/v1/orders/order-1/status")
                .with(
                    jwt()
                        .jwt(token -> token.subject(OTHER.toString()).claim("token_type", "user"))
                        .authorities(
                            new SimpleGrantedAuthority(Permissions.ORDERS_UPDATE),
                            new SimpleGrantedAuthority("ROLE_ADMIN")))
                .contentType("application/json")
                .content("{\"status\":\"SHIPPED\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.status").value("SHIPPED"));
  }

  @Test
  void missingIdempotencyKeyRemainsAValidNonReplayableCreate() throws Exception {
    when(orderService.createOrder(any(), eq(OWNER), eq("owner@example.com"), eq(null)))
        .thenReturn(response("order-1", OWNER, OrderStatus.PENDING));

    mockMvc
        .perform(
            post("/api/v1/orders")
                .with(customer(OWNER, Permissions.ORDERS_CREATE, "owner@example.com"))
                .contentType("application/json")
                .content(validOrderRequest()))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.data.id").value("order-1"));
  }

  @Test
  void duplicateIdempotencyKeyIsForwardedForDurableReplay() throws Exception {
    when(orderService.createOrder(any(), eq(OWNER), eq("owner@example.com"), eq("checkout-1")))
        .thenReturn(response("order-1", OWNER, OrderStatus.PENDING));

    for (int attempt = 0; attempt < 2; attempt++) {
      mockMvc
          .perform(
              post("/api/v1/orders")
                  .with(customer(OWNER, Permissions.ORDERS_CREATE, "owner@example.com"))
                  .header("X-Idempotency-Key", "checkout-1")
                  .contentType("application/json")
                  .content(validOrderRequest()))
          .andExpect(status().isCreated())
          .andExpect(jsonPath("$.data.id").value("order-1"));
    }

    verify(orderService, times(2))
        .createOrder(any(), eq(OWNER), eq("owner@example.com"), eq("checkout-1"));
  }

  private static org.springframework.security.test.web.servlet.request
          .SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor
      customer(UUID userId, String authority) {
    return jwt()
        .jwt(token -> token.subject(userId.toString()).claim("token_type", "user"))
        .authorities(new SimpleGrantedAuthority(authority));
  }

  private static org.springframework.security.test.web.servlet.request
          .SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor
      customer(UUID userId, String authority, String email) {
    return jwt()
        .jwt(
            token ->
                token.subject(userId.toString()).claim("token_type", "user").claim("email", email))
        .authorities(new SimpleGrantedAuthority(authority));
  }

  private static String validOrderRequest() {
    return """
                {"items":[{"productId":"product-1","quantity":1}],"paymentMethod":"CARD",
                 "shippingAddress":{"fullName":"Owner","phone":"1234567890","street":"Main Street",
                 "city":"City","state":"State","zipCode":"12345","country":"IN"}}
                """;
  }

  private static OrderResponse response(String id, UUID userId, OrderStatus status) {
    return new OrderResponse()
        .id(id)
        .orderNumber("ORD-1")
        .userId(userId)
        .status(com.project.order.generated.model.OrderResponse.StatusEnum.valueOf(status.name()));
  }
}
