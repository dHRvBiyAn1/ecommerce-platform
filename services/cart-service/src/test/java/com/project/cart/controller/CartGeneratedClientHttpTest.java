package com.project.cart.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.project.cart.config.SecurityConfig;
import com.project.cart.generated.model.CartResponse;
import com.project.cart.generated.testclient.api.CartApi;
import com.project.cart.generated.testclient.invoker.ApiClient;
import com.project.cart.generated.testclient.invoker.ApiException;
import com.project.cart.generated.testclient.model.AddCartItemRequest;
import com.project.cart.generated.testclient.model.ApplyCouponRequest;
import com.project.cart.generated.testclient.model.UpdateQuantityRequest;
import com.project.cart.service.CartService;
import com.project.common.exception.BusinessException;
import com.project.common.exception.GlobalExceptionHandler;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestComponent;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.web.servlet.context.ServletWebServerApplicationContext;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

@SpringBootTest(
    classes = CartGeneratedClientHttpTest.TestApplication.class,
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {
      "spring.cloud.config.enabled=false",
      "spring.config.import=optional:file:/dev/null",
      "spring.cloud.discovery.enabled=false",
      "eureka.client.enabled=false",
      "management.endpoints.enabled-by-default=false"
    })
class CartGeneratedClientHttpTest {

  private static final UUID USER_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
  private static final String TOKEN = "cart-test-token";

  @Autowired private ServletWebServerApplicationContext serverContext;

  @Autowired
  @Qualifier("requestMappingHandlerMapping")
  private RequestMappingHandlerMapping handlerMapping;

  @MockBean private CartService cartService;

  @MockBean private JwtDecoder jwtDecoder;

  private CartApi api;

  @BeforeEach
  void setUpClientAndJwtDecoder() {
    when(jwtDecoder.decode(TOKEN))
        .thenAnswer(
            ignored -> {
              Instant now = Instant.now();
              return Jwt.withTokenValue(TOKEN)
                  .header("alg", "none")
                  .subject(USER_ID.toString())
                  .issuedAt(now)
                  .expiresAt(now.plusSeconds(60))
                  .claim("roles", List.of("ROLE_CUSTOMER"))
                  .build();
            });
    int port = serverContext.getWebServer().getPort();
    ApiClient client =
        new ApiClient()
            .setHost("localhost")
            .setPort(port)
            .setBasePath("")
            .setRequestInterceptor(
                request -> request.header(HttpHeaders.AUTHORIZATION, "Bearer " + TOKEN));
    api = new CartApi(client);
  }

  @Test
  void generatedClientCallsGetAddAndUpdateAndDecodesNullableMoneyAndItems() throws Exception {
    CartResponse empty =
        new CartResponse(
            null,
            USER_ID,
            List.of(),
            null,
            null,
            BigDecimal.ZERO.setScale(2),
            BigDecimal.ZERO.setScale(2),
            BigDecimal.ZERO.setScale(2),
            0,
            null);
    CartResponse filled =
        new CartResponse(
            "cart-1",
            USER_ID,
            List.of(
                new com.project.cart.generated.model.CartItem(
                    "sku-1", "SKU-1", "Desk", null, new BigDecimal("12.35"), 2)),
            "INR",
            null,
            BigDecimal.ZERO.setScale(2),
            new BigDecimal("24.70"),
            new BigDecimal("24.70"),
            2,
            null);
    when(cartService.getMyCart(USER_ID)).thenReturn(empty);
    when(cartService.addItem(eq(USER_ID), any())).thenReturn(filled);
    when(cartService.updateQuantity(eq(USER_ID), eq("sku-1"), any())).thenReturn(filled);

    var read = api.getMyCart();
    assertThat(read.getUserId()).isEqualTo(USER_ID);
    assertThat(read.getItems()).isEmpty();
    assertThat(read.getId_JsonNullable().isPresent()).isTrue();
    assertThat(read.getId()).isNull();
    assertThat(read.getCurrency_JsonNullable().isPresent()).isTrue();
    assertThat(read.getCurrency()).isNull();
    assertThat(read.getAppliedCouponCode_JsonNullable().isPresent()).isTrue();
    assertThat(read.getAppliedCouponCode()).isNull();
    assertThat(read.getUpdatedAt_JsonNullable().isPresent()).isTrue();
    assertThat(read.getTotal()).isEqualByComparingTo("0.00");

    var added = api.addCartItem(new AddCartItemRequest().productId("sku-1").quantity(2));
    assertThat(added.getItems()).hasSize(1);
    assertThat(added.getItems().getFirst().getImageUrl_JsonNullable().isPresent()).isTrue();
    assertThat(added.getItems().getFirst().getImageUrl()).isNull();
    assertThat(added.getTotal()).isEqualByComparingTo("24.70");
    assertThat(added.getItems().getFirst().getUnitPrice()).isEqualByComparingTo("12.35");

    var updated = api.updateCartItemQuantity("sku-1", new UpdateQuantityRequest().quantity(2));
    assertThat(updated.getItemCount()).isEqualTo(2);
    api.updateCartItemQuantity("sku-1", new UpdateQuantityRequest());
    verify(cartService).getMyCart(USER_ID);
    verify(cartService)
        .addItem(
            eq(USER_ID), eq(new com.project.cart.generated.model.AddCartItemRequest("sku-1", 2)));
    verify(cartService)
        .updateQuantity(
            eq(USER_ID),
            eq("sku-1"),
            eq(new com.project.cart.generated.model.UpdateQuantityRequest(2)));
    verify(cartService)
        .updateQuantity(
            eq(USER_ID),
            eq("sku-1"),
            eq(new com.project.cart.generated.model.UpdateQuantityRequest(0)));
  }

  @Test
  void omittedAndNullQuantityKeepLegacyZeroDefaultOverHttp() throws Exception {
    when(cartService.updateQuantity(eq(USER_ID), eq("sku-1"), any()))
        .thenReturn(new CartResponse().userId(USER_ID).items(List.of()).itemCount(0));
    var client = java.net.http.HttpClient.newHttpClient();
    for (String body : List.of("{}", "{\"quantity\":null}")) {
      var request =
          java.net.http.HttpRequest.newBuilder(
                  java.net.URI.create(
                      "http://localhost:"
                          + serverContext.getWebServer().getPort()
                          + "/api/v1/cart/items/sku-1"))
              .header(HttpHeaders.AUTHORIZATION, "Bearer " + TOKEN)
              .header(HttpHeaders.CONTENT_TYPE, "application/json")
              .method("PATCH", java.net.http.HttpRequest.BodyPublishers.ofString(body))
              .build();
      var response = client.send(request, java.net.http.HttpResponse.BodyHandlers.ofString());
      assertThat(response.statusCode()).isEqualTo(200);
    }
    verify(cartService, org.mockito.Mockito.times(2))
        .updateQuantity(
            eq(USER_ID),
            eq("sku-1"),
            eq(new com.project.cart.generated.model.UpdateQuantityRequest(0)));
  }

  @Test
  void anonymousRequestGetsBodylessBearerChallengeAndCouponOutageRemains503() throws Exception {
    ApiClient anonymousClient =
        new ApiClient()
            .setHost("localhost")
            .setPort(serverContext.getWebServer().getPort())
            .setBasePath("");
    CartApi anonymous = new CartApi(anonymousClient);
    ApiException unauthorized = catchThrowableOfType(anonymous::getMyCart, ApiException.class);
    assertThat(unauthorized.getCode()).isEqualTo(HttpStatus.UNAUTHORIZED.value());
    assertThat(unauthorized.getResponseBody()).isNullOrEmpty();
    assertThat(
            unauthorized.getResponseHeaders().firstValue(HttpHeaders.WWW_AUTHENTICATE).orElse(null))
        .contains("Bearer");

    when(cartService.applyCoupon(eq(USER_ID), any()))
        .thenThrow(
            new BusinessException(
                HttpStatus.SERVICE_UNAVAILABLE, "COUPON_UNAVAILABLE", "upstream coupon detail"));
    ApiException unavailable =
        catchThrowableOfType(
            () -> api.applyCartCoupon(new ApplyCouponRequest().code("SAVE10")), ApiException.class);
    assertThat(unavailable.getCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE.value());
    assertThat(unavailable.getResponseBody())
        .contains("COUPON_UNAVAILABLE")
        .contains("Upstream service temporarily unavailable")
        .doesNotContain("upstream coupon detail");
  }

  @Test
  void generatedRequestValidationKeepsExistingMessagesAndPrimitiveQuantityDefault()
      throws Exception {
    ApiException invalidItem =
        catchThrowableOfType(
            () -> api.addCartItem(new AddCartItemRequest().productId(" ").quantity(null)),
            ApiException.class);
    assertThat(invalidItem.getCode()).isEqualTo(HttpStatus.BAD_REQUEST.value());
    assertThat(invalidItem.getResponseBody())
        .contains("\"productId\":\"must not be blank\"")
        .contains("\"quantity\":\"must be greater than or equal to 1\"");

    ApiException invalidCoupon =
        catchThrowableOfType(
            () -> api.applyCartCoupon(new ApplyCouponRequest().code(" ")), ApiException.class);
    assertThat(invalidCoupon.getCode()).isEqualTo(HttpStatus.BAD_REQUEST.value());
    assertThat(invalidCoupon.getResponseBody()).contains("\"code\":\"must not be blank\"");
  }

  @Test
  void notBlankValidationPreservesMultilineValuesAndRejectsTrimmedControlCharacters()
      throws Exception {
    CartResponse empty =
        new CartResponse(
            null,
            USER_ID,
            List.of(),
            null,
            null,
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            0,
            null);
    when(cartService.addItem(eq(USER_ID), any())).thenReturn(empty);
    when(cartService.applyCoupon(eq(USER_ID), any())).thenReturn(empty);

    api.addCartItem(new AddCartItemRequest().productId("sku\nid").quantity(1));
    api.applyCartCoupon(new ApplyCouponRequest().code("SAVE\n10"));
    verify(cartService)
        .addItem(USER_ID, new com.project.cart.generated.model.AddCartItemRequest("sku\nid", 1));
    verify(cartService)
        .applyCoupon(USER_ID, new com.project.cart.generated.model.ApplyCouponRequest("SAVE\n10"));

    ApiException invalidItem =
        catchThrowableOfType(
            () -> api.addCartItem(new AddCartItemRequest().productId("\0").quantity(1)),
            ApiException.class);
    assertThat(invalidItem.getCode()).isEqualTo(400);
    assertThat(invalidItem.getResponseBody()).contains("\"productId\":\"must not be blank\"");
    ApiException invalidCoupon =
        catchThrowableOfType(
            () -> api.applyCartCoupon(new ApplyCouponRequest().code("\0")), ApiException.class);
    assertThat(invalidCoupon.getCode()).isEqualTo(400);
    assertThat(invalidCoupon.getResponseBody()).contains("\"code\":\"must not be blank\"");
  }

  @Test
  void generatedInterfaceRegistersEachCartHttpOperationExactlyOnce() {
    Map<String, Set<RequestMethod>> expected =
        Map.of(
            "/api/v1/cart", Set.of(RequestMethod.GET, RequestMethod.DELETE),
            "/api/v1/cart/items", Set.of(RequestMethod.POST),
            "/api/v1/cart/items/{productId}", Set.of(RequestMethod.PATCH, RequestMethod.DELETE),
            "/api/v1/cart/coupon", Set.of(RequestMethod.POST, RequestMethod.DELETE));

    Map<String, Map<RequestMethod, Long>> registered =
        handlerMapping.getHandlerMethods().entrySet().stream()
            .filter(entry -> CartController.class.isAssignableFrom(entry.getValue().getBeanType()))
            .flatMap(
                entry ->
                    entry.getKey().getMethodsCondition().getMethods().stream()
                        .flatMap(
                            method ->
                                entry
                                    .getKey()
                                    .getPathPatternsCondition()
                                    .getPatternValues()
                                    .stream()
                                    .map(path -> Map.entry(path, method))))
            .collect(
                java.util.stream.Collectors.groupingBy(
                    Map.Entry::getKey,
                    java.util.stream.Collectors.groupingBy(
                        Map.Entry::getValue, java.util.stream.Collectors.counting())));

    assertThat(registered).hasSize(4);
    expected.forEach(
        (path, methods) -> {
          assertThat(registered).containsKey(path);
          assertThat(registered.get(path).keySet()).containsExactlyInAnyOrderElementsOf(methods);
          methods.forEach(method -> assertThat(registered.get(path).get(method)).isEqualTo(1L));
        });
  }

  @Configuration(proxyBeanMethods = false)
  @TestComponent
  @EnableAutoConfiguration(
      excludeName = {
        "org.springframework.boot.autoconfigure.mongo.MongoAutoConfiguration",
        "org.springframework.boot.autoconfigure.data.mongo.MongoDataAutoConfiguration",
        "org.springframework.boot.autoconfigure.data.mongo.MongoRepositoriesAutoConfiguration",
        "org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration",
        "org.springframework.boot.autoconfigure.data.redis.RedisRepositoriesAutoConfiguration",
        "org.springframework.boot.autoconfigure.kafka.KafkaAutoConfiguration",
        "org.springframework.cloud.netflix.eureka.EurekaClientAutoConfiguration"
      })
  @Import({CartController.class, SecurityConfig.class, GlobalExceptionHandler.class})
  static class TestApplication {}
}
