package com.project.payment.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.project.common.dto.ApiResponse;
import com.project.common.event.PaymentEvent;
import com.project.payment.api.dto.request.PaymentRequest;
import com.project.payment.api.dto.request.PaymentWebhookRequest;
import com.project.payment.api.dto.response.PaymentInitiationResponse;
import com.project.payment.PaymentServiceApplication;
import com.project.payment.application.mapper.PaymentMapper;
import com.project.payment.application.validator.PaymentOrderValidator;
import com.project.payment.application.validator.PaymentTransitionValidator;
import com.project.payment.client.OrderClient;
import com.project.payment.client.dto.OrderSummary;
import com.project.payment.controller.PaymentController;
import com.project.payment.exception.PaymentException;
import com.project.payment.kafka.PaymentEventPublisher;
import com.project.payment.model.Payment;
import com.project.payment.model.PaymentOperation;
import com.project.payment.model.PaymentOutboxEvent;
import com.project.payment.model.PaymentStatus;
import com.project.payment.model.WebhookReceipt;
import com.project.payment.repository.PaymentOperationRepository;
import com.project.payment.repository.PaymentOutboxRepository;
import com.project.payment.repository.PaymentRepository;
import com.project.payment.repository.WebhookReceiptRepository;
import com.project.payment.service.impl.PaymentGateway;
import com.project.payment.service.impl.PaymentOutboxRelay;
import com.project.payment.service.impl.PaymentServiceImpl;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.data.annotation.Version;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.http.HttpStatus;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionOperations;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;

class PaymentDurabilityTest {

    @Test
    void durableInfrastructureEnforcesIndexesVersionsAndRelayScheduling() throws Exception {
        assertThat(Payment.class.getDeclaredField("version").isAnnotationPresent(Version.class)).isTrue();
        assertThat(PaymentOperation.class.getAnnotation(CompoundIndex.class).def())
                .isEqualTo("{'operation': 1, 'userId': 1, 'idempotencyKey': 1}");
        assertThat(PaymentOperation.class.getAnnotation(CompoundIndex.class).unique()).isTrue();
        assertThat(WebhookReceipt.class.getAnnotation(CompoundIndex.class).def())
                .isEqualTo("{'provider': 1, 'eventId': 1}");
        assertThat(WebhookReceipt.class.getAnnotation(CompoundIndex.class).unique()).isTrue();
        assertThat(PaymentServiceApplication.class.isAnnotationPresent(EnableScheduling.class)).isTrue();
        assertThat(Payment.class.getDeclaredField("orderId").getAnnotation(Indexed.class).unique()).isTrue();
        assertThat(Payment.class.getDeclaredField("paymentReference").getAnnotation(Indexed.class).unique()).isTrue();
        assertThat(java.util.Arrays.stream(PaymentServiceImpl.class.getDeclaredFields())
                .map(java.lang.reflect.Field::getType)).doesNotContain(TransactionOperations.class);
        assertThat(java.util.Arrays.stream(PaymentServiceApplication.class.getDeclaredMethods())
                .map(java.lang.reflect.Method::getReturnType)).doesNotContain(TransactionOperations.class);
    }

    @Test
    void concurrentDifferentCreateKeysForOneOrderProduceOnePaymentAndOneIntent() throws Exception {
        Persistence persistence = new Persistence();
        UUID userId = UUID.randomUUID();
        AtomicInteger intents = new AtomicInteger();
        CountDownLatch enteredGateway = new CountDownLatch(1);
        CountDownLatch releaseGateway = new CountDownLatch(1);
        PaymentGateway gateway = gateway();
        when(gateway.createIntent(any())).thenAnswer(invocation -> {
            intents.incrementAndGet();
            enteredGateway.countDown();
            releaseGateway.await();
            return new PaymentGateway.IntentResult("pi-1", "ephemeral-secret");
        });
        PaymentServiceImpl service = service(persistence, gateway, order(userId));
        PaymentRequest request = new PaymentRequest("order-1", null, "CARD", null, null, "checkout");

        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<PaymentInitiationResponse> first = executor.submit(() -> service.createPayment(
                    request, userId, "customer@example.com", "create-key-1"));
            enteredGateway.await();
            Future<PaymentInitiationResponse> second = executor.submit(() -> service.createPayment(
                    request, userId, "customer@example.com", "create-key-2"));
            PaymentInitiationResponse replay = second.get();
            releaseGateway.countDown();
            PaymentInitiationResponse created = first.get();
            assertThat(replay.payment().id()).isEqualTo(created.payment().id());
            assertThat(replay.clientSecret()).isNull();
        } finally {
            releaseGateway.countDown();
            executor.shutdownNow();
        }

        assertThat(intents).hasValue(1);
        assertThat(persistence.payments).hasSize(1);
    }

    @Test
    void concurrentCreateWithTheSameDurableKeyCreatesOnePaymentAndOneGatewayIntent() throws Exception {
        Persistence persistence = new Persistence();
        UUID userId = UUID.randomUUID();
        AtomicInteger intents = new AtomicInteger();
        CountDownLatch enteredGateway = new CountDownLatch(1);
        CountDownLatch releaseGateway = new CountDownLatch(1);
        PaymentGateway gateway = gateway();
        when(gateway.createIntent(any())).thenAnswer(invocation -> {
            intents.incrementAndGet();
            enteredGateway.countDown();
            releaseGateway.await();
            return new PaymentGateway.IntentResult("pi-1", "ephemeral-secret");
        });
        PaymentServiceImpl service = service(persistence, gateway, order(userId));
        PaymentRequest request = new PaymentRequest("order-1", null, "CARD", null, null, "checkout");

        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<?> first = executor.submit(() -> service.createPayment(
                    request, userId, "customer@example.com", "create-key"));
            enteredGateway.await();
            Future<PaymentInitiationResponse> replay = executor.submit(() -> service.createPayment(
                    request, userId, "customer@example.com", "create-key"));
            assertThat(replay.get().clientSecret()).isNull();
            releaseGateway.countDown();
            first.get();
        } finally {
            releaseGateway.countDown();
            executor.shutdownNow();
        }

        assertThat(intents).hasValue(1);
        assertThat(persistence.payments).hasSize(1);
        assertThat(persistence.operations).hasSize(1);
        assertThat(persistence.payments.get(0).getTransactionId()).isEqualTo("pi-1");
        assertThat(persistence.outbox).singleElement().satisfies(event -> {
            assertThat(event.getPayload()).contains("\"paymentId\":\"payment-1\"");
            assertThat(event.getPayload()).doesNotContain("ephemeral-secret");
        });
        String snapshot = persistence.outbox.get(0).getPayload();
        persistence.payment("payment-1").setAmount(new BigDecimal("999.00"));
        assertThat(persistence.outbox.get(0).getPayload()).isEqualTo(snapshot).doesNotContain("999.00");
    }

    @Test
    void concurrentRefundWithTheSameDurableKeyCallsGatewayOnceAndPersistsOneRefund() throws Exception {
        Persistence persistence = new Persistence();
        UUID userId = UUID.randomUUID();
        persistence.persist(completedPayment(userId));
        AtomicInteger refunds = new AtomicInteger();
        CountDownLatch enteredGateway = new CountDownLatch(1);
        CountDownLatch releaseGateway = new CountDownLatch(1);
        PaymentGateway gateway = gateway();
        doAnswer(invocation -> {
            refunds.incrementAndGet();
            enteredGateway.countDown();
            releaseGateway.await();
            return null;
        }).when(gateway).refund(any(), any(), any(), any());
        PaymentServiceImpl service = service(persistence, gateway, order(userId));

        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<?> first = executor.submit(() -> service.refundPayment(
                    "payment-1", "requested", new BigDecimal("25.00"), "refund-key"));
            enteredGateway.await();
            Future<?> replay = executor.submit(() -> service.refundPayment(
                    "payment-1", "requested", new BigDecimal("25.00"), "refund-key"));
            replay.get();
            releaseGateway.countDown();
            first.get();
        } finally {
            releaseGateway.countDown();
            executor.shutdownNow();
        }

        assertThat(refunds).hasValue(1);
        assertThat(persistence.operations).singleElement().satisfies(operation ->
                assertThat(operation.getOperation()).isEqualTo("REFUND"));
        assertThat(persistence.payment("payment-1").getRefundedAmount()).isEqualByComparingTo("25.00");
        assertThat(persistence.outbox).hasSize(1);
    }

    @Test
    void concurrentDifferentRefundKeysAllowOnlyOneExternalRefundClaim() throws Exception {
        Persistence persistence = new Persistence();
        UUID userId = UUID.randomUUID();
        persistence.persist(completedPayment(userId));
        AtomicInteger refunds = new AtomicInteger();
        CountDownLatch enteredGateway = new CountDownLatch(1);
        CountDownLatch releaseGateway = new CountDownLatch(1);
        PaymentGateway gateway = gateway();
        doAnswer(invocation -> {
            refunds.incrementAndGet();
            enteredGateway.countDown();
            releaseGateway.await();
            return null;
        }).when(gateway).refund(any(), any(), any(), any());
        PaymentServiceImpl service = service(persistence, gateway, order(userId));

        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<?> first = executor.submit(() -> service.refundPayment(
                    "payment-1", "first", new BigDecimal("75.00"), "refund-key-1"));
            enteredGateway.await();
            Future<?> second = executor.submit(() -> service.refundPayment(
                    "payment-1", "second", new BigDecimal("75.00"), "refund-key-2"));
            assertThatThrownBy(second::get).hasCauseInstanceOf(PaymentException.class);
            assertThat(persistence.payment("payment-1").getActiveRefundOperationId()).isNotBlank();
            releaseGateway.countDown();
            first.get();
        } finally {
            releaseGateway.countDown();
            executor.shutdownNow();
        }

        assertThat(refunds).hasValue(1);
        assertThat(persistence.payment("payment-1").getRefundedAmount()).isEqualByComparingTo("75.00");
        assertThat(persistence.payment("payment-1").getActiveRefundOperationId()).isNull();
    }

    @Test
    void ambiguousRefundFailureKeepsPaymentBlockedForReconciliation() {
        Persistence persistence = new Persistence();
        UUID userId = UUID.randomUUID();
        persistence.persist(completedPayment(userId));
        AtomicInteger refunds = new AtomicInteger();
        PaymentGateway gateway = gateway();
        doAnswer(invocation -> {
            refunds.incrementAndGet();
            throw new IllegalStateException("provider timeout after request");
        }).when(gateway).refund(any(), any(), any(), any());
        PaymentServiceImpl service = service(persistence, gateway, order(userId));

        assertThatThrownBy(() -> service.refundPayment(
                "payment-1", "first", new BigDecimal("75.00"), "refund-key-1"))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> service.refundPayment(
                "payment-1", "second", new BigDecimal("75.00"), "refund-key-2"))
                .isInstanceOf(PaymentException.class)
                .hasMessageContaining("reconciliation");

        assertThat(refunds).hasValue(1);
        assertThat(persistence.payment("payment-1").getActiveRefundOperationId()).isNotBlank();
        assertThat(persistence.operations).extracting(PaymentOperation::getStatus)
                .contains("RECONCILE", "BLOCKED");
    }

    @Test
    void duplicateStripeEventPersistsOneReceiptAndOneTransitionOutbox() {
        Persistence persistence = new Persistence();
        UUID userId = UUID.randomUUID();
        Payment payment = pendingPayment(userId);
        payment.setTransactionId("pi-1");
        persistence.persist(payment);
        PaymentServiceImpl service = service(persistence, gateway(), order(userId));
        PaymentWebhookRequest webhook = new PaymentWebhookRequest("PAY-1", "pi-1", "COMPLETED", null);

        service.handleStripeWebhook("evt-1", "payment_intent.succeeded", "PAY-1", webhook);
        service.handleStripeWebhook("evt-1", "payment_intent.succeeded", "PAY-1", webhook);
        assertThatThrownBy(() -> service.handleStripeWebhook(
                "evt-1", "payment_intent.payment_failed", "PAY-1",
                new PaymentWebhookRequest("PAY-1", "pi-1", "FAILED", "declined")))
                .isInstanceOf(PaymentException.class)
                .hasMessageContaining("event ID");

        assertThat(persistence.receipts).hasSize(1);
        assertThat(persistence.outbox).hasSize(1);
        assertThat(persistence.payment("payment-1").getStatus()).isEqualTo(PaymentStatus.COMPLETED);
    }

    @Test
    void signedLegacyWebhookUsesDurableReceiptDeduplication() {
        Persistence persistence = new Persistence();
        UUID userId = UUID.randomUUID();
        Payment payment = pendingPayment(userId);
        payment.setTransactionId("pi-1");
        persistence.persist(payment);
        PaymentServiceImpl service = service(persistence, gateway(), order(userId));
        PaymentWebhookRequest webhook = new PaymentWebhookRequest("PAY-1", "pi-1", "COMPLETED", null);

        service.handlePaymentWebhook("PAY-1", webhook);
        service.handlePaymentWebhook("PAY-1", webhook);

        assertThat(persistence.receipts).hasSize(1);
        assertThat(persistence.outbox).hasSize(1);
        assertThat(persistence.payment("payment-1").getStatus()).isEqualTo(PaymentStatus.COMPLETED);
    }

    @Test
    void webhookReplayCompletesOutboxAfterAnInterruptedCrossDocumentPhase() {
        Persistence persistence = new Persistence();
        UUID userId = UUID.randomUUID();
        Payment payment = pendingPayment(userId);
        payment.setTransactionId("pi-1");
        persistence.persist(payment);
        persistence.failNextOutboxInsert = true;
        PaymentServiceImpl service = service(persistence, gateway(), order(userId));
        PaymentWebhookRequest webhook = new PaymentWebhookRequest("PAY-1", "pi-1", "COMPLETED", null);

        assertThatThrownBy(() -> service.handleStripeWebhook(
                "evt-resume", "payment_intent.succeeded", "PAY-1", webhook))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("simulated outbox interruption");
        assertThat(persistence.payment("payment-1").getStatus()).isEqualTo(PaymentStatus.COMPLETED);
        assertThat(persistence.receipts).singleElement().satisfies(receipt ->
                assertThat(receipt.getStatus()).isEqualTo("APPLIED"));

        service.handleStripeWebhook("evt-resume", "payment_intent.succeeded", "PAY-1", webhook);

        assertThat(persistence.outbox).hasSize(1);
        assertThat(persistence.receipts).singleElement().satisfies(receipt ->
                assertThat(receipt.getStatus()).isEqualTo("COMPLETED"));
    }

    @Test
    void processReplayCompletesTerminalOutboxWithoutRepeatingGatewayConfirmation() {
        Persistence persistence = new Persistence();
        UUID userId = UUID.randomUUID();
        persistence.persist(pendingPayment(userId));
        persistence.failOutboxInsertOnAttempt = 2;
        AtomicInteger confirmations = new AtomicInteger();
        PaymentGateway gateway = gateway();
        when(gateway.confirm(any())).thenAnswer(invocation -> {
            confirmations.incrementAndGet();
            return true;
        });
        PaymentServiceImpl service = service(persistence, gateway, order(userId));

        assertThatThrownBy(() -> service.processPayment("payment-1"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("simulated outbox interruption");
        assertThat(persistence.payment("payment-1").getStatus()).isEqualTo(PaymentStatus.COMPLETED);
        assertThat(persistence.outbox).hasSize(1);

        assertThat(service.processPayment("payment-1").status()).isEqualTo(PaymentStatus.COMPLETED);

        assertThat(confirmations).hasValue(1);
        assertThat(persistence.outbox).hasSize(2)
                .extracting(PaymentOutboxEvent::getEventType)
                .containsExactlyInAnyOrder("PROCESSING", "COMPLETED");
        assertThat(persistence.outbox).extracting(PaymentOutboxEvent::getId).doesNotHaveDuplicates();
    }

    @Test
    void cancelReplayCompletesOutboxOnceAfterStateSaveInterruption() {
        Persistence persistence = new Persistence();
        UUID userId = UUID.randomUUID();
        persistence.persist(pendingPayment(userId));
        persistence.failOutboxInsertOnAttempt = 1;
        PaymentServiceImpl service = service(persistence, gateway(), order(userId));

        assertThatThrownBy(() -> service.cancelPaymentByOrderId("order-1"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("simulated outbox interruption");
        assertThat(persistence.payment("payment-1").getStatus()).isEqualTo(PaymentStatus.CANCELLED);
        assertThat(persistence.outbox).isEmpty();

        service.cancelPaymentByOrderId("order-1");
        service.cancelPaymentByOrderId("order-1");

        assertThat(persistence.outbox).singleElement().satisfies(event -> {
            assertThat(event.getEventType()).isEqualTo("CANCELLED");
            assertThat(event.getPayload()).contains("\"paymentId\":\"payment-1\"");
        });
    }

    @Test
    void laterCancellationCannotEraseInterruptedProcessingEvent() {
        Persistence persistence = new Persistence();
        UUID userId = UUID.randomUUID();
        persistence.persist(pendingPayment(userId));
        persistence.failOperationSaveOnAttempt = 2;
        AtomicInteger confirmations = new AtomicInteger();
        PaymentGateway gateway = gateway();
        when(gateway.confirm(any())).thenAnswer(invocation -> {
            confirmations.incrementAndGet();
            return true;
        });
        PaymentServiceImpl service = service(persistence, gateway, order(userId));

        assertThatThrownBy(() -> service.processPayment("payment-1"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("simulated operation interruption");
        assertThat(persistence.payment("payment-1").getStatus()).isEqualTo(PaymentStatus.PROCESSING);
        assertThat(persistence.outbox).isEmpty();

        service.cancelPaymentByOrderId("order-1");
        assertThatThrownBy(() -> service.processPayment("payment-1"))
                .isInstanceOf(PaymentException.class);

        assertThat(confirmations).hasValue(0);
        assertThat(persistence.payment("payment-1").getStatus()).isEqualTo(PaymentStatus.CANCELLED);
        assertThat(persistence.outbox).extracting(PaymentOutboxEvent::getEventType)
                .containsExactlyInAnyOrder("PROCESSING", "CANCELLED");
        assertThat(persistence.outbox).filteredOn(event -> "PROCESSING".equals(event.getEventType()))
                .singleElement().satisfies(event -> assertThat(event.getPayload()).doesNotContain("CANCELLED"));
    }

    @Test
    void relayPublishesRecoveredProcessingBeforeLaterCancellation() {
        Persistence persistence = new Persistence();
        UUID userId = UUID.randomUUID();
        persistence.persist(pendingPayment(userId));
        persistence.failOperationSaveOnAttempt = 2;
        PaymentGateway gateway = gateway();
        when(gateway.confirm(any())).thenReturn(true);
        PaymentServiceImpl service = service(persistence, gateway, order(userId));

        assertThatThrownBy(() -> service.processPayment("payment-1"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("simulated operation interruption");
        service.cancelPaymentByOrderId("order-1");
        assertThatThrownBy(() -> service.processPayment("payment-1")).isInstanceOf(PaymentException.class);

        KafkaTemplate<String, Object> kafka = mock(KafkaTemplate.class);
        List<PaymentEvent.Type> publishedTypes = new ArrayList<>();
        when(kafka.send(any(), any(), any())).thenAnswer(invocation -> {
            publishedTypes.add(((PaymentEvent) invocation.getArgument(2)).getType());
            return CompletableFuture.completedFuture(null);
        });
        PaymentOutboxRelay relay = new PaymentOutboxRelay(
                persistence.outboxRepository, new PaymentEventPublisher(kafka, new ObjectMapper().findAndRegisterModules()));

        relay.relayEvents();
        relay.relayEvents();

        assertThat(publishedTypes).containsExactly(PaymentEvent.Type.PROCESSING, PaymentEvent.Type.CANCELLED);
    }

    @Test
    void relayPublishesRecoveredProcessingBeforeLaterWebhookCompletion() {
        Persistence persistence = new Persistence();
        UUID userId = UUID.randomUUID();
        Payment payment = pendingPayment(userId);
        payment.setTransactionId("pi-1");
        persistence.persist(payment);
        persistence.failOperationSaveOnAttempt = 2;
        PaymentGateway gateway = gateway();
        when(gateway.confirm(any())).thenReturn(true);
        PaymentServiceImpl service = service(persistence, gateway, order(userId));

        assertThatThrownBy(() -> service.processPayment("payment-1"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("simulated operation interruption");
        service.handleStripeWebhook("evt-completed", "payment_intent.succeeded", "PAY-1",
                new PaymentWebhookRequest("PAY-1", "pi-1", "COMPLETED", null));
        assertThatThrownBy(() -> service.processPayment("payment-1")).isInstanceOf(PaymentException.class);

        KafkaTemplate<String, Object> kafka = mock(KafkaTemplate.class);
        List<PaymentEvent.Type> publishedTypes = new ArrayList<>();
        when(kafka.send(any(), any(), any())).thenAnswer(invocation -> {
            publishedTypes.add(((PaymentEvent) invocation.getArgument(2)).getType());
            return CompletableFuture.completedFuture(null);
        });
        PaymentOutboxRelay relay = new PaymentOutboxRelay(
                persistence.outboxRepository, new PaymentEventPublisher(kafka, new ObjectMapper().findAndRegisterModules()));

        relay.relayEvents();
        relay.relayEvents();

        assertThat(publishedTypes).containsExactly(PaymentEvent.Type.PROCESSING, PaymentEvent.Type.COMPLETED);
    }

    @Test
    void processReplayAfterJournalCompletionInterruptionDoesNotDuplicateOutboxOrGateway() {
        Persistence persistence = new Persistence();
        UUID userId = UUID.randomUUID();
        persistence.persist(pendingPayment(userId));
        persistence.failOperationCompletionOnAttempt = 2;
        AtomicInteger confirmations = new AtomicInteger();
        PaymentGateway gateway = gateway();
        when(gateway.confirm(any())).thenAnswer(invocation -> {
            confirmations.incrementAndGet();
            return true;
        });
        PaymentServiceImpl service = service(persistence, gateway, order(userId));

        assertThatThrownBy(() -> service.processPayment("payment-1"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("simulated operation completion interruption");
        assertThat(persistence.outbox).hasSize(2);

        assertThat(service.processPayment("payment-1").status()).isEqualTo(PaymentStatus.COMPLETED);

        assertThat(confirmations).hasValue(1);
        assertThat(persistence.outbox).hasSize(2);
        assertThat(persistence.outbox).extracting(PaymentOutboxEvent::getId).doesNotHaveDuplicates();
    }

    @Test
    void stalePaymentVersionReloadsBeforeApplyingLegalWebhookTransition() {
        Persistence persistence = new Persistence();
        UUID userId = UUID.randomUUID();
        Payment payment = pendingPayment(userId);
        payment.setTransactionId("pi-1");
        persistence.persist(payment);
        persistence.failNextPaymentSave = true;

        service(persistence, gateway(), order(userId)).handleStripeWebhook(
                "evt-stale", "payment_intent.succeeded", "PAY-1",
                new PaymentWebhookRequest("PAY-1", "pi-1", "COMPLETED", null));

        assertThat(persistence.payment("payment-1").getStatus()).isEqualTo(PaymentStatus.COMPLETED);
        assertThat(persistence.payment("payment-1").getVersion()).isEqualTo(2L);
        assertThat(persistence.outbox).hasSize(1);
    }

    @Test
    void stripeWebhookRejectsInvalidSignaturePayloadTypeAndMissingReference() throws Exception {
        PaymentService paymentService = mock(PaymentService.class);
        PaymentController controller = new PaymentController(
                paymentService, mock(com.project.payment.application.validator.PaymentAccessValidator.class));
        ReflectionTestUtils.setField(controller, "stripeWebhookSecret", "whsec_test");

        assertThat(controller.handleStripeWebhook("bad", "{}").getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(controller.handleStripeWebhook(signature("not-json"), "not-json").getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
        String unsupported = stripeEvent("evt-type", "charge.succeeded", "PAY-1", "pi-1");
        assertThat(controller.handleStripeWebhook(signature(unsupported), unsupported).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
        String missingReference = stripeEvent("evt-ref", "payment_intent.succeeded", null, "pi-1");
        assertThat(controller.handleStripeWebhook(signature(missingReference), missingReference).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void stripeWebhookRejectsMismatchedPaymentIntentBeforeTransition() {
        Persistence persistence = new Persistence();
        UUID userId = UUID.randomUUID();
        Payment payment = pendingPayment(userId);
        payment.setTransactionId("pi-owned");
        persistence.persist(payment);

        assertThatThrownBy(() -> service(persistence, gateway(), order(userId)).handleStripeWebhook(
                "evt-wrong-reference", "payment_intent.succeeded", "PAY-1",
                new PaymentWebhookRequest("PAY-1", "pi-other", "COMPLETED", null)))
                .isInstanceOf(PaymentException.class)
                .hasMessageContaining("payment reference");

        assertThat(persistence.payment("payment-1").getStatus()).isEqualTo(PaymentStatus.PENDING);
        assertThat(persistence.receipts).isEmpty();
        assertThat(persistence.outbox).isEmpty();
    }

    @Test
    @SuppressWarnings("unchecked")
    void outboxFailureStaysRetryableAndStaleCallbackCannotCompleteAnotherLease() {
        Persistence persistence = new Persistence();
        String payload = "{\"type\":\"COMPLETED\",\"paymentId\":\"payment-1\",\"amount\":10.00}";
        persistence.outbox.add(PaymentOutboxEvent.builder()
                .id("outbox-1").version(0L).paymentId("payment-1").eventType("COMPLETED").payload(payload)
                .attempts(0).nextAttemptAt(LocalDateTime.now().minusSeconds(1))
                .createdAt(LocalDateTime.now()).build());
        KafkaTemplate<String, Object> kafka = mock(KafkaTemplate.class);
        CompletableFuture<SendResult<String, Object>> failedSend = new CompletableFuture<>();
        CompletableFuture<SendResult<String, Object>> staleSend = new CompletableFuture<>();
        CompletableFuture<SendResult<String, Object>> currentSend = new CompletableFuture<>();
        when(kafka.send(any(), any(), any())).thenReturn(failedSend, staleSend, currentSend);
        PaymentOutboxRelay relay = new PaymentOutboxRelay(
                persistence.outboxRepository, new PaymentEventPublisher(kafka, new ObjectMapper().findAndRegisterModules()));

        relay.relayEvents();
        PaymentOutboxEvent firstLease = persistence.outbox("outbox-1");
        assertThat(firstLease.getLeaseToken()).isNotBlank();
        assertThat(firstLease.getPublishedAt()).isNull();
        assertThat(firstLease.getPayload()).isEqualTo(payload);
        failedSend.completeExceptionally(new IllegalStateException("Kafka unavailable"));
        PaymentOutboxEvent retryable = persistence.outbox("outbox-1");
        assertThat(retryable.getLeaseToken()).isNull();
        assertThat(retryable.getNextAttemptAt()).isNotNull();
        assertThat(retryable.getPublishedAt()).isNull();
        persistence.makeOutboxDue("outbox-1");
        relay.relayEvents();
        persistence.expireLease("outbox-1");
        relay.relayEvents();
        String currentToken = persistence.outbox("outbox-1").getLeaseToken();

        staleSend.completeExceptionally(new IllegalStateException("Kafka unavailable"));
        assertThat(persistence.outbox("outbox-1").getLeaseToken()).isEqualTo(currentToken);
        assertThat(persistence.outbox("outbox-1").getPublishedAt()).isNull();
        currentSend.complete(null);

        PaymentOutboxEvent delivered = persistence.outbox("outbox-1");
        assertThat(delivered.getAttempts()).isEqualTo(3);
        assertThat(delivered.getPublishedAt()).isNotNull();
        assertThat(delivered.getPayload()).isEqualTo(payload);
    }

    private PaymentServiceImpl service(Persistence persistence, PaymentGateway gateway, OrderClient orderClient) {
        return new PaymentServiceImpl(
                persistence.paymentRepository, persistence.operationRepository, persistence.receiptRepository,
                persistence.outboxRepository, gateway, new PaymentMapper(), orderClient,
                new PaymentOrderValidator(), new PaymentTransitionValidator(),
                new ObjectMapper().findAndRegisterModules());
    }

    private PaymentGateway gateway() {
        PaymentGateway gateway = mock(PaymentGateway.class);
        when(gateway.createIntent(any())).thenReturn(new PaymentGateway.IntentResult("pi-1", "secret"));
        return gateway;
    }

    private OrderClient order(UUID userId) {
        OrderClient orderClient = mock(OrderClient.class);
        when(orderClient.getOrder("order-1")).thenReturn(ApiResponse.success(new OrderSummary(
                "order-1", "ORD-1", userId, "PENDING", new BigDecimal("100.00"), "USD")));
        return orderClient;
    }

    private Payment pendingPayment(UUID userId) {
        return Payment.builder().id("payment-1").version(0L).paymentReference("PAY-1").orderId("order-1")
                .userId(userId).status(PaymentStatus.PENDING).paymentMethod("CARD")
                .amount(new BigDecimal("100.00")).refundedAmount(BigDecimal.ZERO).currency("USD").build();
    }

    private Payment completedPayment(UUID userId) {
        Payment payment = pendingPayment(userId);
        payment.setStatus(PaymentStatus.COMPLETED);
        payment.setTransactionId("pi-1");
        return payment;
    }

    private String signature(String payload) throws Exception {
        long timestamp = System.currentTimeMillis() / 1000;
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec("whsec_test".getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        byte[] digest = mac.doFinal((timestamp + "." + payload).getBytes(StandardCharsets.UTF_8));
        return "t=" + timestamp + ",v1=" + java.util.HexFormat.of().formatHex(digest);
    }

    private String stripeEvent(String id, String type, String reference, String intentId) {
        String metadata = reference == null ? "{}" : "{\"paymentReference\":\"" + reference + "\"}";
        return "{\"id\":\"" + id + "\",\"object\":\"event\",\"api_version\":\"2026-04-22.dahlia\",\"type\":\"" + type
                + "\",\"data\":{\"object\":{\"id\":\"" + intentId
                + "\",\"object\":\"payment_intent\",\"metadata\":" + metadata + "}}}";
    }

    private static final class Persistence {
        private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
        private final List<Payment> payments = new ArrayList<>();
        private final List<PaymentOperation> operations = new ArrayList<>();
        private final List<WebhookReceipt> receipts = new ArrayList<>();
        private final List<PaymentOutboxEvent> outbox = new ArrayList<>();
        private boolean failNextPaymentSave;
        private boolean failNextOutboxInsert;
        private int failOutboxInsertOnAttempt;
        private int outboxInsertAttempts;
        private int failOperationSaveOnAttempt;
        private int operationSaveAttempts;
        private int failOperationCompletionOnAttempt;
        private int operationCompletionAttempts;
        private final PaymentRepository paymentRepository = mock(PaymentRepository.class);
        private final PaymentOperationRepository operationRepository = mock(PaymentOperationRepository.class);
        private final WebhookReceiptRepository receiptRepository = mock(WebhookReceiptRepository.class);
        private final PaymentOutboxRepository outboxRepository = mock(PaymentOutboxRepository.class);

        private Persistence() {
            when(paymentRepository.findById(any())).thenAnswer(invocation -> synchronizedPaymentById(invocation.getArgument(0)));
            when(paymentRepository.findByPaymentReference(any())).thenAnswer(invocation -> synchronizedPaymentByReference(invocation.getArgument(0)));
            when(paymentRepository.findByOrderId(any())).thenAnswer(invocation -> synchronizedPaymentByOrder(invocation.getArgument(0)));
            when(paymentRepository.insert(any(Payment.class))).thenAnswer(invocation -> insertPayment(invocation.getArgument(0)));
            when(paymentRepository.save(any(Payment.class))).thenAnswer(invocation -> savePayment(invocation.getArgument(0)));
            when(operationRepository.findByOperationAndUserIdAndIdempotencyKey(any(), any(), any()))
                    .thenAnswer(invocation -> findOperation(invocation.getArgument(0), invocation.getArgument(1), invocation.getArgument(2)));
            when(operationRepository.insert(any(PaymentOperation.class))).thenAnswer(invocation -> insertOperation(invocation.getArgument(0)));
            when(operationRepository.save(any(PaymentOperation.class))).thenAnswer(invocation -> saveOperation(invocation.getArgument(0)));
            when(receiptRepository.insert(any(WebhookReceipt.class))).thenAnswer(invocation -> insertReceipt(invocation.getArgument(0)));
            when(receiptRepository.save(any(WebhookReceipt.class))).thenAnswer(invocation -> saveReceipt(invocation.getArgument(0)));
            when(receiptRepository.findByProviderAndEventId(any(), any())).thenAnswer(invocation -> {
                synchronized (this) {
                    String provider = invocation.getArgument(0);
                    String eventId = invocation.getArgument(1);
                    return receipts.stream().filter(receipt -> provider.equals(receipt.getProvider())
                                    && eventId.equals(receipt.getEventId())).findFirst()
                            .map(receipt -> copy(receipt, WebhookReceipt.class));
                }
            });
            when(outboxRepository.insert(any(PaymentOutboxEvent.class))).thenAnswer(invocation -> insertOutbox(invocation.getArgument(0)));
            when(outboxRepository.findByPublishedAtIsNullAndNextAttemptAtLessThanEqualOrderByCreatedAtAsc(any()))
                    .thenAnswer(invocation -> dueOutbox(invocation.getArgument(0)));
            when(outboxRepository.findByPaymentIdAndPublishedAtIsNull(any()))
                    .thenAnswer(invocation -> unpublishedOutbox(invocation.getArgument(0)));
            when(outboxRepository.findById(any())).thenAnswer(invocation -> findOutbox(invocation.getArgument(0)));
            when(outboxRepository.save(any(PaymentOutboxEvent.class))).thenAnswer(invocation -> saveOutbox(invocation.getArgument(0)));
        }

        private synchronized Payment insertPayment(Payment candidate) {
            if (payments.stream().anyMatch(payment -> candidate.getOrderId().equals(payment.getOrderId())
                    || candidate.getPaymentReference().equals(payment.getPaymentReference()))) {
                throw new DuplicateKeyException("order_or_reference_unique");
            }
            Payment stored = copy(candidate, Payment.class);
            stored.setId("payment-" + (payments.size() + 1));
            stored.setVersion(0L);
            payments.add(stored);
            return copy(stored, Payment.class);
        }

        private synchronized Payment savePayment(Payment candidate) {
            Payment stored = payment(candidate.getId());
            if (failNextPaymentSave) {
                failNextPaymentSave = false;
                stored.setVersion(stored.getVersion() + 1);
                throw new OptimisticLockingFailureException("simulated stale version");
            }
            if (!java.util.Objects.equals(candidate.getVersion(), stored.getVersion())) {
                throw new OptimisticLockingFailureException("stale version");
            }
            Payment replacement = copy(candidate, Payment.class);
            replacement.setVersion(candidate.getVersion() + 1);
            payments.set(payments.indexOf(stored), replacement);
            return copy(replacement, Payment.class);
        }

        private synchronized PaymentOperation insertOperation(PaymentOperation candidate) {
            Optional<PaymentOperation> duplicate = operations.stream().filter(operation ->
                    operation.getOperation().equals(candidate.getOperation())
                            && operation.getUserId().equals(candidate.getUserId())
                            && operation.getIdempotencyKey().equals(candidate.getIdempotencyKey())).findFirst();
            if (duplicate.isPresent()) throw new DuplicateKeyException("operation_user_key_unique");
            PaymentOperation stored = copy(candidate, PaymentOperation.class);
            stored.setId("operation-" + (operations.size() + 1));
            stored.setVersion(0L);
            candidate.setId(stored.getId());
            candidate.setVersion(0L);
            operations.add(stored);
            return copy(stored, PaymentOperation.class);
        }

        private synchronized PaymentOperation saveOperation(PaymentOperation candidate) {
            operationSaveAttempts++;
            if (failOperationSaveOnAttempt == operationSaveAttempts) {
                throw new IllegalStateException("simulated operation interruption");
            }
            if ("COMPLETED".equals(candidate.getStatus())
                    && failOperationCompletionOnAttempt == ++operationCompletionAttempts) {
                throw new IllegalStateException("simulated operation completion interruption");
            }
            PaymentOperation replacement = copy(candidate, PaymentOperation.class);
            PaymentOperation stored = operations.stream()
                    .filter(operation -> operation.getId().equals(replacement.getId())).findFirst().orElseThrow();
            if (!java.util.Objects.equals(candidate.getVersion(), stored.getVersion())) {
                throw new OptimisticLockingFailureException("stale operation version");
            }
            replacement.setVersion(stored.getVersion() + 1);
            candidate.setVersion(replacement.getVersion());
            operations.replaceAll(operation -> operation.getId().equals(replacement.getId()) ? replacement : operation);
            return copy(replacement, PaymentOperation.class);
        }

        private synchronized Optional<PaymentOperation> findOperation(String operation, UUID userId, String key) {
            return operations.stream().filter(item -> item.getOperation().equals(operation)
                            && item.getUserId().equals(userId) && item.getIdempotencyKey().equals(key))
                    .findFirst().map(item -> copy(item, PaymentOperation.class));
        }

        private synchronized WebhookReceipt insertReceipt(WebhookReceipt candidate) {
            if (receipts.stream().anyMatch(receipt -> receipt.getProvider().equals(candidate.getProvider())
                    && receipt.getEventId().equals(candidate.getEventId()))) {
                throw new DuplicateKeyException("provider_event_unique");
            }
            WebhookReceipt stored = copy(candidate, WebhookReceipt.class);
            stored.setId("receipt-" + (receipts.size() + 1));
            stored.setVersion(0L);
            candidate.setId(stored.getId());
            candidate.setVersion(0L);
            receipts.add(stored);
            return copy(stored, WebhookReceipt.class);
        }

        private synchronized WebhookReceipt saveReceipt(WebhookReceipt candidate) {
            WebhookReceipt stored = receipts.stream()
                    .filter(receipt -> receipt.getId().equals(candidate.getId())).findFirst().orElseThrow();
            if (!java.util.Objects.equals(candidate.getVersion(), stored.getVersion())) {
                throw new OptimisticLockingFailureException("stale receipt version");
            }
            WebhookReceipt replacement = copy(candidate, WebhookReceipt.class);
            replacement.setVersion(stored.getVersion() + 1);
            candidate.setVersion(replacement.getVersion());
            receipts.replaceAll(receipt -> receipt.getId().equals(replacement.getId()) ? replacement : receipt);
            return copy(replacement, WebhookReceipt.class);
        }

        private synchronized PaymentOutboxEvent insertOutbox(PaymentOutboxEvent candidate) {
            outboxInsertAttempts++;
            if (failOutboxInsertOnAttempt == outboxInsertAttempts) {
                throw new IllegalStateException("simulated outbox interruption");
            }
            if (failNextOutboxInsert) {
                failNextOutboxInsert = false;
                throw new IllegalStateException("simulated outbox interruption");
            }
            if (candidate.getId() != null && outbox.stream().anyMatch(event -> candidate.getId().equals(event.getId()))) {
                throw new DuplicateKeyException("duplicate outbox id");
            }
            PaymentOutboxEvent stored = copy(candidate, PaymentOutboxEvent.class);
            if (stored.getId() == null) stored.setId("outbox-" + (outbox.size() + 1));
            stored.setVersion(0L);
            outbox.add(stored);
            return copy(stored, PaymentOutboxEvent.class);
        }

        private synchronized PaymentOutboxEvent saveOutbox(PaymentOutboxEvent candidate) {
            PaymentOutboxEvent stored = outbox(candidate.getId());
            if (!java.util.Objects.equals(candidate.getVersion(), stored.getVersion())) {
                throw new OptimisticLockingFailureException("stale outbox version");
            }
            PaymentOutboxEvent replacement = copy(candidate, PaymentOutboxEvent.class);
            replacement.setVersion(candidate.getVersion() + 1);
            outbox.set(outbox.indexOf(stored), replacement);
            return copy(replacement, PaymentOutboxEvent.class);
        }

        private synchronized List<PaymentOutboxEvent> dueOutbox(LocalDateTime now) {
            return outbox.stream().filter(event -> event.getPublishedAt() == null
                            && !event.getNextAttemptAt().isAfter(now))
                    .map(event -> copy(event, PaymentOutboxEvent.class)).toList();
        }

        private synchronized List<PaymentOutboxEvent> unpublishedOutbox(String paymentId) {
            return outbox.stream().filter(event -> paymentId.equals(event.getPaymentId())
                            && event.getPublishedAt() == null)
                    .map(event -> copy(event, PaymentOutboxEvent.class)).toList();
        }

        private synchronized Optional<PaymentOutboxEvent> findOutbox(String id) {
            return outbox.stream().filter(event -> event.getId().equals(id)).findFirst()
                    .map(event -> copy(event, PaymentOutboxEvent.class));
        }

        private synchronized Optional<Payment> synchronizedPaymentById(String id) {
            return payments.stream().filter(payment -> id.equals(payment.getId())).findFirst()
                    .map(payment -> copy(payment, Payment.class));
        }

        private synchronized Optional<Payment> synchronizedPaymentByReference(String reference) {
            return payments.stream().filter(payment -> reference.equals(payment.getPaymentReference())).findFirst()
                    .map(payment -> copy(payment, Payment.class));
        }

        private synchronized Optional<Payment> synchronizedPaymentByOrder(String orderId) {
            return payments.stream().filter(payment -> orderId.equals(payment.getOrderId())).findFirst()
                    .map(payment -> copy(payment, Payment.class));
        }

        private synchronized void persist(Payment payment) {
            payments.add(copy(payment, Payment.class));
        }

        private synchronized Payment payment(String id) {
            return payments.stream().filter(payment -> id.equals(payment.getId())).findFirst().orElseThrow();
        }

        private synchronized PaymentOutboxEvent outbox(String id) {
            return outbox.stream().filter(event -> id.equals(event.getId())).findFirst().orElseThrow();
        }

        private synchronized void expireLease(String id) {
            outbox(id).setLeaseUntil(LocalDateTime.now().minusSeconds(1));
        }

        private synchronized void makeOutboxDue(String id) {
            outbox(id).setNextAttemptAt(LocalDateTime.now().minusSeconds(1));
        }

        private <T> T copy(T value, Class<T> type) {
            return mapper.convertValue(value, type);
        }
    }
}
