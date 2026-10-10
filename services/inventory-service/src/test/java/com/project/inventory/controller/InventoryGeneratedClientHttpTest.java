package com.project.inventory.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.project.inventory.config.SecurityConfig;
import com.project.inventory.application.mapper.InventoryApiMapper;
import com.project.inventory.generated.mapper.InventoryApiMapperImpl;
import com.project.inventory.domain.exception.InsufficientStockException;
import com.project.inventory.generated.testclient.api.InventoryApi;
import com.project.inventory.generated.testclient.invoker.ApiClient;
import com.project.inventory.generated.testclient.invoker.ApiException;
import com.project.inventory.service.InventoryService;
import com.project.common.exception.GlobalExceptionHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestComponent;
import org.springframework.boot.web.servlet.context.ServletWebServerApplicationContext;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SpringBootTest(
        classes = InventoryGeneratedClientHttpTest.TestApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "spring.cloud.config.enabled=false",
                "spring.config.import=optional:file:/dev/null",
                "spring.cloud.discovery.enabled=false",
                "eureka.client.enabled=false",
                "management.endpoints.enabled-by-default=false"
        })
class InventoryGeneratedClientHttpTest {

    private static final String SERVICE_TOKEN = "inventory-service-token";
    private static final String NO_SCOPE_TOKEN = "inventory-no-scope-token";
    private static final String ADMIN_TOKEN = "inventory-admin-token";

    @Autowired
    private ServletWebServerApplicationContext serverContext;

    @Autowired
    @Qualifier("requestMappingHandlerMapping")
    private RequestMappingHandlerMapping handlerMapping;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private TestRestTemplate http;

    @Autowired
    private InventoryApiMapper apiMapper;

    @MockBean
    private InventoryService inventoryService;

    @MockBean
    private JwtDecoder jwtDecoder;

    private InventoryApi serviceApi;
    private InventoryApi noScopeApi;
    private InventoryApi adminApi;

    @BeforeEach
    void setUpClientAndJwtDecoder() {
        when(jwtDecoder.decode(anyString())).thenAnswer(call -> {
            String token = call.getArgument(0);
            Instant now = Instant.now();
            Jwt.Builder builder = Jwt.withTokenValue(token).header("alg", "none")
                    .subject("order-service").issuedAt(now).expiresAt(now.plusSeconds(60))
                    .claim("token_type", "service");
            if (SERVICE_TOKEN.equals(token)) builder.claim("scope", "inventory.write");
            if (ADMIN_TOKEN.equals(token)) builder.subject("admin-user").claim("token_type", "user")
                    .claim("roles", List.of("ROLE_ADMIN"))
                    .claim("permissions", List.of("inventory:write"));
            return builder.build();
        });
        serviceApi = client(SERVICE_TOKEN);
        noScopeApi = client(NO_SCOPE_TOKEN);
        adminApi = client(ADMIN_TOKEN);
    }

    @Test
    void generatedClientKeepsProductLookupDistinctFromRecordMutationIds() throws Exception {
        var domainResponse = new com.project.inventory.generated.model.InventoryResponse()
                .id("record-42").productId("product-7").sku("SKU-7").quantity(8).reservedQuantity(2)
                .availableQuantity(6).lowStockThreshold(1).location(null)
                .lastRestockedAt(LocalDateTime.parse("2026-10-01T10:00:00"))
                .createdAt(LocalDateTime.parse("2026-10-01T09:00:00"))
                .updatedAt(LocalDateTime.parse("2026-10-01T10:00:00"));
        when(inventoryService.getByProductId("product-7")).thenReturn(domainResponse);
        when(inventoryService.updateInventory(eq("record-42"), eq(
                new com.project.inventory.generated.model.InventoryRequest().productId("product-7").sku("SKU-7"))))
                .thenReturn(domainResponse);

        var productLookup = adminApi.getInventoryByProduct("product-7");
        var updated = adminApi.updateInventory("record-42",
                new com.project.inventory.generated.testclient.model.InventoryRequest()
                        .productId("product-7").sku("SKU-7"));
        adminApi.deleteInventory("record-42");

        assertThat(productLookup.getId()).isEqualTo("record-42");
        assertThat(productLookup.getProductId()).isEqualTo("product-7");
        assertThat(productLookup.getLocation_JsonNullable().isPresent()).isTrue();
        assertThat(productLookup.getLocation()).isNull();
        assertThat(productLookup.getLastRestockedAt()).isEqualTo(LocalDateTime.parse("2026-10-01T10:00:00"));
        assertThat(updated.getQuantity()).isEqualTo(8);
        verify(inventoryService).getByProductId("product-7");
        verify(inventoryService).updateInventory("record-42",
                new com.project.inventory.generated.model.InventoryRequest().productId("product-7").sku("SKU-7"));
        verify(inventoryService).deleteInventory("record-42");
    }

    @Test
    void scopedServiceCanReserveStockAndInsufficientStockKeepsConflictStatus() throws Exception {
        var domainResponse = inventoryResponse("product-7", 5, 2);
        when(inventoryService.reserveStock("product-7", 2, "order-7")).thenReturn(domainResponse);
        when(inventoryService.reserveStock("product-7", 9, "order-8"))
                .thenThrow(new InsufficientStockException("Insufficient stock for product product-7"));

        var reserved = serviceApi.reserveStock("product-7",
                new com.project.inventory.generated.testclient.model.StockReservationRequest()
                        .quantity(2).orderId("order-7"));
        ApiException insufficient = catchThrowableOfType(() -> serviceApi.reserveStock("product-7",
                new com.project.inventory.generated.testclient.model.StockReservationRequest()
                        .quantity(9).orderId("order-8")), ApiException.class);

        assertThat(reserved.getReservedQuantity()).isEqualTo(2);
        assertThat(insufficient.getCode()).isEqualTo(HttpStatus.CONFLICT.value());
        assertThat(insufficient.getResponseBody()).contains("INSUFFICIENT_STOCK");
        verify(inventoryService).reserveStock("product-7", 2, "order-7");
    }

    @Test
    void missingOrNullReservationQuantityReturnsValidationErrorForEveryTransition() {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(SERVICE_TOKEN);
        headers.setContentType(MediaType.APPLICATION_JSON);
        List<String> payloads = List.of(
                "{\"orderId\":\"order-10\"}",
                "{\"quantity\":null,\"orderId\":\"order-10\"}");
        List<String> transitions = List.of("reserve", "commit", "release");

        for (String transition : transitions) {
            for (String payload : payloads) {
                ResponseEntity<String> response = http.exchange(
                        "http://localhost:" + serverContext.getWebServer().getPort()
                                + "/api/v1/inventory/product-7/" + transition,
                        HttpMethod.POST, new HttpEntity<>(payload, headers), String.class);

                assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
                assertThat(response.getBody()).contains("Quantity must be positive");
            }
        }

        verify(inventoryService, never()).reserveStock(anyString(), anyInt(), anyString());
        verify(inventoryService, never()).commitStock(anyString(), anyInt(), anyString());
        verify(inventoryService, never()).releaseStock(anyString(), anyInt(), anyString());
    }

    @Test
    void reservationRejectsServiceWithoutInventoryWriteScope() throws Exception {
        ApiException denied = catchThrowableOfType(() -> noScopeApi.reserveStock("product-7",
                new com.project.inventory.generated.testclient.model.StockReservationRequest()
                        .quantity(1).orderId("order-9")), ApiException.class);

        assertThat(denied.getCode()).isEqualTo(HttpStatus.FORBIDDEN.value());
    }

    @Test
    void generatedRequestValidationRetainsInventoryMinimumMessage() throws Exception {
        ApiException invalid = catchThrowableOfType(() -> adminApi.updateInventory("record-42",
                new com.project.inventory.generated.testclient.model.InventoryRequest()
                        .productId("product-7").sku("SKU-7").quantity(-1)), ApiException.class);

        assertThat(invalid.getCode()).isEqualTo(HttpStatus.BAD_REQUEST.value());
        assertThat(invalid.getResponseBody()).contains("Quantity must be zero or positive");
    }

    @Test
    void anonymousInventoryRequestGetsTheExistingBodylessBearerChallenge() throws Exception {
        ApiException unauthorized = catchThrowableOfType(
                () -> client(null).getInventoryByProduct("product-7"), ApiException.class);

        assertThat(unauthorized.getCode()).isEqualTo(HttpStatus.UNAUTHORIZED.value());
        assertThat(unauthorized.getResponseBody()).isNullOrEmpty();
        assertThat(unauthorized.getResponseHeaders().firstValue(HttpHeaders.WWW_AUTHENTICATE).orElse(null))
                .contains("Bearer");
    }

    @Test
    void generatedInterfaceRegistersEachInventoryHttpOperationExactlyOnce() {
        Map<String, Set<RequestMethod>> expected = Map.of(
                "/api/v1/inventory", Set.of(RequestMethod.GET, RequestMethod.POST),
                "/api/v1/inventory/{id}", Set.of(RequestMethod.GET, RequestMethod.PUT, RequestMethod.DELETE),
                "/api/v1/inventory/sku/{sku}", Set.of(RequestMethod.GET),
                "/api/v1/inventory/low-stock", Set.of(RequestMethod.GET),
                "/api/v1/inventory/{productId}/add-stock", Set.of(RequestMethod.POST),
                "/api/v1/inventory/{productId}/reserve", Set.of(RequestMethod.POST),
                "/api/v1/inventory/{productId}/commit", Set.of(RequestMethod.POST),
                "/api/v1/inventory/{productId}/release", Set.of(RequestMethod.POST),
                "/api/v1/inventory/{productId}/check", Set.of(RequestMethod.GET));
        var registered = handlerMapping.getHandlerMethods().entrySet().stream()
                .filter(entry -> InventoryController.class.isAssignableFrom(entry.getValue().getBeanType()))
                .flatMap(entry -> entry.getKey().getMethodsCondition().getMethods().stream()
                        .flatMap(method -> entry.getKey().getPathPatternsCondition().getPatternValues().stream()
                                .map(path -> Map.entry(path, method))))
                .collect(java.util.stream.Collectors.groupingBy(Map.Entry::getKey,
                        java.util.stream.Collectors.groupingBy(Map.Entry::getValue,
                                java.util.stream.Collectors.counting())));

        assertThat(registered).hasSize(expected.size());
        expected.forEach((path, methods) -> {
            assertThat(registered).containsKey(path);
            assertThat(registered.get(path).keySet()).containsExactlyInAnyOrderElementsOf(methods);
            methods.forEach(method -> assertThat(registered.get(path).get(method)).isEqualTo(1L));
        });
    }

    @Test
    void generatedPageBoundaryMatchesSpringPageJsonForSortedAndEmptyPages() throws Exception {
        var item = inventoryResponse("product-7", 8, 2);
        Page<com.project.inventory.generated.model.InventoryResponse> sorted = new PageImpl<>(List.of(item),
                PageRequest.of(0, 10, Sort.by(Sort.Order.desc("createdAt"), Sort.Order.asc("productId"))), 1);
        Page<com.project.inventory.generated.model.InventoryResponse> empty = Page.empty(
                PageRequest.of(2, 5, Sort.unsorted()));

        assertThat(objectMapper.readTree(objectMapper.writeValueAsString(apiMapper.toApi(sorted))))
                .isEqualTo(objectMapper.readTree(objectMapper.writeValueAsString(sorted)));
        assertThat(objectMapper.readTree(objectMapper.writeValueAsString(apiMapper.toApi(empty))))
                .isEqualTo(objectMapper.readTree(objectMapper.writeValueAsString(empty)));
    }

    private InventoryApi client(String token) {
        ApiClient client = new ApiClient().setHost("localhost")
                .setPort(serverContext.getWebServer().getPort()).setBasePath("");
        if (token != null) client.setRequestInterceptor(
                request -> request.header(HttpHeaders.AUTHORIZATION, "Bearer " + token));
        return new InventoryApi(client);
    }

    private static com.project.inventory.generated.model.InventoryResponse inventoryResponse(
            String productId, int quantity, int reserved) {
        return new com.project.inventory.generated.model.InventoryResponse()
                .id("record-42").productId(productId).sku("SKU-7").quantity(quantity).reservedQuantity(reserved)
                .availableQuantity(quantity - reserved).lowStockThreshold(1).location("A1")
                .createdAt(LocalDateTime.parse("2026-10-01T09:00:00"))
                .updatedAt(LocalDateTime.parse("2026-10-01T10:00:00"));
    }

    @Configuration(proxyBeanMethods = false)
    @TestComponent
    @EnableAutoConfiguration(excludeName = {
            "org.springframework.boot.autoconfigure.mongo.MongoAutoConfiguration",
            "org.springframework.boot.autoconfigure.data.mongo.MongoDataAutoConfiguration",
            "org.springframework.boot.autoconfigure.data.mongo.MongoRepositoriesAutoConfiguration",
            "org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration",
            "org.springframework.boot.autoconfigure.data.redis.RedisRepositoriesAutoConfiguration",
            "org.springframework.boot.autoconfigure.kafka.KafkaAutoConfiguration",
            "org.springframework.cloud.netflix.eureka.EurekaClientAutoConfiguration"
    })
    @Import({InventoryController.class, SecurityConfig.class,
            InventoryApiMapperImpl.class,
            com.project.inventory.application.validator.InventoryValidator.class, GlobalExceptionHandler.class})
    static class TestApplication { }
}
