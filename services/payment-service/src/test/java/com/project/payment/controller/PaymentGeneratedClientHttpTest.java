package com.project.payment.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.project.common.constant.Permissions;
import com.project.common.exception.GlobalExceptionHandler;
import com.project.payment.application.mapper.PaymentMapper;
import com.project.payment.application.validator.PaymentAccessValidator;
import com.project.payment.config.SecurityConfig;
import com.project.payment.generated.model.PaymentInitiationResponse;
import com.project.payment.generated.model.PaymentResponse;
import com.project.payment.model.PaymentStatus;
import com.project.payment.generated.testclient.api.PaymentsApi;
import com.project.payment.generated.testclient.invoker.ApiClient;
import com.project.payment.generated.testclient.invoker.ApiException;
import com.project.payment.generated.testclient.model.PaymentRequest;
import com.project.payment.generated.testclient.model.RefundRequest;
import com.project.payment.service.PaymentService;
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
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.HexFormat;
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

@SpringBootTest(classes = PaymentGeneratedClientHttpTest.TestApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {"spring.cloud.config.enabled=false", "spring.config.import=optional:file:/dev/null",
                "spring.cloud.discovery.enabled=false", "eureka.client.enabled=false",
                "management.endpoints.enabled-by-default=false", "payment.webhook.secret=webhook-test-secret",
                "payment.webhook.tolerance-seconds=300", "stripe.webhook-secret=stripe-test-secret"})
class PaymentGeneratedClientHttpTest {

    private static final UUID OWNER = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID OTHER = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final String TOKEN = "payment-test-token";

    @Autowired private ServletWebServerApplicationContext serverContext;
    @Autowired private ObjectMapper objectMapper;
    @Autowired @Qualifier("requestMappingHandlerMapping") private RequestMappingHandlerMapping handlerMapping;
    @MockBean private PaymentService paymentService;
    @MockBean private JwtDecoder jwtDecoder;

    private PaymentsApi api;

    @BeforeEach
    void configureClientAndJwtDecoder() {
        when(jwtDecoder.decode(TOKEN)).thenAnswer(ignored -> Jwt.withTokenValue(TOKEN).header("alg", "none")
                .subject(OWNER.toString()).issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(60))
                .claim("token_type", "user").claim("roles", List.of("ROLE_CUSTOMER"))
                .claim("permissions", List.of(Permissions.PAYMENTS_READ, Permissions.PAYMENTS_REFUND)).build());
        ApiClient client = new ApiClient().setHost("localhost").setPort(serverContext.getWebServer().getPort())
                .setBasePath("").setRequestInterceptor(request -> request.header("Authorization", "Bearer " + TOKEN));
        api = new PaymentsApi(client);
    }

    @Test
    void generatedClientReceivesTheClientSecretOnlyOnFirstSuccessfulInitiation() throws Exception {
        var first = new PaymentInitiationResponse(payment("pay-1", OWNER, PaymentStatus.PENDING), "one-time-secret");
        var replay = new PaymentInitiationResponse(payment("pay-1", OWNER, PaymentStatus.PENDING), null);
        when(paymentService.createPayment(any(), eq(OWNER), eq(null), eq("payment-1"))).thenReturn(first, replay, replay);
        var request = new PaymentRequest().orderId("order-1").paymentMethod("CARD")
                .amount(new BigDecimal("0.01")).currency("USD");

        var created = api.initiatePaymentWithHttpInfo(request, "payment-1");
        var replayed = api.initiatePaymentWithHttpInfo(request, "payment-1");

        assertThat(created.getStatusCode()).isEqualTo(201);
        assertThat(created.getData().getData().getClientSecret()).isEqualTo("one-time-secret");
        assertThat(replayed.getData().getData().getClientSecret()).isNull();
        assertThat(replayed.getData().toString()).doesNotContain("one-time-secret");

        HttpResponse<String> rawResponse = rawInitiation("payment-1");
        assertThat(rawResponse.statusCode()).isEqualTo(201);
        assertThat(objectMapper.readTree(rawResponse.body()).path("data"))
                .isEqualTo(objectMapper.readTree(objectMapper.writeValueAsBytes(replay)));
        assertThat(objectMapper.readTree(rawResponse.body()).path("data").path("clientSecret").isNull()).isTrue();

        when(paymentService.createPayment(any(), eq(OWNER), eq(null), eq("payment-raw")))
                .thenReturn(first, replay);
        HttpResponse<String> rawFirst = rawInitiation("payment-raw");
        HttpResponse<String> rawReplay = rawInitiation("payment-raw");
        assertThat(rawFirst.statusCode()).isEqualTo(201);
        assertThat(rawReplay.statusCode()).isEqualTo(201);
        assertThat(objectMapper.readTree(rawFirst.body()).path("data"))
                .isEqualTo(objectMapper.readTree(objectMapper.writeValueAsBytes(first)));
        assertThat(objectMapper.readTree(rawReplay.body()).path("data"))
                .isEqualTo(objectMapper.readTree(objectMapper.writeValueAsBytes(replay)));
    }

    @Test
    void generatedClientChecksPaymentOwnershipAndForwardsRefundIdempotency() throws Exception {
        when(paymentService.getPayment("pay-1")).thenReturn(payment("pay-1", OTHER, PaymentStatus.PENDING));
        ApiException forbidden = catchThrowableOfType(() -> api.getPaymentById("pay-1"), ApiException.class);
        assertThat(forbidden.getCode()).isEqualTo(HttpStatus.FORBIDDEN.value());

        when(paymentService.refundPayment("pay-1", "duplicate", new BigDecimal("2.50"), "refund-1"))
                .thenReturn(payment("pay-1", OWNER, PaymentStatus.PARTIALLY_REFUNDED));
        var refunded = api.refundPaymentWithHttpInfo("pay-1",
                new RefundRequest().reason("duplicate").amount(new BigDecimal("2.50")), "refund-1");
        assertThat(refunded.getStatusCode()).isEqualTo(200);
        assertThat(refunded.getData().getData().getStatus().toString()).isEqualTo("PARTIALLY_REFUNDED");
        verify(paymentService).refundPayment("pay-1", "duplicate", new BigDecimal("2.50"), "refund-1");
    }

    @Test
    void generatedClientReadsPaymentsByReferenceAndOrderId() throws Exception {
        when(paymentService.getPaymentByReference("ref-1"))
                .thenReturn(payment("pay-1", OWNER, null));
        when(paymentService.getPaymentByOrderId("order-1"))
                .thenReturn(payment("pay-1", OWNER, PaymentStatus.PENDING));

        var byReference = api.getPaymentByReferenceWithHttpInfo("ref-1");
        var byOrderId = api.getPaymentByOrderIdWithHttpInfo("order-1");

        assertThat(byReference.getStatusCode()).isEqualTo(200);
        assertThat(byReference.getData().getData().getStatus()).isNull();
        assertThat(byOrderId.getStatusCode()).isEqualTo(200);
        assertThat(byOrderId.getData().getData().getStatus().toString()).isEqualTo("PENDING");
        verify(paymentService).getPaymentByReference("ref-1");
        verify(paymentService).getPaymentByOrderId("order-1");
    }

    @Test
    void generatedRequestValidationPreservesWhitespaceAndMoneyMessages() {
        ApiException rejected = catchThrowableOfType(() -> api.initiatePayment(
                new PaymentRequest().orderId(" ").paymentMethod("CARD"), null), ApiException.class);
        assertThat(rejected.getCode()).isEqualTo(HttpStatus.BAD_REQUEST.value());
        assertThat(rejected.getResponseBody()).contains("Order ID is required");
    }

    @Test
    void generatedClientPreservesLegacyNotBlankHandlingOfMultilineAndControlValues() throws ApiException {
        when(paymentService.createPayment(any(), eq(OWNER), eq(null), eq(null)))
                .thenReturn(new PaymentInitiationResponse(payment("pay-1", OWNER, PaymentStatus.PENDING), null));

        var multiline = api.initiatePaymentWithHttpInfo(
                new PaymentRequest().orderId("line1\nline2").paymentMethod("CARD"), null);
        assertThat(multiline.getStatusCode()).isEqualTo(201);

        ApiException controlOnly = catchThrowableOfType(() -> api.initiatePayment(
                new PaymentRequest().orderId(String.valueOf((char) 0)).paymentMethod("CARD"), null), ApiException.class);
        assertThat(controlOnly.getCode()).isEqualTo(HttpStatus.BAD_REQUEST.value());
        assertThat(controlOnly.getResponseBody()).contains("Order ID is required");
        var captured = org.mockito.ArgumentCaptor.forClass(com.project.payment.generated.model.PaymentRequest.class);
        verify(paymentService).createPayment(captured.capture(), eq(OWNER), eq(null), eq(null));
        assertThat(captured.getValue().getOrderId()).isEqualTo("line1\nline2");
    }

    @Test
    void generatedAndHandwrittenPaymentRoutesAreEachRegisteredOnce() {
        Map<String, Map<RequestMethod, Long>> registered = handlerMapping.getHandlerMethods().entrySet().stream()
                .filter(entry -> PaymentController.class.isAssignableFrom(entry.getValue().getBeanType()))
                .flatMap(entry -> entry.getKey().getMethodsCondition().getMethods().stream()
                        .flatMap(method -> entry.getKey().getPathPatternsCondition().getPatternValues().stream()
                                .map(path -> Map.entry(path, method))))
                .collect(java.util.stream.Collectors.groupingBy(Map.Entry::getKey,
                        java.util.stream.Collectors.groupingBy(Map.Entry::getValue, java.util.stream.Collectors.counting())));
        assertThat(registered).hasSize(8);
        assertThat(registered.values().stream().mapToLong(Map::size).sum()).isEqualTo(9);
        registered.values().forEach(methods -> methods.values().forEach(count -> assertThat(count).isEqualTo(1L)));
        assertThat(registered).containsKeys("/api/v1/payments/webhook", "/api/v1/payments/webhook/stripe");
    }

    @Test
    void rawInternalWebhookAuthenticatesTheExactBytesBeforeParsingJson() throws Exception {
        long timestamp = Instant.now().getEpochSecond();
        byte[] body = "{ \"paymentReference\" : \"ref-1\", \"status\" : \"COMPLETED\" }"
                .getBytes(StandardCharsets.UTF_8);
        String signature = hmac("webhook-test-secret", timestamp + "." + new String(body, StandardCharsets.UTF_8));
        HttpClient client = HttpClient.newHttpClient();
        HttpRequest valid = HttpRequest.newBuilder(webhookUri())
                .header("Content-Type", "application/json")
                .header("X-Webhook-Signature", "t=" + timestamp + ",v1=" + signature)
                .POST(HttpRequest.BodyPublishers.ofByteArray(body)).build();
        HttpResponse<String> accepted = client.send(valid, HttpResponse.BodyHandlers.ofString());
        assertThat(accepted.statusCode()).isEqualTo(200);
        verify(paymentService).handleVerifiedWebhook(eq("internal"), any(), eq("COMPLETED"), eq("ref-1"), any());

        byte[] changedBody = "{\"paymentReference\":\"ref-1\",\"status\":\"COMPLETED\"}"
                .getBytes(StandardCharsets.UTF_8);
        HttpRequest invalid = HttpRequest.newBuilder(webhookUri())
                .header("Content-Type", "application/json")
                .header("X-Webhook-Signature", "t=" + timestamp + ",v1=" + signature)
                .POST(HttpRequest.BodyPublishers.ofByteArray(changedBody)).build();
        HttpResponse<String> rejected = client.send(invalid, HttpResponse.BodyHandlers.ofString());
        assertThat(rejected.statusCode()).isEqualTo(401);

        byte[] malformedJson = "{ \"paymentReference\" :".getBytes(StandardCharsets.UTF_8);
        String malformedSignature = hmac("webhook-test-secret",
                timestamp + "." + new String(malformedJson, StandardCharsets.UTF_8));
        HttpRequest signedMalformed = HttpRequest.newBuilder(webhookUri())
                .header("Content-Type", "application/json")
                .header("X-Webhook-Signature", "t=" + timestamp + ",v1=" + malformedSignature)
                .POST(HttpRequest.BodyPublishers.ofByteArray(malformedJson)).build();
        HttpResponse<String> malformed = client.send(signedMalformed, HttpResponse.BodyHandlers.ofString());
        assertThat(malformed.statusCode()).isEqualTo(400);
    }

    @Test
    void rawStripeWebhookAuthenticatesTheExactBytesWithTheStripeSdk() throws Exception {
        long timestamp = Instant.now().getEpochSecond();
        byte[] body = ("{ \"id\":\"evt-raw\", \"object\":\"event\", \"api_version\":\"2026-04-22.dahlia\", "
                + "\"type\":\"payment_intent.succeeded\", \"data\":{\"object\":{\"id\":\"pi-raw\", "
                + "\"object\":\"payment_intent\", \"metadata\":{\"paymentReference\":\"ref-raw\"}}}}")
                .getBytes(StandardCharsets.UTF_8);
        String signature = stripeSignature("stripe-test-secret", timestamp, body);
        HttpClient client = HttpClient.newHttpClient();
        HttpRequest valid = HttpRequest.newBuilder(stripeWebhookUri())
                .header("Content-Type", "application/json")
                .header("Stripe-Signature", signature)
                .POST(HttpRequest.BodyPublishers.ofByteArray(body)).build();
        HttpResponse<String> accepted = client.send(valid, HttpResponse.BodyHandlers.ofString());
        assertThat(accepted.statusCode()).isEqualTo(200);
        verify(paymentService).handleStripeWebhook(eq("evt-raw"), eq("payment_intent.succeeded"), eq("ref-raw"), any());

        byte[] changedBody = new String(body, StandardCharsets.UTF_8).replace("ref-raw", "ref-modified")
                .getBytes(StandardCharsets.UTF_8);
        HttpRequest invalid = HttpRequest.newBuilder(stripeWebhookUri())
                .header("Content-Type", "application/json")
                .header("Stripe-Signature", signature)
                .POST(HttpRequest.BodyPublishers.ofByteArray(changedBody)).build();
        HttpResponse<String> rejected = client.send(invalid, HttpResponse.BodyHandlers.ofString());
        assertThat(rejected.statusCode()).isEqualTo(400);
    }

    private URI webhookUri() {
        return URI.create("http://localhost:" + serverContext.getWebServer().getPort() + "/api/v1/payments/webhook");
    }

    private HttpResponse<String> rawInitiation(String idempotencyKey) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create("http://localhost:"
                        + serverContext.getWebServer().getPort() + "/api/v1/payments"))
                .header("Authorization", "Bearer " + TOKEN)
                .header("Content-Type", "application/json")
                .header("X-Idempotency-Key", idempotencyKey)
                .POST(HttpRequest.BodyPublishers.ofString("{\"orderId\":\"order-1\",\"paymentMethod\":\"CARD\"}"))
                .build();
        return HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());
    }

    private URI stripeWebhookUri() {
        return URI.create("http://localhost:" + serverContext.getWebServer().getPort()
                + "/api/v1/payments/webhook/stripe");
    }

    private static String hmac(String secret, String message) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return HexFormat.of().formatHex(mac.doFinal(message.getBytes(StandardCharsets.UTF_8)));
    }

    private static String stripeSignature(String secret, long timestamp, byte[] body) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        byte[] prefix = (timestamp + ".").getBytes(StandardCharsets.UTF_8);
        byte[] signedPayload = new byte[prefix.length + body.length];
        System.arraycopy(prefix, 0, signedPayload, 0, prefix.length);
        System.arraycopy(body, 0, signedPayload, prefix.length, body.length);
        return "t=" + timestamp + ",v1=" + HexFormat.of().formatHex(mac.doFinal(signedPayload));
    }

    private static PaymentResponse payment(String id, UUID user, PaymentStatus status) {
        return new PaymentResponse().id(id).paymentReference("ref-1").orderId("order-1").orderNumber("ORD-1").userId(user).userEmail(null).status(status == null ? null : com.project.payment.generated.model.PaymentStatus.valueOf(status.name())).paymentMethod("CARD").amount(new BigDecimal("10.00")).refundedAmount(BigDecimal.ZERO).currency("USD").transactionId(null).gatewayResponse(null).failureReason(null).retryCount(0).description(null).createdAt(null).updatedAt(null).completedAt(null);
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
            "org.springframework.cloud.netflix.eureka.EurekaClientAutoConfiguration"})
    @Import({PaymentController.class, SecurityConfig.class, PaymentMapper.class, PaymentAccessValidator.class,
            GlobalExceptionHandler.class})
    static class TestApplication { }
}
