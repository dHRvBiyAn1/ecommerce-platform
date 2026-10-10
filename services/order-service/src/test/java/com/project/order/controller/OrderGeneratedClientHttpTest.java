package com.project.order.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.project.common.constant.ErrorCode;
import com.project.common.constant.Permissions;
import com.project.common.exception.GlobalExceptionHandler;
import com.project.order.config.SecurityConfig;
import com.project.order.generated.mapper.OrderApiMapperImpl;
import com.project.order.generated.model.OrderResponse;
import com.project.order.generated.testclient.api.OrdersApi;
import com.project.order.generated.testclient.invoker.ApiClient;
import com.project.order.generated.testclient.invoker.ApiException;
import com.project.order.generated.testclient.model.OrderItemRequest;
import com.project.order.generated.testclient.model.OrderRequest;
import com.project.order.generated.testclient.model.OrderStatusUpdateRequest;
import com.project.order.service.OrderService;
import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestComponent;
import org.springframework.boot.web.server.servlet.context.ServletWebServerApplicationContext;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest(
    classes = OrderGeneratedClientHttpTest.TestApplication.class,
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {
      "spring.cloud.config.enabled=false",
      "spring.config.import=optional:file:/dev/null",
      "spring.cloud.discovery.enabled=false",
      "eureka.client.enabled=false",
      "management.endpoints.enabled-by-default=false"
    })
class OrderGeneratedClientHttpTest {

  private static final UUID OWNER = UUID.fromString("11111111-1111-1111-1111-111111111111");
  private static final UUID OTHER = UUID.fromString("22222222-2222-2222-2222-222222222222");
  private static final UUID ADMIN = UUID.fromString("33333333-3333-3333-3333-333333333333");
  private static final String OWNER_TOKEN = "order-owner-token";
  private static final String OTHER_TOKEN = "order-other-token";
  private static final String ADMIN_TOKEN = "order-admin-token";

  @Autowired private ServletWebServerApplicationContext serverContext;

  @Autowired private ObjectMapper objectMapper;

  @Autowired
  @Qualifier("requestMappingHandlerMapping")
  private RequestMappingHandlerMapping handlerMapping;

  @MockitoBean private OrderService orderService;

  @MockitoBean private JwtDecoder jwtDecoder;

  private OrdersApi ownerApi;

  @BeforeEach
  void configureClientsAndJwtDecoder() {
    when(jwtDecoder.decode(OWNER_TOKEN)).thenReturn(jwt(OWNER_TOKEN, OWNER, false));
    when(jwtDecoder.decode(OTHER_TOKEN)).thenReturn(jwt(OTHER_TOKEN, OTHER, false));
    when(jwtDecoder.decode(ADMIN_TOKEN)).thenReturn(jwt(ADMIN_TOKEN, ADMIN, true));
    ownerApi = client(OWNER, false);
  }

  @Test
  void generatedClientReplaysTheSameOrderWithTheSameIdempotencyKey() throws Exception {
    when(orderService.createOrder(any(), eq(OWNER), eq(null), eq("checkout-1")))
        .thenReturn(response("order-1", OWNER, com.project.order.model.OrderStatus.PENDING));

    var first = ownerApi.createOrderWithHttpInfo(validRequest(), "checkout-1");
    var replay = ownerApi.createOrderWithHttpInfo(validRequest(), "checkout-1");

    assertThat(first.getStatusCode()).isEqualTo(201);
    assertThat(replay.getStatusCode()).isEqualTo(201);
    assertThat(replay.getData().getData().getId()).isEqualTo("order-1");
    verify(orderService, org.mockito.Mockito.times(2))
        .createOrder(any(), eq(OWNER), eq(null), eq("checkout-1"));
    verify(jwtDecoder, org.mockito.Mockito.atLeastOnce()).decode(OWNER_TOKEN);
  }

  @Test
  void generatedClientPreservesOrderOwnershipAndAdminStatusAuthorization() throws Exception {
    when(orderService.getOrder("order-1"))
        .thenReturn(response("order-1", OWNER, com.project.order.model.OrderStatus.PENDING));
    ApiException forbidden =
        catchThrowableOfType(() -> client(OTHER, false).getOrder("order-1"), ApiException.class);
    assertThat(forbidden.getCode()).isEqualTo(HttpStatus.FORBIDDEN.value());

    when(orderService.updateOrderStatus(eq("order-1"), any()))
        .thenReturn(response("order-1", OWNER, com.project.order.model.OrderStatus.SHIPPED));
    var updated =
        client(ADMIN, true)
            .updateOrderStatus(
                "order-1",
                new OrderStatusUpdateRequest().status(OrderStatusUpdateRequest.StatusEnum.SHIPPED));
    assertThat(updated.getData().getStatus().toString()).isEqualTo("SHIPPED");
    verify(jwtDecoder).decode(OTHER_TOKEN);
    verify(jwtDecoder).decode(ADMIN_TOKEN);

    ApiException customerDenied =
        catchThrowableOfType(
            () ->
                ownerApi.updateOrderStatus(
                    "order-1",
                    new OrderStatusUpdateRequest()
                        .status(OrderStatusUpdateRequest.StatusEnum.SHIPPED)),
            ApiException.class);
    assertThat(customerDenied.getCode()).isEqualTo(HttpStatus.FORBIDDEN.value());
  }

  @Test
  void invalidStatusFilterKeepsTheLegacyTypeMismatchResponse() throws Exception {
    HttpResponse<String> rejected = rawGetOrdersByStatus("not-a-status");

    assertThat(rejected.statusCode()).isEqualTo(HttpStatus.BAD_REQUEST.value());
    JsonNode error = objectMapper.readTree(rejected.body());
    assertThat(error.path("code").asText()).isEqualTo(ErrorCode.TYPE_MISMATCH.value());
    assertThat(error.path("message").asText())
        .isEqualTo("Parameter 'status' has invalid value: not-a-status");
    verify(orderService, never()).getOrdersByStatus(any(), any());
  }

  @Test
  void generatedEnumFilterAndTrimmedLegacyPathValueReachTheService() throws Exception {
    when(orderService.getOrdersByStatus(eq(com.project.order.model.OrderStatus.PENDING), any()))
        .thenReturn(Page.empty());

    var generated =
        client(ADMIN, true)
            .getOrdersByStatus(
                com.project.order.generated.testclient.model.OrderStatus.PENDING, null, null, null);
    HttpResponse<String> trimmed = rawGetOrdersByStatus("%20PENDING%20");

    assertThat(generated.getData().getContent()).isEmpty();
    assertThat(trimmed.statusCode()).isEqualTo(HttpStatus.OK.value());
    verify(orderService, org.mockito.Mockito.times(2))
        .getOrdersByStatus(eq(com.project.order.model.OrderStatus.PENDING), any());
  }

  @Test
  void generatedClientCancellationUsesTheAuthenticatedOwner() throws Exception {
    when(orderService.cancelOrder("order-1", OWNER, false))
        .thenReturn(response("order-1", OWNER, com.project.order.model.OrderStatus.CANCELLED));
    var cancelled = ownerApi.cancelOrder("order-1");
    assertThat(cancelled.getData().getStatus().toString()).isEqualTo("CANCELLED");
    verify(orderService).cancelOrder("order-1", OWNER, false);

    when(orderService.cancelOrder("order-2", ADMIN, true))
        .thenReturn(response("order-2", OWNER, com.project.order.model.OrderStatus.CANCELLED));
    var adminCancelled = client(ADMIN, true).cancelOrder("order-2");
    assertThat(adminCancelled.getData().getStatus().toString()).isEqualTo("CANCELLED");
    verify(orderService).cancelOrder("order-2", ADMIN, true);
  }

  @Test
  void generatedRequestValidationKeepsLegacyWhitespaceAndPrimitiveMinimumMessages() {
    var invalid =
        new OrderRequest()
            .items(List.of(new OrderItemRequest().productId(" ").quantity(null)))
            .paymentMethod(" ");
    ApiException rejected =
        catchThrowableOfType(() -> ownerApi.createOrder(invalid, null), ApiException.class);
    assertThat(rejected.getCode()).isEqualTo(HttpStatus.BAD_REQUEST.value());
    assertThat(rejected.getResponseBody())
        .contains(
            "Product ID is required", "Quantity must be at least 1", "Payment method is required");
  }

  @Test
  void generatedClientPreservesLegacyNotBlankHandlingOfMultilineAndControlValues()
      throws ApiException {
    when(orderService.createOrder(any(), eq(OWNER), eq(null), eq(null)))
        .thenReturn(response("order-1", OWNER, com.project.order.model.OrderStatus.PENDING));
    OrderRequest multiline =
        new OrderRequest()
            .items(List.of(new OrderItemRequest().productId("line1\nline2").quantity(1)))
            .paymentMethod("CARD");

    assertThat(ownerApi.createOrderWithHttpInfo(multiline, null).getStatusCode()).isEqualTo(201);
    OrderRequest controlOnly =
        new OrderRequest()
            .items(List.of(new OrderItemRequest().productId(String.valueOf((char) 0)).quantity(1)))
            .paymentMethod("CARD");
    ApiException rejected =
        catchThrowableOfType(() -> ownerApi.createOrder(controlOnly, null), ApiException.class);
    assertThat(rejected.getCode()).isEqualTo(HttpStatus.BAD_REQUEST.value());
    assertThat(rejected.getResponseBody()).contains("Product ID is required");
    var captured =
        org.mockito.ArgumentCaptor.forClass(com.project.order.generated.model.OrderRequest.class);
    verify(orderService).createOrder(captured.capture(), eq(OWNER), eq(null), eq(null));
    assertThat(captured.getValue().getItems().get(0).getProductId()).isEqualTo("line1\nline2");
  }

  @Test
  void generatedOrderPageSerializationMatchesLegacySpringPageForSortedAndEmptyPages()
      throws Exception {
    assertPageParity(
        new PageImpl<>(
            List.of(response("order-1", OWNER, com.project.order.model.OrderStatus.PENDING)),
            PageRequest.of(1, 2, Sort.by(Sort.Order.desc("createdAt"))),
            5));
    assertPageParity(Page.empty(PageRequest.of(0, 20)));
  }

  private void assertPageParity(Page<OrderResponse> page) throws Exception {
    when(orderService.getUserOrders(eq(OWNER), any())).thenReturn(page);
    HttpRequest request =
        HttpRequest.newBuilder(
                URI.create(
                    "http://localhost:"
                        + serverContext.getWebServer().getPort()
                        + "/api/v1/orders?page="
                        + page.getNumber()
                        + "&size="
                        + page.getSize()
                        + (page.getSort().isSorted() ? "&sort=createdAt,desc" : "")))
            .header("Authorization", "Bearer " + OWNER_TOKEN)
            .GET()
            .build();
    HttpResponse<String> response =
        HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());
    assertThat(response.statusCode()).isEqualTo(200);
    JsonNode actualData = objectMapper.readTree(response.body()).path("data");
    JsonNode legacyData =
        objectMapper
            .readTree(
                objectMapper.writeValueAsString(com.project.common.web.Responses.success(page)))
            .path("data");
    assertThat(actualData).isEqualTo(legacyData);
    verify(orderService)
        .getUserOrders(
            eq(OWNER), eq(PageRequest.of(page.getNumber(), page.getSize(), page.getSort())));
  }

  @Test
  void generatedInterfaceRegistersEveryOrderRouteOnce() {
    Map<String, Map<RequestMethod, Long>> registered =
        handlerMapping.getHandlerMethods().entrySet().stream()
            .filter(entry -> OrderController.class.isAssignableFrom(entry.getValue().getBeanType()))
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
    assertThat(registered).hasSize(6);
    registered
        .values()
        .forEach(methods -> methods.values().forEach(count -> assertThat(count).isEqualTo(1L)));
  }

  private OrdersApi client(UUID user, boolean admin) {
    String token = admin ? ADMIN_TOKEN : user.equals(OWNER) ? OWNER_TOKEN : OTHER_TOKEN;
    ApiClient apiClient =
        new ApiClient()
            .setHost("localhost")
            .setPort(serverContext.getWebServer().getPort())
            .setBasePath("")
            .setRequestInterceptor(request -> request.header("Authorization", "Bearer " + token));
    return new OrdersApi(apiClient);
  }

  private static Jwt jwt(String token, UUID subject, boolean admin) {
    Instant now = Instant.now();
    return Jwt.withTokenValue(token)
        .header("alg", "none")
        .subject(subject.toString())
        .issuedAt(now)
        .expiresAt(now.plusSeconds(60))
        .claim("roles", admin ? List.of("ROLE_ADMIN") : List.of("ROLE_CUSTOMER"))
        .claim(
            "permissions",
            admin
                ? List.of(
                    Permissions.ORDERS_UPDATE, Permissions.ORDERS_READ, Permissions.ORDERS_CANCEL)
                : List.of(
                    Permissions.ORDERS_CREATE, Permissions.ORDERS_READ, Permissions.ORDERS_CANCEL))
        .build();
  }

  private static OrderRequest validRequest() {
    return new OrderRequest()
        .items(List.of(new OrderItemRequest().productId("product-1").quantity(1)))
        .paymentMethod("CARD");
  }

  private HttpResponse<String> rawGetOrdersByStatus(String status) throws Exception {
    HttpRequest request =
        HttpRequest.newBuilder(
                URI.create(
                    "http://localhost:"
                        + serverContext.getWebServer().getPort()
                        + "/api/v1/orders/status/"
                        + status))
            .header("Authorization", "Bearer " + ADMIN_TOKEN)
            .GET()
            .build();
    return HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());
  }

  private static OrderResponse response(
      String id, UUID user, com.project.order.model.OrderStatus status) {
    return new OrderResponse(
        id,
        "ORD-1",
        user,
        null,
        com.project.order.generated.model.OrderResponse.StatusEnum.valueOf(status.name()),
        List.of(),
        BigDecimal.ZERO,
        BigDecimal.ZERO,
        BigDecimal.ZERO,
        BigDecimal.ZERO,
        BigDecimal.ZERO,
        "INR",
        null,
        null,
        null,
        "CARD",
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null);
  }

  @Configuration(proxyBeanMethods = false)
  @TestComponent
  @EnableAutoConfiguration(
      excludeName = {
        "org.springframework.boot.mongodb.autoconfigure.MongoAutoConfiguration",
        "org.springframework.boot.data.mongodb.autoconfigure.DataMongoAutoConfiguration",
        "org.springframework.boot.data.mongodb.autoconfigure.DataMongoRepositoriesAutoConfiguration",
        "org.springframework.boot.data.redis.autoconfigure.DataRedisAutoConfiguration",
        "org.springframework.boot.data.redis.autoconfigure.DataRedisRepositoriesAutoConfiguration",
        "org.springframework.boot.kafka.autoconfigure.KafkaAutoConfiguration",
        "org.springframework.cloud.netflix.eureka.EurekaClientAutoConfiguration"
      })
  @Import({
    OrderController.class,
    SecurityConfig.class,
    OrderApiMapperImpl.class,
    com.project.order.validation.OrderAccessValidator.class,
    GlobalExceptionHandler.class
  })
  static class TestApplication {}
}
