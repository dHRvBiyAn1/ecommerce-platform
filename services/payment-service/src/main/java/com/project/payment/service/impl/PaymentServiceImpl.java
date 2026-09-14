package com.project.payment.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.project.common.event.PaymentEvent;
import com.project.common.exception.ResourceNotFoundException;
import com.project.payment.api.dto.request.PaymentRequest;
import com.project.payment.api.dto.request.PaymentWebhookRequest;
import com.project.payment.api.dto.response.PaymentInitiationResponse;
import com.project.payment.api.dto.response.PaymentResponse;
import com.project.payment.application.mapper.PaymentMapper;
import com.project.payment.application.validator.PaymentOrderValidator;
import com.project.payment.application.validator.PaymentTransitionValidator;
import com.project.payment.client.OrderClient;
import com.project.payment.client.dto.OrderSummary;
import com.project.payment.exception.PaymentException;
import com.project.payment.model.Payment;
import com.project.payment.model.PaymentOperation;
import com.project.payment.model.PaymentOutboxEvent;
import com.project.payment.model.PaymentStatus;
import com.project.payment.model.WebhookReceipt;
import com.project.payment.repository.PaymentOperationRepository;
import com.project.payment.repository.PaymentOutboxRepository;
import com.project.payment.repository.PaymentRepository;
import com.project.payment.repository.WebhookReceiptRepository;
import com.project.payment.service.PaymentService;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

@Service
@RequiredArgsConstructor
public class PaymentServiceImpl implements PaymentService {

    private static final int MAX_OPTIMISTIC_ATTEMPTS = 3;

    private final PaymentRepository paymentRepository;
    private final PaymentOperationRepository operationRepository;
    private final WebhookReceiptRepository receiptRepository;
    private final PaymentOutboxRepository outboxRepository;
    private final PaymentGateway gateway;
    private final PaymentMapper paymentMapper;
    private final OrderClient orderClient;
    private final PaymentOrderValidator orderValidator;
    private final PaymentTransitionValidator transitionValidator;
    private final ObjectMapper objectMapper;

    @Override
    public PaymentInitiationResponse createPayment(PaymentRequest request, UUID userId, String userEmail,
                                                   String idempotencyKey) {
        OrderSummary order = validateOrder(request, userId);
        String durableKey = hasKey(idempotencyKey) ? idempotencyKey : UUID.randomUUID().toString();
        OperationClaim claim = claimOperation("CREATE", userId, durableKey, request.orderId(), null, null);
        PaymentOperation operation = claim.operation();

        if (!claim.created() && operation.getPaymentId() != null) {
            Payment replay = findPayment(operation.getPaymentId());
            recoverOperation(operation, replay);
            return new PaymentInitiationResponse(paymentMapper.toResponse(replay), null);
        }

        PaymentChoice choice = insertOrFindPayment(request, userId, userEmail, order, operation.getId());
        Payment payment = choice.payment();
        operation.setPaymentId(payment.getId());
        if (!operation.getId().equals(payment.getCreateOperationId())) {
            operation.setStatus("DUPLICATE");
            operation.setCompletedAt(LocalDateTime.now());
            operationRepository.save(operation);
            return new PaymentInitiationResponse(paymentMapper.toResponse(payment), null);
        }

        operation.setStatus("GATEWAY_STARTED");
        operationRepository.save(operation);
        PaymentGateway.IntentResult intent;
        try {
            intent = gateway.createIntent(payment);
        } catch (RuntimeException exception) {
            Payment failed = updatePaymentWithRetry(payment.getId(), current -> {
                transitionValidator.validate(current.getStatus(), PaymentStatus.FAILED);
                current.setStatus(PaymentStatus.FAILED);
                current.setFailureReason("Gateway error");
            });
            completeOperation(operation, PaymentEvent.Type.FAILED, failed);
            throw new PaymentException("Payment initialization failed", exception);
        }

        Payment saved = updatePaymentWithRetry(payment.getId(), current -> current.setTransactionId(intent.transactionId()));
        completeOperation(operation, PaymentEvent.Type.INITIATED, saved);
        return new PaymentInitiationResponse(paymentMapper.toResponse(saved),
                claim.created() && choice.created() ? intent.clientSecret() : null);
    }

    private OperationClaim claimOperation(String type, UUID userId, String key, String orderId,
                                          BigDecimal amount, String reason) {
        try {
            PaymentOperation operation = operationRepository.insert(PaymentOperation.builder()
                    .operation(type).userId(userId).idempotencyKey(key).orderId(orderId)
                    .amount(amount).reason(reason).status("CLAIMED").createdAt(LocalDateTime.now()).build());
            return new OperationClaim(operation, true);
        } catch (DuplicateKeyException exception) {
            PaymentOperation operation = operationRepository
                    .findByOperationAndUserIdAndIdempotencyKey(type, userId, key)
                    .orElseThrow(() -> exception);
            if (orderId != null && !orderId.equals(operation.getOrderId())) {
                throw new PaymentException("Idempotency key conflicts with another order");
            }
            return new OperationClaim(operation, false);
        }
    }

    private PaymentChoice insertOrFindPayment(PaymentRequest request, UUID userId, String userEmail,
                                              OrderSummary order, String operationId) {
        for (int attempt = 0; attempt < MAX_OPTIMISTIC_ATTEMPTS; attempt++) {
            Payment existing = paymentRepository.findByOrderId(request.orderId()).orElse(null);
            if (existing != null) return owned(existing, userId, false);
            try {
                Payment inserted = paymentRepository.insert(Payment.builder()
                        .paymentReference("PAY-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase())
                        .orderId(request.orderId()).orderNumber(order.orderNumber()).userId(userId).userEmail(userEmail)
                        .status(PaymentStatus.PENDING).paymentMethod(request.paymentMethod()).amount(order.totalAmount())
                        .currency(order.currency()).description(request.description()).retryCount(0)
                        .createOperationId(operationId).createdAt(LocalDateTime.now()).updatedAt(LocalDateTime.now())
                        .build());
                return new PaymentChoice(inserted, true);
            } catch (DuplicateKeyException exception) {
                existing = paymentRepository.findByOrderId(request.orderId()).orElse(null);
                if (existing != null) return owned(existing, userId, false);
            }
        }
        throw new PaymentException("Unable to allocate a unique payment reference");
    }

    private PaymentChoice owned(Payment payment, UUID userId, boolean created) {
        if (!userId.equals(payment.getUserId())) {
            throw new PaymentException("Payment already exists for this order");
        }
        return new PaymentChoice(payment, created);
    }

    private OrderSummary validateOrder(PaymentRequest request, UUID userId) {
        var response = orderClient.getOrder(request.orderId());
        return orderValidator.validateForPayment(response == null ? null : response.getData(), userId);
    }

    @Override
    public PaymentResponse getPayment(String paymentId) {
        return paymentMapper.toResponse(findPayment(paymentId));
    }

    @Override
    public PaymentResponse getPaymentByReference(String reference) {
        return paymentMapper.toResponse(findPaymentByReference(reference));
    }

    @Override
    public PaymentResponse getPaymentByOrderId(String orderId) {
        return paymentMapper.toResponse(paymentRepository.findByOrderId(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Payment for order", orderId)));
    }

    @Override
    public List<PaymentResponse> getUserPayments(UUID userId) {
        return paymentRepository.findByUserId(userId).stream().map(paymentMapper::toResponse).toList();
    }

    @Override
    public PaymentResponse processPayment(String paymentId) {
        Payment initial = findPayment(paymentId);
        if (gateway.requiresVerifiedWebhook()) {
            throw new PaymentException("Payment completion requires a verified webhook");
        }

        PaymentOperation processingOperation = existingOrClaimStateOperation(
                "PROCESSING", initial, paymentId, PaymentStatus.PENDING);
        Payment processing = applyStateOperation(processingOperation, PaymentEvent.Type.PROCESSING, payment -> {
            transitionValidator.validate(payment.getStatus(), PaymentStatus.PROCESSING);
            payment.setStatus(PaymentStatus.PROCESSING);
        });

        if (processing.getStatus() != PaymentStatus.PROCESSING) {
            PaymentOperation operation = operationRepository
                    .findByOperationAndUserIdAndIdempotencyKey("PROCESS", processing.getUserId(), paymentId)
                    .orElseThrow(() -> new PaymentException(
                            "Payment cannot be processed; status=" + processing.getStatus()));
            return paymentMapper.toResponse(resumeProcess(operation));
        }
        OperationClaim claim = claimOperation("PROCESS", processing.getUserId(), paymentId, processing.getOrderId(), null, null);
        PaymentOperation operation = claim.operation();
        operation.setPaymentId(paymentId);
        if (!claim.created()) return paymentMapper.toResponse(resumeProcess(operation));
        return paymentMapper.toResponse(executeProcess(operation, processing));
    }

    private Payment executeProcess(PaymentOperation operation, Payment processing) {
        operation.setStatus("GATEWAY_STARTED");
        operationRepository.save(operation);
        boolean confirmed;
        try {
            confirmed = gateway.confirm(processing);
        } catch (RuntimeException exception) {
            operation.setStatus("RECONCILE");
            operationRepository.save(operation);
            throw exception;
        }
        operation.setStatus(confirmed ? "GATEWAY_SUCCEEDED" : "GATEWAY_FAILED");
        operationRepository.save(operation);
        return finishProcess(operation, confirmed);
    }

    private Payment resumeProcess(PaymentOperation operation) {
        Payment payment = findPayment(operation.getPaymentId());
        if (operation.getPayload() != null) {
            finishOperationOutbox(operation);
            return payment;
        }
        return switch (operation.getStatus()) {
            case "CLAIMED" -> executeProcess(operation, payment);
            case "GATEWAY_SUCCEEDED" -> finishProcess(operation, true);
            case "GATEWAY_FAILED" -> finishProcess(operation, false);
            default -> throw new PaymentException("Payment operation requires reconciliation");
        };
    }

    private Payment finishProcess(PaymentOperation operation, boolean confirmed) {
        PaymentEvent.Type type = confirmed ? PaymentEvent.Type.COMPLETED : PaymentEvent.Type.FAILED;
        return applyStateOperation(operation, type, payment -> {
            PaymentStatus next = confirmed ? PaymentStatus.COMPLETED : PaymentStatus.FAILED;
            transitionValidator.validate(payment.getStatus(), next);
            payment.setStatus(next);
            if (confirmed) {
                payment.setCompletedAt(LocalDateTime.now());
                payment.setGatewayResponse("OK");
            } else {
                payment.setFailureReason("Gateway declined");
            }
        });
    }

    @Override
    public PaymentResponse handlePaymentWebhook(String paymentReference, PaymentWebhookRequest webhook) {
        String eventId = UUID.nameUUIDFromBytes((paymentReference + "|" + webhook.transactionId() + "|"
                + webhook.status() + "|" + webhook.failureReason()).getBytes(StandardCharsets.UTF_8)).toString();
        return handleVerifiedWebhook("internal", eventId, webhook.status(), paymentReference, webhook);
    }

    @Override
    public PaymentResponse handleStripeWebhook(String eventId, String eventType, String paymentReference,
                                               PaymentWebhookRequest webhook) {
        stripeType(eventType, webhook.status());
        return handleVerifiedWebhook("stripe", eventId, eventType, paymentReference, webhook);
    }

    @Override
    public PaymentResponse handleVerifiedWebhook(String provider, String eventId, String eventType,
                                                 String paymentReference, PaymentWebhookRequest webhook) {
        if (eventId == null || eventId.isBlank()) throw new PaymentException("Webhook event ID is required");
        PaymentEvent.Type type = "stripe".equals(provider)
                ? stripeType(eventType, webhook.status()) : webhookType(webhook.status());
        validateReference(paymentReference, webhook);
        Payment existing = findPaymentByReference(paymentReference);
        if (existing.getTransactionId() == null || !existing.getTransactionId().equals(webhook.transactionId())) {
            throw new PaymentException("Webhook payment reference does not match the stored payment intent");
        }
        WebhookReceipt receipt;
        try {
            receipt = receiptRepository.insert(WebhookReceipt.builder().provider(provider).eventId(eventId)
                    .eventType(eventType).paymentReference(paymentReference).webhookStatus(webhook.status())
                    .transactionId(webhook.transactionId()).failureReason(webhook.failureReason())
                    .status("RECEIVED").receivedAt(LocalDateTime.now()).build());
        } catch (DuplicateKeyException exception) {
            receipt = receiptRepository.findByProviderAndEventId(provider, eventId).orElseThrow(() -> exception);
            validateReceipt(receipt, eventType, paymentReference);
        }
        Payment payment = applyReceipt(receipt, webhook, type);
        completeReceipt(receipt, type, payment);
        return paymentMapper.toResponse(payment);
    }

    private Payment applyReceipt(WebhookReceipt receipt, PaymentWebhookRequest webhook, PaymentEvent.Type type) {
        String outboxId = stableId(receipt.getProvider(), receipt.getEventId(), type.name());
        Payment saved = updatePaymentWithRetry(findPaymentByReference(receipt.getPaymentReference()).getId(), payment -> {
            if (stateTransition(payment, outboxId) != null) return;
            if (!receipt.getProvider().equals(payment.getLastWebhookProvider())
                    || !receipt.getEventId().equals(payment.getLastWebhookEventId())) {
                if (payment.getTransactionId() == null || !payment.getTransactionId().equals(webhook.transactionId())) {
                    throw new PaymentException("Webhook payment reference does not match the stored payment intent");
                }
                applyWebhook(payment, webhook);
                payment.setLastWebhookProvider(receipt.getProvider());
                payment.setLastWebhookEventId(receipt.getEventId());
            }
            addTransition(payment, outboxId, outboxId, type);
        });
        Payment.StateTransition transition = stateTransition(saved, outboxId);
        if (receipt.getTransitionSequence() == null) {
            receipt.setOutboxId(transition.outboxId());
            if (receipt.getPayload() == null) receipt.setPayload(transition.payload());
            receipt.setTransitionSequence(transition.transitionSequence());
            receipt.setStatus("APPLIED");
            receiptRepository.save(receipt);
        }
        return saved;
    }

    @Override
    public PaymentResponse refundPayment(String paymentId, String reason, BigDecimal amount, String idempotencyKey) {
        Payment initial = findPayment(paymentId);
        Refund requested = refund(initial, amount);
        transitionValidator.validate(initial.getStatus(),
                requested.partial() ? PaymentStatus.PARTIALLY_REFUNDED : PaymentStatus.REFUNDED);
        String durableKey = hasKey(idempotencyKey) ? idempotencyKey : UUID.randomUUID().toString();
        OperationClaim operationClaim = claimOperation(
                "REFUND", initial.getUserId(), durableKey, null, requested.amount(), reason);
        PaymentOperation operation = operationClaim.operation();
        operation.setPaymentId(paymentId);

        if (!operationClaim.created()) {
            Payment current = findPayment(paymentId);
            recoverOperation(operation, current);
            if (operation.getId().equals(current.getActiveRefundOperationId())
                    || "COMPLETED".equals(operation.getStatus())) {
                return paymentMapper.toResponse(current);
            }
            throw new PaymentException("Refund operation requires reconciliation");
        }

        Payment claimed;
        try {
            claimed = claimRefund(paymentId, operation);
        } catch (RuntimeException exception) {
            operation.setStatus("BLOCKED");
            operation.setCompletedAt(LocalDateTime.now());
            operationRepository.save(operation);
            throw exception;
        }
        operation.setStatus("GATEWAY_STARTED");
        operationRepository.save(operation);
        try {
            gateway.refund(claimed, requested.amount(), reason, durableKey);
        } catch (RuntimeException exception) {
            operation.setStatus("RECONCILE");
            operationRepository.save(operation);
            throw exception;
        }

        Payment saved = finishRefund(paymentId, operation, requested.amount(), reason);
        PaymentEvent.Type type = saved.getStatus() == PaymentStatus.REFUNDED
                ? PaymentEvent.Type.REFUNDED : PaymentEvent.Type.PARTIALLY_REFUNDED;
        completeOperation(operation, type, saved);
        return paymentMapper.toResponse(saved);
    }

    private Payment claimRefund(String paymentId, PaymentOperation operation) {
        return updatePaymentWithRetry(paymentId, payment -> {
            if (payment.getActiveRefundOperationId() != null
                    && !operation.getId().equals(payment.getActiveRefundOperationId())) {
                throw new PaymentException("Another refund requires completion or reconciliation");
            }
            Refund current = refund(payment, operation.getAmount());
            transitionValidator.validate(payment.getStatus(),
                    current.partial() ? PaymentStatus.PARTIALLY_REFUNDED : PaymentStatus.REFUNDED);
            payment.setActiveRefundOperationId(operation.getId());
        });
    }

    private Payment finishRefund(String paymentId, PaymentOperation operation, BigDecimal amount, String reason) {
        return updatePaymentWithRetry(paymentId, payment -> {
            if (operation.getId().equals(payment.getLastRefundOperationId())) return;
            if (!operation.getId().equals(payment.getActiveRefundOperationId())) {
                throw new PaymentException("Refund claim ownership was lost");
            }
            applyRefund(payment, amount, reason);
            payment.setLastRefundOperationId(operation.getId());
            payment.setActiveRefundOperationId(null);
        });
    }

    private Refund refund(Payment payment, BigDecimal requestedAmount) {
        if (payment.getStatus() != PaymentStatus.COMPLETED
                && payment.getStatus() != PaymentStatus.PARTIALLY_REFUNDED) {
            throw new PaymentException("Only completed payments can be refunded");
        }
        BigDecimal refunded = payment.getRefundedAmount() == null ? BigDecimal.ZERO : payment.getRefundedAmount();
        BigDecimal remaining = payment.getAmount().subtract(refunded);
        BigDecimal amount = requestedAmount == null ? remaining : requestedAmount;
        if (amount.signum() <= 0 || amount.compareTo(remaining) > 0) {
            throw new PaymentException("Refund amount exceeds the remaining refundable amount of " + remaining);
        }
        return new Refund(amount, refunded.add(amount).compareTo(payment.getAmount()) < 0);
    }

    private void applyRefund(Payment payment, BigDecimal amount, String reason) {
        Refund current = refund(payment, amount);
        PaymentStatus next = current.partial() ? PaymentStatus.PARTIALLY_REFUNDED : PaymentStatus.REFUNDED;
        transitionValidator.validate(payment.getStatus(), next);
        BigDecimal refunded = payment.getRefundedAmount() == null ? BigDecimal.ZERO : payment.getRefundedAmount();
        payment.setRefundedAmount(refunded.add(amount));
        payment.setStatus(next);
        payment.setGatewayResponse("Refund: " + reason);
    }

    @Override
    public void cancelPaymentByOrderId(String orderId) {
        Payment payment = paymentRepository.findByOrderId(orderId).orElse(null);
        if (payment == null) return;
        PaymentOperation operation;
        try {
            operation = existingOrClaimStateOperation("CANCEL", payment, orderId,
                    PaymentStatus.PENDING, PaymentStatus.PROCESSING);
        } catch (PaymentException exception) {
            if (payment.getStatus() == PaymentStatus.CANCELLED) return;
            throw exception;
        }
        applyStateOperation(operation, PaymentEvent.Type.CANCELLED, current -> {
            transitionValidator.validate(current.getStatus(), PaymentStatus.CANCELLED);
            current.setStatus(PaymentStatus.CANCELLED);
        });
    }

    private PaymentOperation existingOrClaimStateOperation(String type, Payment payment, String key,
                                                           PaymentStatus... allowedStatuses) {
        PaymentOperation existing = operationRepository
                .findByOperationAndUserIdAndIdempotencyKey(type, payment.getUserId(), key).orElse(null);
        if (existing != null) return existing;
        if (java.util.Arrays.stream(allowedStatuses).noneMatch(status -> status == payment.getStatus())) {
            throw new PaymentException("Payment cannot be processed; status=" + payment.getStatus());
        }
        PaymentOperation operation = claimOperation(type, payment.getUserId(), key, payment.getOrderId(), null, null)
                .operation();
        operation.setPaymentId(payment.getId());
        operationRepository.save(operation);
        return operation;
    }

    private Payment applyStateOperation(PaymentOperation operation, PaymentEvent.Type type,
                                        Consumer<Payment> mutation) {
        Payment current = findPayment(operation.getPaymentId());
        if (operation.getPayload() != null) {
            finishOperationOutbox(operation);
            return current;
        }
        Payment saved = updatePaymentWithRetry(operation.getPaymentId(), payment -> {
            if (stateTransition(payment, operation.getId()) != null) return;
            mutation.accept(payment);
            addTransition(payment, operation.getId(), stableId("operation", operation.getId(), type.name()), type);
        });
        Payment.StateTransition transition = stateTransition(saved, operation.getId());
        operation.setEventType(transition.eventType());
        operation.setOutboxId(transition.outboxId());
        operation.setPayload(transition.payload());
        operation.setTransitionSequence(transition.transitionSequence());
        operation.setStatus("APPLIED");
        operationRepository.save(operation);
        finishOperationOutbox(operation);
        return saved;
    }

    private Payment.StateTransition stateTransition(Payment payment, String operationId) {
        if (payment.getStateTransitions() == null) return null;
        return payment.getStateTransitions().stream()
                .filter(transition -> operationId.equals(transition.operationId())).findFirst().orElse(null);
    }

    private void addTransition(Payment payment, String sourceId, String outboxId, PaymentEvent.Type type) {
        if (payment.getStateTransitions() == null) payment.setStateTransitions(new java.util.ArrayList<>());
        payment.getStateTransitions().add(new Payment.StateTransition(sourceId, type.name(), outboxId,
                payload(type, payment), payment.getStateTransitions().stream()
                .mapToLong(Payment.StateTransition::transitionSequence).max().orElse(0) + 1));
    }

    private Payment updatePaymentWithRetry(String paymentId, Consumer<Payment> mutation) {
        OptimisticLockingFailureException lastFailure = null;
        for (int attempt = 0; attempt < MAX_OPTIMISTIC_ATTEMPTS; attempt++) {
            Payment current = findPayment(paymentId);
            mutation.accept(current);
            current.setUpdatedAt(LocalDateTime.now());
            try {
                return paymentRepository.save(current);
            } catch (OptimisticLockingFailureException exception) {
                lastFailure = exception;
            }
        }
        throw lastFailure;
    }

    private void recoverOperation(PaymentOperation operation, Payment payment) {
        if (operation.getPayload() != null) {
            finishOperationOutbox(operation);
            return;
        }
        if ("CREATE".equals(operation.getOperation())) {
            if (payment.getTransactionId() != null) completeOperation(operation, PaymentEvent.Type.INITIATED, payment);
            else if (payment.getStatus() == PaymentStatus.FAILED) completeOperation(operation, PaymentEvent.Type.FAILED, payment);
        } else if (operation.getId().equals(payment.getLastRefundOperationId())) {
            PaymentEvent.Type type = payment.getStatus() == PaymentStatus.REFUNDED
                    ? PaymentEvent.Type.REFUNDED : PaymentEvent.Type.PARTIALLY_REFUNDED;
            completeOperation(operation, type, payment);
        }
    }

    private void completeOperation(PaymentOperation operation, PaymentEvent.Type type, Payment payment) {
        if (operation.getPayload() == null) {
            Payment saved = updatePaymentWithRetry(payment.getId(), current -> {
                if (stateTransition(current, operation.getId()) == null) {
                    addTransition(current, operation.getId(), stableId("operation", operation.getId(), type.name()), type);
                }
            });
            Payment.StateTransition transition = stateTransition(saved, operation.getId());
            operation.setEventType(transition.eventType());
            operation.setOutboxId(transition.outboxId());
            operation.setPayload(transition.payload());
            operation.setTransitionSequence(transition.transitionSequence());
            operation.setStatus("APPLIED");
            operationRepository.save(operation);
        }
        finishOperationOutbox(operation);
    }

    private void finishOperationOutbox(PaymentOperation operation) {
        insertOutbox(PaymentOutboxEvent.builder().id(operation.getOutboxId()).paymentId(operation.getPaymentId())
                .eventType(operation.getEventType()).payload(operation.getPayload())
                .transitionSequence(operation.getTransitionSequence()).attempts(0)
                .nextAttemptAt(LocalDateTime.now()).createdAt(LocalDateTime.now()).build());
        operation.setStatus("COMPLETED");
        operation.setCompletedAt(LocalDateTime.now());
        operationRepository.save(operation);
    }

    private void completeReceipt(WebhookReceipt receipt, PaymentEvent.Type type, Payment payment) {
        insertOutbox(PaymentOutboxEvent.builder().id(receipt.getOutboxId()).paymentId(payment.getId())
                .eventType(type.name()).payload(receipt.getPayload())
                .transitionSequence(receipt.getTransitionSequence()).attempts(0)
                .nextAttemptAt(LocalDateTime.now()).createdAt(LocalDateTime.now()).build());
        receipt.setStatus("COMPLETED");
        receiptRepository.save(receipt);
    }

    private void insertOutbox(PaymentOutboxEvent event) {
        try {
            outboxRepository.insert(event);
        } catch (DuplicateKeyException ignored) {
            // Deterministic ID means an earlier phase already persisted the same immutable snapshot.
        }
    }

    private String payload(PaymentEvent.Type type, Payment payment) {
        try {
            return objectMapper.writeValueAsString(toEvent(type, payment));
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Unable to snapshot payment event", exception);
        }
    }

    private String stableId(String namespace, String sourceId, String type) {
        return UUID.nameUUIDFromBytes((namespace + ":" + sourceId + ":" + type)
                .getBytes(StandardCharsets.UTF_8)).toString();
    }

    private void applyWebhook(Payment payment, PaymentWebhookRequest webhook) {
        PaymentStatus next = switch (webhook.status().toUpperCase()) {
            case "COMPLETED", "SUCCEEDED" -> PaymentStatus.COMPLETED;
            case "FAILED" -> PaymentStatus.FAILED;
            case "CANCELLED" -> PaymentStatus.CANCELLED;
            default -> throw new PaymentException("Unknown webhook status: " + webhook.status());
        };
        transitionValidator.validate(payment.getStatus(), next);
        payment.setStatus(next);
        payment.setGatewayResponse("Webhook: " + webhook.status());
        if (next == PaymentStatus.COMPLETED) payment.setCompletedAt(LocalDateTime.now());
        if (next == PaymentStatus.FAILED) payment.setFailureReason(webhook.failureReason());
    }

    private PaymentEvent.Type webhookType(String status) {
        return switch (status.toUpperCase()) {
            case "COMPLETED", "SUCCEEDED" -> PaymentEvent.Type.COMPLETED;
            case "FAILED" -> PaymentEvent.Type.FAILED;
            case "CANCELLED" -> PaymentEvent.Type.CANCELLED;
            default -> throw new PaymentException("Unknown webhook status: " + status);
        };
    }

    private PaymentEvent.Type stripeType(String eventType, String status) {
        if ("payment_intent.succeeded".equals(eventType)
                && ("COMPLETED".equalsIgnoreCase(status) || "SUCCEEDED".equalsIgnoreCase(status))) {
            return PaymentEvent.Type.COMPLETED;
        }
        if ("payment_intent.payment_failed".equals(eventType) && "FAILED".equalsIgnoreCase(status)) {
            return PaymentEvent.Type.FAILED;
        }
        throw new PaymentException("Unsupported Stripe event type or status");
    }

    private void validateReference(String paymentReference, PaymentWebhookRequest webhook) {
        if (paymentReference == null || paymentReference.isBlank()
                || webhook.paymentReference() == null || !paymentReference.equals(webhook.paymentReference())) {
            throw new PaymentException("Webhook payment reference mismatch");
        }
    }

    private void validateReceipt(WebhookReceipt receipt, String eventType, String paymentReference) {
        if (!eventType.equals(receipt.getEventType()) || !paymentReference.equals(receipt.getPaymentReference())) {
            throw new PaymentException("Webhook event ID conflicts with its stored type or payment reference");
        }
    }

    private Payment findPayment(String paymentId) {
        return paymentRepository.findById(paymentId)
                .orElseThrow(() -> new ResourceNotFoundException("Payment", paymentId));
    }

    private Payment findPaymentByReference(String reference) {
        return paymentRepository.findByPaymentReference(reference)
                .orElseThrow(() -> new ResourceNotFoundException("Payment", reference));
    }

    private boolean hasKey(String key) {
        return key != null && !key.isBlank();
    }

    private PaymentEvent toEvent(PaymentEvent.Type type, Payment payment) {
        return PaymentEvent.paymentEventBuilder().type(type).paymentId(payment.getId())
                .paymentReference(payment.getPaymentReference()).orderId(payment.getOrderId())
                .userId(payment.getUserId()).userEmail(payment.getUserEmail()).amount(payment.getAmount())
                .currency(payment.getCurrency()).paymentMethod(payment.getPaymentMethod())
                .failureReason(payment.getFailureReason()).build();
    }

    private record OperationClaim(PaymentOperation operation, boolean created) {}
    private record PaymentChoice(Payment payment, boolean created) {}
    private record Refund(BigDecimal amount, boolean partial) {}
}
