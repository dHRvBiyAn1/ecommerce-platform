package com.project.product_service.controller;

import com.project.common.exception.GlobalExceptionHandler;
import com.project.product_service.config.SecurityConfig;
import com.project.product_service.application.mapper.ProductApiMapper;
import com.project.product_service.generated.model.CategoryResponse;
import com.project.product_service.generated.model.ProductResponse;
import com.project.product_service.generated.model.ProductApprovalStatus;
import com.project.product_service.generated.testclient.api.CategoriesApi;
import com.project.product_service.generated.testclient.api.ProductsApi;
import com.project.product_service.generated.testclient.invoker.ApiClient;
import com.project.product_service.generated.testclient.invoker.ApiException;
import com.project.product_service.service.CategoryService;
import com.project.product_service.service.ProductService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestComponent;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.web.servlet.context.ServletWebServerApplicationContext;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.times;
import org.mockito.ArgumentCaptor;

@SpringBootTest(
        classes = ProductGeneratedClientHttpTest.TestApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "spring.cloud.config.enabled=false",
                "spring.config.import=optional:file:/dev/null",
                "spring.cloud.discovery.enabled=false",
                "eureka.client.enabled=false",
                "management.endpoints.enabled-by-default=false"
        })
class ProductGeneratedClientHttpTest {

    private static final UUID SELLER_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID OTHER_SELLER_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final String SELLER_TOKEN = "product-seller-token";
    private static final String ADMIN_TOKEN = "product-admin-token";

    @Autowired
    private ServletWebServerApplicationContext serverContext;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    @Qualifier("requestMappingHandlerMapping")
    private RequestMappingHandlerMapping handlerMapping;

    @MockBean
    private ProductService productService;

    @MockBean
    private CategoryService categoryService;

    @MockBean
    private JwtDecoder jwtDecoder;

    private ProductsApi productsApi;
    private CategoriesApi categoriesApi;

    @BeforeEach
    void setUpClientAndJwtDecoder() {
        when(jwtDecoder.decode(SELLER_TOKEN)).thenAnswer(ignored -> {
            Instant now = Instant.now();
            return Jwt.withTokenValue(SELLER_TOKEN)
                    .header("alg", "none")
                    .subject(SELLER_ID.toString())
                    .issuedAt(now)
                    .expiresAt(now.plusSeconds(60))
                    .claim("roles", List.of("ROLE_SELLER"))
                    .claim("permissions", List.of(
                            "products:create", "products:update", "products:delete"))
                    .build();
        });
        when(jwtDecoder.decode(ADMIN_TOKEN)).thenAnswer(ignored -> {
            Instant now = Instant.now();
            return Jwt.withTokenValue(ADMIN_TOKEN)
                    .header("alg", "none")
                    .subject(SELLER_ID.toString())
                    .issuedAt(now)
                    .expiresAt(now.plusSeconds(60))
                    .claim("roles", List.of("ROLE_ADMIN"))
                    .claim("permissions", List.of("products:read"))
                    .build();
        });
        ApiClient client = new ApiClient()
                .setHost("localhost")
                .setPort(serverContext.getWebServer().getPort())
                .setBasePath("")
                .setRequestInterceptor(request -> request.header(
                        HttpHeaders.AUTHORIZATION, "Bearer " + SELLER_TOKEN));
        productsApi = new ProductsApi(client);
        categoriesApi = new CategoriesApi(client);
    }

    @Test
    void generatedClientReadsPublicProductAndCategoryAndPreservesPricePrecision() throws Exception {
        ProductResponse response = product("desk-1", new BigDecimal("149.9900"));
        when(productService.getProduct("desk-1")).thenReturn(response);
        when(categoryService.getAllCategories()).thenReturn(List.of(new CategoryResponse()
                .id("furniture").name("Furniture").active(true)
                .createdAt(LocalDateTime.parse("2026-10-01T12:34:56"))));

        var decodedProduct = productsApi.getProduct("desk-1");
        assertThat(decodedProduct.getPrice()).isEqualByComparingTo("149.9900");
        assertThat(decodedProduct.getCreatedAt()).isEqualTo(LocalDateTime.parse("2026-10-01T12:34:56"));

        var decodedCategories = categoriesApi.getAllCategories();
        assertThat(decodedCategories).hasSize(1);
        assertThat(decodedCategories.getFirst().getName()).isEqualTo("Furniture");
        assertThat(decodedCategories.getFirst().getCreatedAt())
                .isEqualTo(LocalDateTime.parse("2026-10-01T12:34:56"));
    }

    @Test
    void generatedClientPreservesPagedResultsAndRequestDefaults() throws Exception {
        when(productService.getAllActiveProducts(any())).thenAnswer(invocation -> {
            Pageable pageable = invocation.getArgument(0);
            if (pageable.getPageNumber() == 2) {
                return new PageImpl<>(List.of(product("desk-2", new BigDecimal("2.35"))), pageable, 15);
            }
            return new PageImpl<>(List.of(), pageable, 0);
        });

        var decoded = productsApi.getAllActiveProducts(2, 7, List.of("price,desc"));
        assertThat(decoded.getContent()).hasSize(1);
        assertThat(decoded.getContent().getFirst().getPrice()).isEqualByComparingTo("2.35");
        assertThat(decoded.getNumber()).isEqualTo(2);
        assertThat(decoded.getSize()).isEqualTo(7);
        assertThat(decoded.getTotalElements()).isEqualTo(15L);
        assertThat(decoded.getTotalPages()).isEqualTo(3);
        assertThat(decoded.getFirst()).isFalse();
        assertThat(decoded.getLast()).isTrue();
        assertThat(decoded.getPageable().getOffset()).isEqualTo(14L);
        assertThat(decoded.getSort().getSorted()).isTrue();
        assertThat(decoded.getSort().getEmpty()).isFalse();
        assertThat(decoded.getPageable().getSort().getSorted()).isTrue();

        assertThat(productsApi.getAllActiveProducts(null, null, null).getSize()).isEqualTo(20);
        ArgumentCaptor<Pageable> pageables = ArgumentCaptor.forClass(Pageable.class);
        verify(productService, times(2)).getAllActiveProducts(pageables.capture());
        assertThat(pageables.getAllValues().get(0).getPageNumber()).isEqualTo(2);
        assertThat(pageables.getAllValues().get(0).getPageSize()).isEqualTo(7);
        assertThat(pageables.getAllValues().get(0).getSort().getOrderFor("price").getDirection())
                .isEqualTo(Sort.Direction.DESC);
        Pageable defaults = pageables.getAllValues().get(1);
        assertThat(defaults.getPageNumber()).isZero();
        assertThat(defaults.getPageSize()).isEqualTo(20);
        assertThat(defaults.getSort().getOrderFor("createdAt").getDirection()).isEqualTo(Sort.Direction.DESC);
    }

    @Test
    void rawProductUpdateDistinguishesOmittedAttributesFromExplicitEmptyMap() throws Exception {
        when(productService.updateProduct(eq("desk-1"), any(), eq(false)))
                .thenReturn(product("desk-1", new BigDecimal("10.00")));
        String body = """
                {"sku":"SKU-DESK","name":"Edited desk","categoryId":"furniture",
                 "price":10.00,"stockQuantity":1}
                """;
        var explicitEmpty = objectMapper.readTree(body);
        ((com.fasterxml.jackson.databind.node.ObjectNode) explicitEmpty).putObject("attributes");
        var http = java.net.http.HttpClient.newHttpClient();
        for (String payload : List.of(body, objectMapper.writeValueAsString(explicitEmpty))) {
            var response = http.send(java.net.http.HttpRequest.newBuilder(java.net.URI.create(
                            "http://localhost:" + serverContext.getWebServer().getPort() + "/api/v1/products/desk-1"))
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + SELLER_TOKEN)
                    .header(HttpHeaders.CONTENT_TYPE, "application/json")
                    .PUT(java.net.http.HttpRequest.BodyPublishers.ofString(payload)).build(),
                    java.net.http.HttpResponse.BodyHandlers.ofString());
            assertThat(response.statusCode()).isEqualTo(200);
        }
        var requests = ArgumentCaptor.forClass(com.project.product_service.generated.model.ProductRequest.class);
        verify(productService, times(2)).updateProduct(eq("desk-1"), requests.capture(), eq(false));
        assertThat(requests.getAllValues().get(0).getAttributes()).isNull();
        assertThat(requests.getAllValues().get(1).getAttributes()).isEmpty();
        assertThat(requests.getAllValues()).allSatisfy(request ->
                assertThat(request.getSellerId()).isEqualTo(SELLER_ID));
    }

    @Test
    void requiredTextAcceptsMultilineContentAndRejectsLegacyWhitespaceOnlyValues() throws Exception {
        var multiline = new com.project.product_service.generated.testclient.model.ProductRequest()
                .sku("SKU-MULTILINE").name("Desk\nLamp").categoryId("furniture")
                .price(new BigDecimal("10.00")).stockQuantity(1);
        when(productService.createProduct(any(com.project.product_service.generated.model.ProductRequest.class),
                eq(false))).thenReturn(product("multiline", new BigDecimal("10.00")));

        productsApi.createProduct(multiline);

        var captured = org.mockito.ArgumentCaptor.forClass(com.project.product_service.generated.model.ProductRequest.class);
        verify(productService).createProduct(captured.capture(), eq(false));
        assertThat(captured.getValue().getName()).isEqualTo("Desk\nLamp");

        var whitespaceOnly = new com.project.product_service.generated.testclient.model.ProductRequest()
                .sku("SKU-BLANK").name(" \n\t ").categoryId("furniture")
                .price(new BigDecimal("10.00")).stockQuantity(1);
        ApiException invalid = catchThrowableOfType(() -> productsApi.createProduct(whitespaceOnly), ApiException.class);
        assertThat(invalid.getCode()).isEqualTo(HttpStatus.BAD_REQUEST.value());
        assertThat(invalid.getResponseBody()).contains("must not be blank");

        var controlOnly = new com.project.product_service.generated.testclient.model.ProductRequest()
                .sku("SKU-CONTROL").name(Character.toString(0)).categoryId("furniture")
                .price(new BigDecimal("10.00")).stockQuantity(1);
        ApiException invalidControl = catchThrowableOfType(() -> productsApi.createProduct(controlOnly), ApiException.class);
        assertThat(invalidControl.getCode()).isEqualTo(HttpStatus.BAD_REQUEST.value());
        assertThat(invalidControl.getResponseBody()).contains("must not be blank");
    }

    @Test
    void generatedModerationEnumAndSpringTrimmedBindingReachTheService() throws Exception {
        when(productService.listByApprovalStatus(any(), any()))
                .thenAnswer(invocation -> Page.empty(invocation.getArgument(1)));
        ApiClient adminClient = adminClient();
        ProductsApi api = new ProductsApi(adminClient);

        api.listByApprovalStatus(
                com.project.product_service.generated.testclient.model.ProductApprovalStatus.APPROVED,
                0, 20, null);
        var trimmed = java.net.http.HttpClient.newHttpClient().send(
                java.net.http.HttpRequest.newBuilder(java.net.URI.create(
                                "http://localhost:" + serverContext.getWebServer().getPort()
                                        + "/api/v1/products/admin/by-status?status=%20APPROVED%20"))
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + ADMIN_TOKEN)
                        .GET().build(),
                java.net.http.HttpResponse.BodyHandlers.ofString());

        assertThat(trimmed.statusCode()).isEqualTo(HttpStatus.OK.value());
        var statuses = ArgumentCaptor.forClass(com.project.product_service.model.ProductApprovalStatus.class);
        var pageables = ArgumentCaptor.forClass(Pageable.class);
        verify(productService, times(2)).listByApprovalStatus(statuses.capture(), pageables.capture());
        assertThat(statuses.getAllValues()).containsExactly(
                com.project.product_service.model.ProductApprovalStatus.APPROVED,
                com.project.product_service.model.ProductApprovalStatus.APPROVED);
        assertThat(pageables.getAllValues().get(0).getPageSize()).isEqualTo(20);
        assertThat(pageables.getAllValues().get(1).getPageSize()).isEqualTo(50);
    }

    @Test
    void invalidModerationStatusRemainsBadRequestTypeMismatch() throws Exception {
        var invalid = java.net.http.HttpClient.newHttpClient().send(
                java.net.http.HttpRequest.newBuilder(java.net.URI.create(
                                "http://localhost:" + serverContext.getWebServer().getPort()
                                        + "/api/v1/products/admin/by-status?status=BOGUS"))
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + ADMIN_TOKEN)
                        .GET().build(),
                java.net.http.HttpResponse.BodyHandlers.ofString());

        assertThat(invalid.statusCode()).isEqualTo(HttpStatus.BAD_REQUEST.value());
        assertThat(invalid.body())
                .contains("\"code\":\"TYPE_MISMATCH\"", "Parameter 'status' has invalid value: BOGUS");
        org.mockito.Mockito.verifyNoInteractions(productService);
    }

    @Test
    void mappedPageMetadataMatchesSpringPageJsonForSortedAndEmptyPages() throws Exception {
        var sortedPageable = PageRequest.of(1, 3, Sort.by(Sort.Order.desc("createdAt")));
        var sortedPage = new PageImpl<>(List.of(product("desk-3", new BigDecimal("4.50"))),
                sortedPageable, 10);
        assertThat(objectMapper.readTree(objectMapper.writeValueAsBytes(ProductApiMapper.toApi(sortedPage))))
                .isEqualTo(objectMapper.readTree(objectMapper.writeValueAsBytes(sortedPage)));

        Page<ProductResponse> emptyPage = new PageImpl<>(List.of(), PageRequest.of(0, 20), 0);
        assertThat(objectMapper.readTree(objectMapper.writeValueAsBytes(ProductApiMapper.toApi(emptyPage))))
                .isEqualTo(objectMapper.readTree(objectMapper.writeValueAsBytes(emptyPage)));
    }

    @Test
    void securityFilterAndSellerOwnershipRemainEnforcedOverRealHttp() throws Exception {
        ApiClient anonymousClient = new ApiClient()
                .setHost("localhost")
                .setPort(serverContext.getWebServer().getPort())
                .setBasePath("");
        ApiException anonymous = catchThrowableOfType(
                () -> new ProductsApi(anonymousClient).getProductsBySeller(SELLER_ID, 0, 20, null),
                ApiException.class);
        assertThat(anonymous.getCode()).isEqualTo(HttpStatus.UNAUTHORIZED.value());
        assertThat(anonymous.getResponseBody()).isNullOrEmpty();
        assertThat(anonymous.getResponseHeaders().firstValue(HttpHeaders.WWW_AUTHENTICATE).orElse(null))
                .contains("Bearer");

        when(productService.getProductsBySeller(eq(OTHER_SELLER_ID), any())).thenReturn(
                new PageImpl<>(List.of(), PageRequest.of(0, 20), 0));
        ApiException foreignSeller = catchThrowableOfType(
                () -> productsApi.getProductsBySeller(OTHER_SELLER_ID, 0, 20, null), ApiException.class);
        assertThat(foreignSeller.getCode()).isEqualTo(HttpStatus.FORBIDDEN.value());
    }

    @Test
    void generatedInterfacesRegisterEveryOwnedRouteOnlyOnce() {
        Map<String, Set<RequestMethod>> expected = Map.ofEntries(
                Map.entry("/api/v1/products", Set.of(RequestMethod.GET, RequestMethod.POST)),
                Map.entry("/api/v1/products/search", Set.of(RequestMethod.GET)),
                Map.entry("/api/v1/products/category/{categoryId}", Set.of(RequestMethod.GET)),
                Map.entry("/api/v1/products/{id}", Set.of(RequestMethod.GET, RequestMethod.PUT, RequestMethod.DELETE)),
                Map.entry("/api/v1/products/seller/{sellerId}", Set.of(RequestMethod.GET)),
                Map.entry("/api/v1/products/filter", Set.of(RequestMethod.GET)),
                Map.entry("/api/v1/products/filter/attribute", Set.of(RequestMethod.GET)),
                Map.entry("/api/v1/products/seller", Set.of(RequestMethod.GET)),
                Map.entry("/api/v1/products/{id}/stock", Set.of(RequestMethod.PATCH)),
                Map.entry("/api/v1/products/{id}/active", Set.of(RequestMethod.PUT)),
                Map.entry("/api/v1/products/{id}/approve", Set.of(RequestMethod.PUT)),
                Map.entry("/api/v1/products/{id}/reject", Set.of(RequestMethod.PUT)),
                Map.entry("/api/v1/products/admin/by-status", Set.of(RequestMethod.GET)),
                Map.entry("/api/v1/categories/{id}/products", Set.of(RequestMethod.GET)),
                Map.entry("/api/v1/categories", Set.of(RequestMethod.GET, RequestMethod.POST)),
                Map.entry("/api/v1/categories/{id}", Set.of(RequestMethod.GET, RequestMethod.PUT, RequestMethod.DELETE)));

        var registered = handlerMapping.getHandlerMethods().entrySet().stream()
                .filter(entry -> ProductController.class.isAssignableFrom(entry.getValue().getBeanType())
                        || CategoryController.class.isAssignableFrom(entry.getValue().getBeanType()))
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

    private ApiClient adminClient() {
        return new ApiClient()
                .setHost("localhost")
                .setPort(serverContext.getWebServer().getPort())
                .setBasePath("")
                .setRequestInterceptor(request -> request.header(
                        HttpHeaders.AUTHORIZATION, "Bearer " + ADMIN_TOKEN));
    }

    private static ProductResponse product(String id, BigDecimal price) {
        return new ProductResponse().id(id).sku("SKU-" + id).name("Desk")
                .categoryId("furniture").categoryName("Furniture").price(price)
                .stockQuantity(3).imageUrls(List.of()).sellerId(SELLER_ID).active(true)
                .approvalStatus(ProductApprovalStatus.APPROVED).attributes(Map.of())
                .createdAt(LocalDateTime.parse("2026-10-01T12:34:56"));
    }

    @Configuration(proxyBeanMethods = false)
    @TestComponent
    @EnableAutoConfiguration(excludeName = {
            "org.springframework.boot.autoconfigure.mongo.MongoAutoConfiguration",
            "org.springframework.boot.autoconfigure.data.mongo.MongoDataAutoConfiguration",
            "org.springframework.boot.autoconfigure.data.mongo.MongoRepositoriesAutoConfiguration",
            "org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration",
            "org.springframework.boot.autoconfigure.data.redis.RedisRepositoriesAutoConfiguration",
            "org.springframework.boot.autoconfigure.data.elasticsearch.ElasticsearchDataAutoConfiguration",
            "org.springframework.boot.autoconfigure.data.elasticsearch.ElasticsearchRepositoriesAutoConfiguration",
            "org.springframework.boot.autoconfigure.kafka.KafkaAutoConfiguration",
            "org.springframework.cloud.netflix.eureka.EurekaClientAutoConfiguration"
    })
    @Import({ProductController.class, CategoryController.class, SecurityConfig.class, GlobalExceptionHandler.class})
    static class TestApplication {
    }
}
