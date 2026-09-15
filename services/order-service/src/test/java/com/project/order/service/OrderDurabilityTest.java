package com.project.order.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.project.order.application.mapper.OrderMapperImpl;
import com.project.order.application.validator.OrderRequestValidator;
import com.project.order.client.CouponClient;
import com.project.order.client.InventoryClient;
import com.project.order.client.ProductClient;
import com.project.order.client.dto.ProductSummary;
import com.project.order.dto.OrderItemRequest;
import com.project.order.dto.OrderRequest;
import com.project.order.dto.OrderResponse;
import com.project.order.exception.OrderValidationException;
import com.project.order.kafka.OrderEventPublisher;
import com.project.order.model.Order;
import com.project.order.model.OrderItem;
import com.project.order.model.OrderStatus;
import com.project.order.model.OutboxEvent;
import com.project.order.model.PaymentStatus;
import com.project.order.model.SagaState;
import com.project.order.repository.OrderRepository;
import com.project.order.service.impl.OrderServiceImpl;
import com.project.order.service.impl.OutboxEventRelay;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OrderDurabilityTest {

    @Test
    void duplicateKeyRaceAfterRestartRereadsTheSinglePersistedWinner() {
        RepositoryHarness persistence = new RepositoryHarness();
        UUID userId = UUID.randomUUID();

        OrderResponse first = service(persistence.repository(), availableProduct(), mock(InventoryClient.class))
                .createOrder(request(), userId, "customer@example.com", "checkout-10");
        persistence.hideNextIdempotencyLookup();
        OrderResponse replay = service(persistence.repository(), availableProduct(), mock(InventoryClient.class))
                .createOrder(request(), userId, "customer@example.com", "checkout-10");

        assertThat(replay.id()).isEqualTo(first.id());
        assertThat(persistence.insertedOrders()).isEqualTo(1);
        assertThat(persistence.stored().getIdempotencyKey()).isEqualTo("checkout-10");
    }

    @Test
    void reservationFailurePersistsRetryableOperationState() {
        RepositoryHarness persistence = new RepositoryHarness();
        InventoryClient unavailableInventory = mock(InventoryClient.class);
        doAnswer(invocation -> {
            throw new IllegalStateException("inventory unavailable");
        }).when(unavailableInventory).reserve(any(), any());

        assertThatThrownBy(() -> service(persistence.repository(), availableProduct(), unavailableInventory)
                .createOrder(request(), UUID.randomUUID(), "customer@example.com", "checkout-11"))
                .isInstanceOf(OrderValidationException.class);

        assertThat(persistence.savedSagaStages()).contains(SagaState.Stage.RETRYABLE);
        assertThat(persistence.stored().getStatus()).isEqualTo(OrderStatus.PENDING);
        assertThat(persistence.stored().getSagaState().getStage()).isEqualTo(SagaState.Stage.RETRYABLE);
        assertThat(persistence.stored().getSagaState().getAttempts()).isEqualTo(1);
        assertThat(persistence.stored().getSagaState().getNextAttemptAt()).isNotNull();
        assertThat(persistence.stored().getSagaState().getOperations()).singleElement().satisfies(operation ->
                assertThat(operation.getStatus()).isEqualTo(SagaState.OperationStatus.IN_PROGRESS));
    }

    @Test
    void freshServiceRetriesPersistedSagaAndCreatesDurableOutboxSnapshot() {
        RepositoryHarness persistence = new RepositoryHarness();
        InventoryClient inventory = mock(InventoryClient.class);
        AtomicInteger reservationAttempts = new AtomicInteger();
        doAnswer(invocation -> {
            if (reservationAttempts.incrementAndGet() == 1) {
                throw new IllegalStateException("inventory unavailable");
            }
            return null;
        }).when(inventory).reserve(any(), any());

        assertThatThrownBy(() -> service(persistence.repository(), availableProduct(), inventory)
                .createOrder(request(), UUID.randomUUID(), "customer@example.com", "checkout-12"))
                .isInstanceOf(OrderValidationException.class);
        persistence.makeSagaDue();

        service(persistence.repository(), availableProduct(), inventory).recoverOrders();

        Order recovered = persistence.stored();
        assertThat(reservationAttempts).hasValue(2);
        assertThat(recovered.getSagaState().getStage()).isEqualTo(SagaState.Stage.COMPLETED);
        assertThat(recovered.getOutboxEvents()).singleElement().satisfies(event -> {
            assertThat(event.getPayload()).contains("\"orderId\":\"" + recovered.getId() + "\"");
            assertThat(event.getPayload()).contains("\"totalAmount\":108.00");
            assertThat(event.getAttempts()).isZero();
            assertThat(event.getPublishedAt()).isNull();
        });
        recovered.setTotalAmount(new BigDecimal("999.00"));
        persistence.persist(recovered);
        assertThat(persistence.stored().getOutboxEvents().get(0).getPayload())
                .contains("\"totalAmount\":108.00")
                .doesNotContain("999.00");
    }

    @Test
    void freshServiceReplaysAmbiguousReservationAfterProcessCrash() {
        RepositoryHarness persistence = new RepositoryHarness();
        InventoryClient inventory = mock(InventoryClient.class);
        AtomicInteger reservationAttempts = new AtomicInteger();
        doAnswer(invocation -> {
            if (reservationAttempts.incrementAndGet() == 1) {
                throw new AssertionError("process stopped after the remote reservation");
            }
            return null;
        }).when(inventory).reserve(any(), any());

        assertThatThrownBy(() -> service(persistence.repository(), availableProduct(), inventory)
                .createOrder(request(), UUID.randomUUID(), "customer@example.com", "checkout-crash"))
                .isInstanceOf(AssertionError.class);
        assertThat(persistence.stored().getSagaState().getStage()).isEqualTo(SagaState.Stage.RESERVING);
        persistence.makeSagaDue();

        service(persistence.repository(), availableProduct(), inventory).recoverOrders();

        assertThat(reservationAttempts).hasValue(2);
        assertThat(persistence.stored().getSagaState().getStage()).isEqualTo(SagaState.Stage.COMPLETED);
        assertThat(persistence.stored().getOutboxEvents()).hasSize(1);
    }

    @Test
    void concurrentRecoveryUsesOneActiveOperationLease() throws Exception {
        RepositoryHarness persistence = new RepositoryHarness();
        persistence.persist(recoverableOrder());
        persistence.makeSagaDue();
        persistence.returnClaimedSagaForConcurrentWorker();
        InventoryClient inventory = mock(InventoryClient.class);
        CountDownLatch invocationStarted = new CountDownLatch(1);
        CountDownLatch releaseInvocation = new CountDownLatch(1);
        AtomicInteger invocations = new AtomicInteger();
        doAnswer(invocation -> {
            invocations.incrementAndGet();
            invocationStarted.countDown();
            releaseInvocation.await(5, TimeUnit.SECONDS);
            return null;
        }).when(inventory).reserve(any(), any());

        var workers = Executors.newFixedThreadPool(2);
        try {
            var first = workers.submit(() -> service(persistence.repository(), availableProduct(), inventory).recoverOrders());
            assertThat(invocationStarted.await(5, TimeUnit.SECONDS)).isTrue();
            service(persistence.repository(), availableProduct(), inventory).recoverOrders();
            assertThat(invocations).hasValue(1);
            releaseInvocation.countDown();
            first.get(5, TimeUnit.SECONDS);
        } finally {
            releaseInvocation.countDown();
            workers.shutdownNow();
        }

        assertThat(persistence.stored().getSagaState().getOperations()).singleElement().satisfies(operation ->
                assertThat(operation.getStatus()).isEqualTo(SagaState.OperationStatus.COMPLETED));
    }

    @Test
    void expiredLeaseAllowsRecoveryAndStaleOwnerCannotCompleteOperation() throws Exception {
        RepositoryHarness persistence = new RepositoryHarness();
        persistence.persist(recoverableOrder());
        persistence.makeSagaDue();
        persistence.returnClaimedSagaForConcurrentWorker();
        InventoryClient inventory = mock(InventoryClient.class);
        CountDownLatch firstInvocation = new CountDownLatch(1);
        CountDownLatch releaseInvocations = new CountDownLatch(1);
        AtomicInteger invocations = new AtomicInteger();
        doAnswer(invocation -> {
            invocations.incrementAndGet();
            firstInvocation.countDown();
            releaseInvocations.await(5, TimeUnit.SECONDS);
            return null;
        }).when(inventory).reserve(any(), any());

        var workers = Executors.newFixedThreadPool(2);
        try {
            var staleOwner = workers.submit(() -> service(persistence.repository(), availableProduct(), inventory).recoverOrders());
            assertThat(firstInvocation.await(5, TimeUnit.SECONDS)).isTrue();
            persistence.expireSagaLease();
            var newOwner = workers.submit(() -> service(persistence.repository(), availableProduct(), inventory).recoverOrders());
            awaitInvocations(invocations, 2);
            assertThat(persistence.stored().getSagaState().getOperations()).singleElement().satisfies(operation ->
                    assertThat(operation.getStatus()).isEqualTo(SagaState.OperationStatus.IN_PROGRESS));
            releaseInvocations.countDown();
            staleOwner.get(5, TimeUnit.SECONDS);
            newOwner.get(5, TimeUnit.SECONDS);
        } finally {
            releaseInvocations.countDown();
            workers.shutdownNow();
        }

        assertThat(invocations).hasValue(2);
        assertThat(persistence.stored().getSagaState().getOperations()).singleElement().satisfies(operation ->
                assertThat(operation.getStatus()).isEqualTo(SagaState.OperationStatus.COMPLETED));
    }

    @Test
    void completionConflictsConvergeWithoutRepeatingExternalOperation() {
        RepositoryHarness persistence = new RepositoryHarness();
        persistence.persist(recoverableOrder());
        persistence.makeSagaDue();
        InventoryClient inventory = mock(InventoryClient.class);
        doAnswer(invocation -> {
            persistence.setCompletionConflicts(3);
            return null;
        }).when(inventory).reserve(any(), any());

        service(persistence.repository(), availableProduct(), inventory).recoverOrders();

        assertThat(persistence.stored().getSagaState().getOperations()).singleElement().satisfies(operation -> {
            assertThat(operation.getStatus()).isEqualTo(SagaState.OperationStatus.COMPLETED);
            assertThat(operation.getLeaseToken()).isNull();
            assertThat(operation.getLeaseUntil()).isNull();
        });
        verify(inventory).reserve(any(), any());
    }

    private void awaitInvocations(AtomicInteger invocations, int expected) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (invocations.get() < expected && System.nanoTime() < deadline) {
            Thread.sleep(10);
        }
        assertThat(invocations).hasValue(expected);
    }

    @Test
    void partialReservationFailureResumesMissingWorkWithoutReusingReleasedReservation() {
        RepositoryHarness persistence = new RepositoryHarness();
        Map<String, String> lifecycle = new HashMap<>();
        AtomicInteger productTwoAttempts = new AtomicInteger();
        InventoryClient inventory = mock(InventoryClient.class);
        when(inventory.reserve(any(), any())).thenAnswer(invocation -> {
            String productId = invocation.getArgument(0);
            if ("RELEASED".equals(lifecycle.get(productId))) {
                throw new IllegalStateException("released reservation cannot be reused");
            }
            if ("product-2".equals(productId) && productTwoAttempts.incrementAndGet() == 1) {
                throw new IllegalStateException("transient inventory failure");
            }
            lifecycle.put(productId, "RESERVED");
            return null;
        });
        when(inventory.release(any(), any())).thenAnswer(invocation -> {
            lifecycle.put(invocation.getArgument(0), "RELEASED");
            return null;
        });

        assertThatThrownBy(() -> service(persistence.repository(), availableProducts(), inventory)
                .createOrder(twoItemRequest(), UUID.randomUUID(), "customer@example.com", "checkout-partial"))
                .isInstanceOf(OrderValidationException.class);
        persistence.makeSagaDue();

        service(persistence.repository(), availableProducts(), inventory).recoverOrders();

        assertThat(persistence.stored().getSagaState().getStage()).isEqualTo(SagaState.Stage.COMPLETED);
        assertThat(lifecycle).containsEntry("product-1", "RESERVED").containsEntry("product-2", "RESERVED");
    }

    @Test
    void duplicateProductLinesKeepUniqueOperationIdentityAcrossRecovery() {
        RepositoryHarness persistence = new RepositoryHarness();
        InventoryClient inventory = mock(InventoryClient.class);
        AtomicInteger quantityOneCalls = new AtomicInteger();
        AtomicInteger quantityTwoCalls = new AtomicInteger();
        when(inventory.reserve(any(), any())).thenAnswer(invocation -> {
            com.project.order.client.dto.StockReservationCommand command = invocation.getArgument(1);
            if (command.getQuantity() == 1) {
                if (quantityOneCalls.incrementAndGet() > 1) {
                    throw new IllegalStateException("first line reserved more than once");
                }
            } else if (quantityTwoCalls.incrementAndGet() == 1) {
                throw new IllegalStateException("transient second-line failure");
            }
            return null;
        });

        assertThatThrownBy(() -> service(persistence.repository(), availableProduct(), inventory)
                .createOrder(duplicateProductRequest(), UUID.randomUUID(),
                        "customer@example.com", "checkout-duplicate-lines"))
                .isInstanceOf(OrderValidationException.class);
        persistence.makeSagaDue();

        service(persistence.repository(), availableProduct(), inventory).recoverOrders();

        Order recovered = persistence.stored();
        assertThat(recovered.getSagaState().getStage()).isEqualTo(SagaState.Stage.COMPLETED);
        assertThat(recovered.getSagaState().getOperations())
                .extracting(SagaState.Operation::getId)
                .doesNotHaveDuplicates();
        assertThat(recovered.getSagaState().getOperations())
                .allSatisfy(operation -> assertThat(operation.getStatus())
                        .isEqualTo(SagaState.OperationStatus.COMPLETED));
        assertThat(quantityOneCalls).hasValue(1);
        assertThat(quantityTwoCalls).hasValue(2);
    }

    @Test
    @SuppressWarnings("unchecked")
    void relayPersistsLeaseAndRetriesImmutablePayloadUntilProducerAcknowledges() {
        RepositoryHarness persistence = new RepositoryHarness();
        persistence.persist(pendingOrder());
        String originalPayload = persistence.stored().getOutboxEvents().get(0).getPayload();
        KafkaTemplate<String, Object> kafkaTemplate = mock(KafkaTemplate.class);
        CompletableFuture<SendResult<String, Object>> failedSend = new CompletableFuture<>();
        CompletableFuture<SendResult<String, Object>> successfulSend = new CompletableFuture<>();
        when(kafkaTemplate.send(any(), any(), any())).thenReturn(failedSend, successfulSend);
        OrderEventPublisher publisher = new OrderEventPublisher(kafkaTemplate, objectMapper());

        new OutboxEventRelay(persistence.repository(), publisher).relayEvents();

        OutboxEvent leased = persistence.stored().getOutboxEvents().get(0);
        assertThat(leased.getLeaseUntil()).isNotNull();
        assertThat(leased.getAttempts()).isEqualTo(1);
        assertThat(leased.getPublishedAt()).isNull();
        assertThat(leased.getPayload()).isEqualTo(originalPayload);

        failedSend.completeExceptionally(new IllegalStateException("Kafka unavailable"));

        OutboxEvent retryable = persistence.stored().getOutboxEvents().get(0);
        assertThat(retryable.getLeaseUntil()).isNull();
        assertThat(retryable.getNextAttemptAt()).isNotNull();
        assertThat(retryable.getPublishedAt()).isNull();
        assertThat(retryable.getPayload()).isEqualTo(originalPayload);
        persistence.makeOutboxDue();

        new OutboxEventRelay(persistence.repository(), publisher).relayEvents();
        assertThat(persistence.stored().getOutboxEvents().get(0).getPublishedAt()).isNull();
        successfulSend.complete(null);

        OutboxEvent published = persistence.stored().getOutboxEvents().get(0);
        assertThat(published.getAttempts()).isEqualTo(2);
        assertThat(published.getPublishedAt()).isNotNull();
        assertThat(published.getPayload()).isEqualTo(originalPayload);
    }

    @Test
    @SuppressWarnings("unchecked")
    void staleRelayFailureCannotClearANewerWorkersLease() {
        RepositoryHarness persistence = new RepositoryHarness();
        persistence.persist(pendingOrder());
        KafkaTemplate<String, Object> kafkaTemplate = mock(KafkaTemplate.class);
        CompletableFuture<SendResult<String, Object>> firstSend = new CompletableFuture<>();
        CompletableFuture<SendResult<String, Object>> secondSend = new CompletableFuture<>();
        when(kafkaTemplate.send(any(), any(), any())).thenReturn(firstSend, secondSend);
        OrderEventPublisher publisher = new OrderEventPublisher(kafkaTemplate, objectMapper());

        new OutboxEventRelay(persistence.repository(), publisher).relayEvents();
        persistence.expireOutboxLease();
        new OutboxEventRelay(persistence.repository(), publisher).relayEvents();
        LocalDateTime secondLease = persistence.stored().getOutboxEvents().get(0).getLeaseUntil();

        firstSend.completeExceptionally(new IllegalStateException("late failure"));

        OutboxEvent stillOwnedBySecondWorker = persistence.stored().getOutboxEvents().get(0);
        assertThat(stillOwnedBySecondWorker.getLeaseUntil()).isEqualTo(secondLease);
        assertThat(stillOwnedBySecondWorker.getAttempts()).isEqualTo(2);
        assertThat(stillOwnedBySecondWorker.getNextAttemptAt()).isBeforeOrEqualTo(LocalDateTime.now());

        secondSend.complete(null);
        assertThat(persistence.stored().getOutboxEvents().get(0).getPublishedAt()).isNotNull();
    }

    @Test
    @SuppressWarnings("unchecked")
    void activeLeaseOnEarlierEventBlocksLaterEventForThatOrder() {
        RepositoryHarness persistence = new RepositoryHarness();
        persistence.persist(orderWithTwoOrderedEvents());
        KafkaTemplate<String, Object> kafkaTemplate = mock(KafkaTemplate.class);
        when(kafkaTemplate.send(any(), any(), any()))
                .thenReturn(CompletableFuture.completedFuture(null));
        OutboxEventRelay relay = new OutboxEventRelay(
                persistence.repository(), new OrderEventPublisher(kafkaTemplate, objectMapper()));

        relay.relayEvents();

        assertThat(persistence.stored().getOutboxEvents()).allSatisfy(event -> {
            assertThat(event.getPublishedAt()).isNull();
            assertThat(event.getAttempts()).isZero();
        });

        persistence.expireOutboxLease();
        relay.relayEvents();

        assertThat(persistence.stored().getOutboxEvents().get(0).getPublishedAt()).isNotNull();
        assertThat(persistence.stored().getOutboxEvents().get(1).getPublishedAt()).isNull();
        relay.relayEvents();
        assertThat(persistence.stored().getOutboxEvents().get(1).getPublishedAt()).isNotNull();
    }

    @Test
    @SuppressWarnings("unchecked")
    void duplicateBusinessEventIdsUseDistinctDurableRelayIdentity() {
        RepositoryHarness persistence = new RepositoryHarness();
        persistence.persist(orderWithDuplicateEventIds());
        KafkaTemplate<String, Object> kafkaTemplate = mock(KafkaTemplate.class);
        CompletableFuture<SendResult<String, Object>> staleFirstSend = new CompletableFuture<>();
        CompletableFuture<SendResult<String, Object>> currentFirstSend = new CompletableFuture<>();
        CompletableFuture<SendResult<String, Object>> secondEventSend = new CompletableFuture<>();
        when(kafkaTemplate.send(any(), any(), any()))
                .thenReturn(staleFirstSend, currentFirstSend, secondEventSend);
        OutboxEventRelay relay = new OutboxEventRelay(
                persistence.repository(), new OrderEventPublisher(kafkaTemplate, objectMapper()));

        relay.relayEvents();
        persistence.expireOutboxLease();
        relay.relayEvents();
        staleFirstSend.complete(null);

        assertThat(persistence.stored().getOutboxEvents()).allSatisfy(event ->
                assertThat(event.getPublishedAt()).isNull());

        currentFirstSend.complete(null);
        assertThat(persistence.stored().getOutboxEvents().get(0).getPublishedAt()).isNotNull();
        assertThat(persistence.stored().getOutboxEvents().get(1).getPublishedAt()).isNull();

        relay.relayEvents();
        secondEventSend.complete(null);

        assertThat(persistence.stored().getOutboxEvents().get(1).getPublishedAt()).isNotNull();
        assertThat(persistence.stored().getOutboxEvents())
                .extracting(OutboxEvent::getEventType)
                .containsExactly("CREATED", "STATUS_CHANGED");
    }

    @Test
    @SuppressWarnings("unchecked")
    void legacyCreatedOutboxWithoutPayloadIsSnapshottedAndPublishedOnce() {
        RepositoryHarness persistence = new RepositoryHarness();
        Order legacy = pendingOrder();
        legacy.getOutboxEvents().get(0).setPayload(null);
        persistence.persist(legacy);
        KafkaTemplate<String, Object> kafkaTemplate = mock(KafkaTemplate.class);
        when(kafkaTemplate.send(any(), any(), any()))
                .thenReturn(CompletableFuture.completedFuture(null));

        new OutboxEventRelay(persistence.repository(),
                new OrderEventPublisher(kafkaTemplate, objectMapper())).relayEvents();

        OutboxEvent migrated = persistence.stored().getOutboxEvents().get(0);
        assertThat(migrated.getPayload()).contains("\"orderId\":\"order-1\"");
        assertThat(migrated.getPublishedAt()).isNotNull();
    }

    @Test
    @SuppressWarnings("unchecked")
    void legacyMutableEventWithoutPayloadIsMarkedForManualHandling() {
        RepositoryHarness persistence = new RepositoryHarness();
        Order legacy = pendingOrder();
        legacy.getOutboxEvents().get(0).setEventType("STATUS_CHANGED");
        legacy.getOutboxEvents().get(0).setPayload(null);
        persistence.persist(legacy);
        KafkaTemplate<String, Object> kafkaTemplate = mock(KafkaTemplate.class);

        new OutboxEventRelay(persistence.repository(),
                new OrderEventPublisher(kafkaTemplate, objectMapper())).relayEvents();

        OutboxEvent manual = persistence.stored().getOutboxEvents().get(0);
        assertThat(manual.getStatus()).isEqualTo("MANUAL");
        assertThat(manual.getNextAttemptAt()).isNull();
        assertThat(manual.getProcessedAt()).isNotNull();
    }

    @Test
    void sagaStateWriteRetriesAfterOptimisticConflict() {
        RepositoryHarness persistence = new RepositoryHarness();
        InventoryClient inventory = mock(InventoryClient.class);
        AtomicInteger attempts = new AtomicInteger();
        when(inventory.reserve(any(), any())).thenAnswer(invocation -> {
            if (attempts.incrementAndGet() == 1) {
                throw new IllegalStateException("transient failure");
            }
            return null;
        });
        assertThatThrownBy(() -> service(persistence.repository(), availableProduct(), inventory)
                .createOrder(request(), UUID.randomUUID(), "customer@example.com", "checkout-version"))
                .isInstanceOf(OrderValidationException.class);
        persistence.makeSagaDue();
        persistence.failNextSaveWithOptimisticConflict();

        service(persistence.repository(), availableProduct(), inventory).recoverOrders();

        assertThat(persistence.stored().getSagaState().getStage()).isEqualTo(SagaState.Stage.COMPLETED);
    }

    @Test
    @SuppressWarnings("unchecked")
    void acknowledgedPublicationRetriesAfterOptimisticConflict() {
        RepositoryHarness persistence = new RepositoryHarness();
        persistence.persist(pendingOrder());
        KafkaTemplate<String, Object> kafkaTemplate = mock(KafkaTemplate.class);
        CompletableFuture<SendResult<String, Object>> send = new CompletableFuture<>();
        when(kafkaTemplate.send(any(), any(), any())).thenReturn(send);
        new OutboxEventRelay(persistence.repository(),
                new OrderEventPublisher(kafkaTemplate, objectMapper())).relayEvents();
        persistence.failNextSaveWithOptimisticConflict();

        send.complete(null);

        assertThat(persistence.stored().getOutboxEvents().get(0).getPublishedAt()).isNotNull();
    }

    @Test
    void paymentCommitProgressResumesAfterProcessRestart() {
        RepositoryHarness persistence = new RepositoryHarness();
        persistence.persist(durablePendingOrder());
        InventoryClient inventory = mock(InventoryClient.class);
        AtomicInteger commits = new AtomicInteger();
        when(inventory.commit(any(), any())).thenAnswer(invocation -> {
            if (commits.incrementAndGet() == 1) {
                throw new AssertionError("process stopped after inventory commit");
            }
            return null;
        });

        assertThatThrownBy(() -> service(persistence.repository(), availableProduct(), inventory)
                .onPaymentResult("order-1", "payment-1", PaymentStatus.COMPLETED))
                .isInstanceOf(AssertionError.class);
        assertThat(persistence.stored().getSagaState().getWorkflow())
                .isEqualTo(SagaState.Workflow.PAYMENT_COMPLETION);
        assertThat(persistence.stored().getSagaState().getOperations()).singleElement().satisfies(operation ->
                assertThat(operation.getStatus()).isEqualTo(SagaState.OperationStatus.IN_PROGRESS));
        persistence.makeSagaDue();

        service(persistence.repository(), availableProduct(), inventory).recoverOrders();

        assertThat(commits).hasValue(2);
        assertThat(persistence.stored().getPaymentStatus()).isEqualTo(PaymentStatus.COMPLETED);
        assertThat(persistence.stored().getStatus()).isEqualTo(OrderStatus.CONFIRMED);
    }

    @Test
    void paymentReleaseProgressResumesAfterProcessRestart() {
        RepositoryHarness persistence = new RepositoryHarness();
        persistence.persist(durablePendingOrder());
        InventoryClient inventory = mock(InventoryClient.class);
        AtomicInteger releases = new AtomicInteger();
        when(inventory.release(any(), any())).thenAnswer(invocation -> {
            if (releases.incrementAndGet() == 1) {
                throw new AssertionError("process stopped after inventory release");
            }
            return null;
        });

        assertThatThrownBy(() -> service(persistence.repository(), availableProduct(), inventory)
                .onPaymentResult("order-1", "payment-1", PaymentStatus.FAILED))
                .isInstanceOf(AssertionError.class);
        assertThat(persistence.stored().getSagaState().getWorkflow())
                .isEqualTo(SagaState.Workflow.PAYMENT_FAILURE);
        assertThat(persistence.stored().getSagaState().getOperations()).singleElement().satisfies(operation ->
                assertThat(operation.getStatus()).isEqualTo(SagaState.OperationStatus.IN_PROGRESS));
        persistence.makeSagaDue();

        service(persistence.repository(), availableProduct(), inventory).recoverOrders();

        assertThat(releases).hasValue(2);
        assertThat(persistence.stored().getPaymentStatus()).isEqualTo(PaymentStatus.FAILED);
        assertThat(persistence.stored().getStatus()).isEqualTo(OrderStatus.CANCELLED);
    }

    private OrderServiceImpl service(
            OrderRepository repository, ProductClient productClient, InventoryClient inventoryClient) {
        return new OrderServiceImpl(repository, productClient, inventoryClient, mock(CouponClient.class),
                new OrderMapperImpl(), new OrderRequestValidator(), objectMapper());
    }

    private ProductClient availableProduct() {
        ProductClient products = mock(ProductClient.class);
        ProductSummary product = new ProductSummary();
        product.setId("product-1");
        product.setSku("SKU-1");
        product.setName("Product");
        product.setPrice(new BigDecimal("50.00"));
        product.setActive(true);
        when(products.getProduct("product-1")).thenReturn(product);
        return products;
    }

    private ProductClient availableProducts() {
        ProductClient products = mock(ProductClient.class);
        when(products.getProduct(any())).thenAnswer(invocation -> product(invocation.getArgument(0)));
        return products;
    }

    private ProductSummary product(String id) {
        ProductSummary product = new ProductSummary();
        product.setId(id);
        product.setSku("SKU-" + id);
        product.setName("Product " + id);
        product.setPrice(new BigDecimal("50.00"));
        product.setActive(true);
        return product;
    }

    private OrderRequest request() {
        return new OrderRequest(List.of(new OrderItemRequest("product-1", 1)), null, null, null, null, "CARD");
    }

    private OrderRequest twoItemRequest() {
        return new OrderRequest(List.of(
                new OrderItemRequest("product-1", 1),
                new OrderItemRequest("product-2", 1)), null, null, null, null, "CARD");
    }

    private OrderRequest duplicateProductRequest() {
        return new OrderRequest(List.of(
                new OrderItemRequest("product-1", 1),
                new OrderItemRequest("product-1", 2)), null, null, null, null, "CARD");
    }

    private Order pendingOrder() {
        OutboxEvent event = OutboxEvent.builder()
                .id("event-1")
                .eventType("CREATED")
                .payload("{\"type\":\"CREATED\",\"orderId\":\"order-1\"}")
                .status("PENDING")
                .attempts(0)
                .nextAttemptAt(LocalDateTime.now().minusSeconds(1))
                .createdAt(LocalDateTime.now())
                .build();
        Order order = new Order();
        order.setId("order-1");
        order.setStatus(OrderStatus.PENDING);
        order.setPaymentStatus(PaymentStatus.PENDING);
        order.setItems(new ArrayList<>(List.of(OrderItem.builder().productId("product-1").quantity(1).build())));
        order.setOutboxEvents(new ArrayList<>(List.of(event)));
        return order;
    }

    private Order recoverableOrder() {
        Order order = pendingOrder();
        order.setOutboxEvents(new ArrayList<>());
        order.setSagaState(SagaState.builder()
                .workflow(SagaState.Workflow.CHECKOUT)
                .stage(SagaState.Stage.RETRYABLE)
                .nextAttemptAt(LocalDateTime.now())
                .operations(new ArrayList<>(List.of(SagaState.Operation.builder()
                        .id("reserve:product-1")
                        .resourceType(SagaState.ResourceType.INVENTORY)
                        .action(SagaState.Action.RESERVE)
                        .resourceId("product-1")
                        .quantity(1)
                        .status(SagaState.OperationStatus.PENDING)
                        .build())))
                .build());
        return order;
    }

    private Order durablePendingOrder() {
        Order order = pendingOrder();
        order.setOutboxEvents(new ArrayList<>());
        order.setSagaState(SagaState.builder().stage(SagaState.Stage.COMPLETED).build());
        return order;
    }

    private Order orderWithTwoOrderedEvents() {
        Order order = pendingOrder();
        OutboxEvent first = order.getOutboxEvents().get(0);
        first.setLeaseUntil(LocalDateTime.now().plusMinutes(1));
        OutboxEvent second = OutboxEvent.builder()
                .id("event-2")
                .eventType("CREATED")
                .payload("{\"type\":\"CREATED\",\"orderId\":\"order-1\"}")
                .status("PENDING")
                .nextAttemptAt(LocalDateTime.now().minusSeconds(1))
                .createdAt(LocalDateTime.now().plusSeconds(1))
                .build();
        order.getOutboxEvents().add(second);
        return order;
    }

    private Order orderWithDuplicateEventIds() {
        Order order = orderWithTwoOrderedEvents();
        OutboxEvent first = order.getOutboxEvents().get(0);
        first.setLeaseUntil(null);
        OutboxEvent second = order.getOutboxEvents().get(1);
        second.setId(first.getId());
        second.setEventType("STATUS_CHANGED");
        second.setPayload("{\"type\":\"CONFIRMED\",\"orderId\":\"order-1\"}");
        return order;
    }

    private ObjectMapper objectMapper() {
        return new ObjectMapper().findAndRegisterModules();
    }

    private final class RepositoryHarness {
        private final OrderRepository repository = mock(OrderRepository.class);
        private final ObjectMapper persistenceMapper = objectMapper();
        private final List<Order> saveHistory = new ArrayList<>();
        private Order stored;
        private boolean hideNextLookup;
        private boolean failNextSave;
        private boolean returnClaimedSaga;
        private int completionConflicts;
        private int insertedOrders;

        private RepositoryHarness() {
            when(repository.findById(any())).thenAnswer(invocation -> {
                synchronized (this) {
                    return Optional.ofNullable(copy(stored));
                }
            });
            when(repository.findByUserIdAndIdempotencyKey(any(), any())).thenAnswer(invocation -> {
                synchronized (this) {
                    if (hideNextLookup) {
                        hideNextLookup = false;
                        return Optional.empty();
                    }
                    UUID userId = invocation.getArgument(0);
                    String key = invocation.getArgument(1);
                    return stored != null && userId.equals(stored.getUserId()) && key.equals(stored.getIdempotencyKey())
                            ? Optional.of(copy(stored)) : Optional.empty();
                }
            });
            when(repository.insert(any(Order.class))).thenAnswer(invocation -> {
                synchronized (this) {
                    Order candidate = copy(invocation.getArgument(0));
                    if (stored != null && candidate.getUserId().equals(stored.getUserId())
                            && candidate.getIdempotencyKey().equals(stored.getIdempotencyKey())) {
                        throw new DuplicateKeyException("userId_1_idempotencyKey_1");
                    }
                    candidate.setId("order-" + (++insertedOrders));
                    candidate.setVersion(0L);
                    stored = copy(candidate);
                    saveHistory.add(copy(candidate));
                    return copy(candidate);
                }
            });
            when(repository.save(any(Order.class))).thenAnswer(invocation -> {
                synchronized (this) {
                    Order candidate = copy(invocation.getArgument(0));
                    if (failNextSave) {
                        failNextSave = false;
                        stored.setVersion(stored.getVersion() + 1);
                        throw new OptimisticLockingFailureException("simulated stale version");
                    }
                    if (completionConflicts > 0 && candidate.getSagaState() != null
                            && candidate.getSagaState().getOperations().stream()
                            .anyMatch(operation -> operation.getStatus() == SagaState.OperationStatus.COMPLETED)) {
                        completionConflicts--;
                        stored.setVersion(stored.getVersion() + 1);
                        throw new OptimisticLockingFailureException("simulated completion conflict");
                    }
                    if (!java.util.Objects.equals(candidate.getVersion(), stored.getVersion())) {
                        throw new OptimisticLockingFailureException("stale version");
                    }
                    candidate.setVersion(candidate.getVersion() + 1);
                    stored = candidate;
                    saveHistory.add(copy(stored));
                    return copy(stored);
                }
            });
            when(repository.findOrdersWithRecoverableSaga(any())).thenAnswer(invocation -> {
                synchronized (this) {
                    LocalDateTime now = invocation.getArgument(0);
                    if (!returnClaimedSaga && (stored == null || stored.getSagaState() == null
                            || stored.getSagaState().getStage() == SagaState.Stage.COMPLETED
                            || stored.getSagaState().getNextAttemptAt() == null
                            || stored.getSagaState().getNextAttemptAt().isAfter(now))) {
                        return List.of();
                    }
                    return List.of(copy(stored));
                }
            });
            when(repository.findOrdersWithPendingEvents("PENDING")).thenAnswer(invocation -> {
                synchronized (this) {
                    if (stored == null || stored.getOutboxEvents() == null
                            || stored.getOutboxEvents().stream().noneMatch(event -> event.getPublishedAt() == null)) {
                        return List.of();
                    }
                    return List.of(copy(stored));
                }
            });
        }

        private OrderRepository repository() {
            return repository;
        }

        private synchronized void hideNextIdempotencyLookup() {
            hideNextLookup = true;
        }

        private synchronized int insertedOrders() {
            return insertedOrders;
        }

        private synchronized Order stored() {
            return copy(stored);
        }

        private synchronized List<SagaState.Stage> savedSagaStages() {
            return saveHistory.stream()
                    .filter(order -> order.getSagaState() != null)
                    .map(order -> order.getSagaState().getStage())
                    .toList();
        }

        private synchronized void persist(Order order) {
            stored = copy(order);
            if (stored.getVersion() == null) {
                stored.setVersion(0L);
            }
        }

        private synchronized void makeSagaDue() {
            stored.getSagaState().setNextAttemptAt(LocalDateTime.now().minusSeconds(1));
            if (stored.getSagaState().getOperations() != null) {
                stored.getSagaState().getOperations().forEach(operation ->
                        operation.setLeaseUntil(LocalDateTime.now().minusSeconds(1)));
            }
            stored.setVersion(stored.getVersion() + 1);
        }

        private synchronized void returnClaimedSagaForConcurrentWorker() {
            returnClaimedSaga = true;
        }

        private synchronized void expireSagaLease() {
            stored.getSagaState().getOperations().get(0).setLeaseUntil(LocalDateTime.now().minusSeconds(1));
            stored.setVersion(stored.getVersion() + 1);
        }

        private synchronized void makeOutboxDue() {
            stored.getOutboxEvents().get(0).setNextAttemptAt(LocalDateTime.now().minusSeconds(1));
            stored.setVersion(stored.getVersion() + 1);
        }

        private synchronized void expireOutboxLease() {
            stored.getOutboxEvents().get(0).setLeaseUntil(LocalDateTime.now().minusSeconds(1));
            stored.setVersion(stored.getVersion() + 1);
        }

        private synchronized void failNextSaveWithOptimisticConflict() {
            failNextSave = true;
        }

        private synchronized void setCompletionConflicts(int conflicts) {
            completionConflicts = conflicts;
        }

        private Order copy(Order order) {
            return order == null ? null : persistenceMapper.convertValue(order, Order.class);
        }
    }
}
