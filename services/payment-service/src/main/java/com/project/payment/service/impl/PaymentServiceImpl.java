package com.project.payment.service.impl;

import com.project.common.event.PaymentEvent;
import com.project.common.exception.ResourceNotFoundException;
import com.project.payment.dto.PaymentRequest;
import com.project.payment.dto.PaymentResponse;
import com.project.payment.dto.PaymentWebhookRequest;
import com.project.payment.exception.PaymentException;
import com.project.payment.kafka.PaymentEventPublisher;
import com.project.payment.model.Payment;
import com.project.payment.model.PaymentStatus;
import com.project.payment.repository.PaymentRepository;
import com.project.payment.service.PaymentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentServiceImpl implements PaymentService {

    private final PaymentRepository paymentRepository;
    private final PaymentEventPublisher eventPublisher;
    private final StringRedisTemplate redis;
    private final PaymentGateway gateway;

    @Override
    @Transactional
    public PaymentResponse createPayment(PaymentRequest request, UUID userId, String userEmail,
                                         String idempotencyKey) {
        // Idempotency
        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
            String key = "payment:create:" + userId + ":" + idempotencyKey;
            String existing = redis.opsForValue().get(key);
            if (existing != null) {
                return mapToResponse(paymentRepository.findById(existing)
                        .orElseThrow(() -> new ResourceNotFoundException("Payment", existing)));
            }
            Boolean acquired = redis.opsForValue().setIfAbsent(key, "PENDING", Duration.ofMinutes(10));
            if (!Boolean.TRUE.equals(acquired)) {
                throw new PaymentException("Duplicate payment request in flight");
            }
        }

        // Disallow duplicate payments per order (in addition to idempotency-key)
        Optional<Payment> existing = paymentRepository.findByOrderId(request.getOrderId());
        if (existing.isPresent()) {
            throw new PaymentException("A payment already exists for order: " + request.getOrderId());
        }

        Payment payment = Payment.builder()
                .paymentReference("PAY-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase())
                .orderId(request.getOrderId())
                .orderNumber(request.getOrderNumber())
                .userId(userId)
                .userEmail(userEmail)
                .status(PaymentStatus.PENDING)
                .paymentMethod(request.getPaymentMethod())
                .amount(request.getAmount() != null ? request.getAmount() : BigDecimal.ZERO)
                .currency(request.getCurrency() != null ? request.getCurrency() : "INR")
                .description(request.getDescription())
                .retryCount(0)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        payment = paymentRepository.save(payment);

        // Create the gateway intent (Stripe in production; sandbox-stub for dev)
        try {
            String intentId = gateway.createIntent(payment);
            payment.setTransactionId(intentId);
            payment = paymentRepository.save(payment);
        } catch (Exception e) {
            log.error("Gateway createIntent failed for payment {}: {}", payment.getId(), e.getMessage());
            payment.setStatus(PaymentStatus.FAILED);
            payment.setFailureReason("Gateway error: " + e.getMessage());
            payment = paymentRepository.save(payment);
            eventPublisher.publish(toEvent(PaymentEvent.Type.FAILED, payment));
            throw new PaymentException("Payment initialization failed");
        }

        eventPublisher.publish(toEvent(PaymentEvent.Type.INITIATED, payment));

        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
            redis.opsForValue().set("payment:create:" + userId + ":" + idempotencyKey,
                    payment.getId(), Duration.ofHours(24));
        }
        return mapToResponse(payment);
    }

    @Override
    public PaymentResponse getPayment(String paymentId) {
        return mapToResponse(paymentRepository.findById(paymentId)
                .orElseThrow(() -> new ResourceNotFoundException("Payment", paymentId)));
    }

    @Override
    public PaymentResponse getPaymentByReference(String reference) {
        return mapToResponse(paymentRepository.findByPaymentReference(reference)
                .orElseThrow(() -> new ResourceNotFoundException("Payment", reference)));
    }

    @Override
    public PaymentResponse getPaymentByOrderId(String orderId) {
        return mapToResponse(paymentRepository.findByOrderId(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Payment for order", orderId)));
    }

    @Override
    public List<PaymentResponse> getUserPayments(UUID userId) {
        return paymentRepository.findByUserId(userId).stream().map(this::mapToResponse).collect(Collectors.toList());
    }

    /**
     * In production this is rarely called directly: PSP webhooks drive the state.
     * Kept for admin retry / dev-mode simulation.
     */
    @Override
    @Transactional
    public PaymentResponse processPayment(String paymentId) {
        Payment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new ResourceNotFoundException("Payment", paymentId));
        if (payment.getStatus() != PaymentStatus.PENDING) {
            throw new PaymentException("Payment cannot be processed; status=" + payment.getStatus());
        }
        payment.setStatus(PaymentStatus.PROCESSING);
        payment.setUpdatedAt(LocalDateTime.now());
        payment = paymentRepository.save(payment);
        eventPublisher.publish(toEvent(PaymentEvent.Type.PROCESSING, payment));

        boolean ok = gateway.confirm(payment);
        if (ok) {
            payment.setStatus(PaymentStatus.COMPLETED);
            payment.setCompletedAt(LocalDateTime.now());
            payment.setGatewayResponse("OK");
        } else {
            payment.setStatus(PaymentStatus.FAILED);
            payment.setFailureReason("Gateway declined");
        }
        payment.setUpdatedAt(LocalDateTime.now());
        payment = paymentRepository.save(payment);
        eventPublisher.publish(toEvent(ok ? PaymentEvent.Type.COMPLETED : PaymentEvent.Type.FAILED, payment));
        return mapToResponse(payment);
    }

    @Override
    @Transactional
    public PaymentResponse handlePaymentWebhook(String paymentReference, PaymentWebhookRequest webhook) {
        Payment payment = paymentRepository.findByPaymentReference(paymentReference)
                .orElseThrow(() -> new ResourceNotFoundException("Payment", paymentReference));

        if (webhook.getTransactionId() != null) payment.setTransactionId(webhook.getTransactionId());
        payment.setGatewayResponse("Webhook: " + webhook.getStatus());

        PaymentEvent.Type eventType;
        switch (webhook.getStatus().toUpperCase()) {
            case "COMPLETED", "SUCCEEDED" -> {
                payment.setStatus(PaymentStatus.COMPLETED);
                payment.setCompletedAt(LocalDateTime.now());
                eventType = PaymentEvent.Type.COMPLETED;
            }
            case "FAILED" -> {
                payment.setStatus(PaymentStatus.FAILED);
                payment.setFailureReason(webhook.getFailureReason());
                eventType = PaymentEvent.Type.FAILED;
            }
            case "CANCELLED" -> {
                payment.setStatus(PaymentStatus.CANCELLED);
                eventType = PaymentEvent.Type.CANCELLED;
            }
            default -> throw new PaymentException("Unknown webhook status: " + webhook.getStatus());
        }
        payment.setUpdatedAt(LocalDateTime.now());
        payment = paymentRepository.save(payment);
        eventPublisher.publish(toEvent(eventType, payment));
        return mapToResponse(payment);
    }

    @Override
    @Transactional
    public PaymentResponse refundPayment(String paymentId, String reason, BigDecimal amount, String idempotencyKey) {
        // Idempotency
        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
            String key = "payment:refund:" + paymentId + ":" + idempotencyKey;
            Boolean acquired = redis.opsForValue().setIfAbsent(key, "DONE", Duration.ofHours(24));
            if (!Boolean.TRUE.equals(acquired)) {
                return getPayment(paymentId);
            }
        }

        Payment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new ResourceNotFoundException("Payment", paymentId));
        if (payment.getStatus() != PaymentStatus.COMPLETED &&
            payment.getStatus() != PaymentStatus.PARTIALLY_REFUNDED) {
            throw new PaymentException("Only completed payments can be refunded");
        }

        boolean partial = amount != null && amount.signum() > 0
                && amount.compareTo(payment.getAmount()) < 0;

        gateway.refund(payment, partial ? amount : payment.getAmount(), reason);

        payment.setStatus(partial ? PaymentStatus.PARTIALLY_REFUNDED : PaymentStatus.REFUNDED);
        payment.setGatewayResponse("Refund: " + reason);
        payment.setUpdatedAt(LocalDateTime.now());
        payment = paymentRepository.save(payment);
        eventPublisher.publish(toEvent(
                partial ? PaymentEvent.Type.PARTIALLY_REFUNDED : PaymentEvent.Type.REFUNDED, payment));
        return mapToResponse(payment);
    }

    @Override
    @Transactional
    public void cancelPaymentByOrderId(String orderId) {
        Payment payment = paymentRepository.findByOrderId(orderId).orElse(null);
        if (payment == null) {
            log.info("No payment record found for order {}, skipping cancellation", orderId);
            return;
        }

        if (payment.getStatus() == PaymentStatus.PENDING || payment.getStatus() == PaymentStatus.PROCESSING) {
            payment.setStatus(PaymentStatus.CANCELLED);
            payment.setUpdatedAt(LocalDateTime.now());
            payment = paymentRepository.save(payment);
            log.info("Payment for order {} cancelled successfully", orderId);
            eventPublisher.publish(toEvent(PaymentEvent.Type.CANCELLED, payment));
        } else {
            log.info("Payment for order {} is in status {}, skipping cancellation", orderId, payment.getStatus());
        }
    }

    private PaymentEvent toEvent(PaymentEvent.Type type, Payment p) {
        return PaymentEvent.paymentEventBuilder()
                .type(type)
                .paymentId(p.getId())
                .paymentReference(p.getPaymentReference())
                .orderId(p.getOrderId())
                .userId(p.getUserId())
                .userEmail(p.getUserEmail())
                .amount(p.getAmount())
                .currency(p.getCurrency())
                .paymentMethod(p.getPaymentMethod())
                .failureReason(p.getFailureReason())
                .build();
    }

    private PaymentResponse mapToResponse(Payment payment) {
        return PaymentResponse.builder()
                .id(payment.getId())
                .paymentReference(payment.getPaymentReference())
                .orderId(payment.getOrderId())
                .orderNumber(payment.getOrderNumber())
                .userId(payment.getUserId())
                .userEmail(payment.getUserEmail())
                .status(payment.getStatus())
                .paymentMethod(payment.getPaymentMethod())
                .amount(payment.getAmount())
                .currency(payment.getCurrency())
                .transactionId(payment.getTransactionId())
                .gatewayResponse(payment.getGatewayResponse())
                .failureReason(payment.getFailureReason())
                .retryCount(payment.getRetryCount())
                .description(payment.getDescription())
                .createdAt(payment.getCreatedAt())
                .updatedAt(payment.getUpdatedAt())
                .completedAt(payment.getCompletedAt())
                .build();
    }
}
