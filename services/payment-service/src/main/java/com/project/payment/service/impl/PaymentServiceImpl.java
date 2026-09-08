package com.project.payment.service.impl;

import com.project.common.event.PaymentEvent;
import com.project.common.exception.ResourceNotFoundException;
import com.project.payment.api.dto.request.PaymentRequest;
import com.project.payment.api.dto.request.PaymentWebhookRequest;
import com.project.payment.api.dto.response.PaymentResponse;
import com.project.payment.api.dto.response.PaymentInitiationResponse;
import com.project.payment.application.mapper.PaymentMapper;
import com.project.payment.application.validator.PaymentOrderValidator;
import com.project.payment.application.validator.PaymentTransitionValidator;
import com.project.payment.client.OrderClient;
import com.project.payment.client.dto.OrderSummary;
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
    private final PaymentMapper paymentMapper;
    private final OrderClient orderClient;
    private final PaymentOrderValidator orderValidator;
    private final PaymentTransitionValidator transitionValidator;

    @Override
    @Transactional
    public PaymentInitiationResponse createPayment(PaymentRequest request, UUID userId, String userEmail,
                                                   String idempotencyKey) {
        String redisKey = null;
        String existingPaymentId = null;
        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
            redisKey = "payment:create:" + userId + ":" + idempotencyKey;
            String existing = redis.opsForValue().get(redisKey);
            if (existing != null) {
                existingPaymentId = existing;
            }
            if (existingPaymentId == null && !Boolean.TRUE.equals(redis.opsForValue()
                    .setIfAbsent(redisKey, "PENDING", Duration.ofMinutes(10)))) {
                throw new PaymentException("Duplicate payment request in flight");
            }
        }

        try {
            OrderSummary order = validateOrder(request, userId);
            if (existingPaymentId != null) {
                if ("PENDING".equals(existingPaymentId)) {
                    throw new PaymentException("Duplicate payment request in flight");
                }
                String paymentId = existingPaymentId;
                return new PaymentInitiationResponse(paymentMapper.toResponse(paymentRepository.findById(paymentId)
                        .orElseThrow(() -> new ResourceNotFoundException("Payment", paymentId))), null);
            }

            PaymentInitiationResponse response = createNewPayment(request, userId, userEmail, order);
            if (redisKey != null) {
                redis.opsForValue().set(redisKey, response.payment().id(), Duration.ofHours(24));
            }
            return response;
        } catch (RuntimeException exception) {
            if (redisKey != null) {
                redis.delete(redisKey);
            }
            throw exception;
        }
    }

    private OrderSummary validateOrder(PaymentRequest request, UUID userId) {
        var orderResponse = orderClient.getOrder(request.orderId());
        return orderValidator.validateForPayment(
                orderResponse == null ? null : orderResponse.getData(), userId);
    }

    private PaymentInitiationResponse createNewPayment(PaymentRequest request, UUID userId, String userEmail,
                                                       OrderSummary order) {
        Optional<Payment> existing = paymentRepository.findByOrderId(request.orderId());
        if (existing.isPresent()) {
            throw new PaymentException("A payment already exists for order: " + request.orderId());
        }

        Payment payment = Payment.builder()
                .paymentReference("PAY-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase())
                .orderId(request.orderId())
                .orderNumber(order.orderNumber())
                .userId(userId)
                .userEmail(userEmail)
                .status(PaymentStatus.PENDING)
                .paymentMethod(request.paymentMethod())
                .amount(order.totalAmount())
                .currency(order.currency())
                .description(request.description())
                .retryCount(0)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        payment = paymentRepository.save(payment);

        // Create the gateway intent (Stripe in production; sandbox-stub for dev)
        String clientSecret;
        try {
            PaymentGateway.IntentResult intent = gateway.createIntent(payment);
            payment.setTransactionId(intent.transactionId());
            clientSecret = intent.clientSecret();
            payment = paymentRepository.save(payment);
        } catch (Exception e) {
            log.error("Gateway createIntent failed for payment {}: {}", payment.getId(), e.getMessage());
            transitionValidator.validate(payment.getStatus(), PaymentStatus.FAILED);
            payment.setStatus(PaymentStatus.FAILED);
            payment.setFailureReason("Gateway error: " + e.getMessage());
            payment = paymentRepository.save(payment);
            eventPublisher.publish(toEvent(PaymentEvent.Type.FAILED, payment));
            throw new PaymentException("Payment initialization failed");
        }

        eventPublisher.publish(toEvent(PaymentEvent.Type.INITIATED, payment));
        return new PaymentInitiationResponse(paymentMapper.toResponse(payment), clientSecret);
    }

    @Override
    public PaymentResponse getPayment(String paymentId) {
        return paymentMapper.toResponse(paymentRepository.findById(paymentId)
                .orElseThrow(() -> new ResourceNotFoundException("Payment", paymentId)));
    }

    @Override
    public PaymentResponse getPaymentByReference(String reference) {
        return paymentMapper.toResponse(paymentRepository.findByPaymentReference(reference)
                .orElseThrow(() -> new ResourceNotFoundException("Payment", reference)));
    }

    @Override
    public PaymentResponse getPaymentByOrderId(String orderId) {
        return paymentMapper.toResponse(paymentRepository.findByOrderId(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Payment for order", orderId)));
    }

    @Override
    public List<PaymentResponse> getUserPayments(UUID userId) {
        return paymentRepository.findByUserId(userId).stream().map(paymentMapper::toResponse).collect(Collectors.toList());
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
        if (gateway.requiresVerifiedWebhook()) {
            throw new PaymentException("Payment completion requires a verified webhook");
        }
        transitionValidator.validate(payment.getStatus(), PaymentStatus.PROCESSING);
        payment.setStatus(PaymentStatus.PROCESSING);
        payment.setUpdatedAt(LocalDateTime.now());
        payment = paymentRepository.save(payment);
        eventPublisher.publish(toEvent(PaymentEvent.Type.PROCESSING, payment));

        boolean ok = gateway.confirm(payment);
        if (ok) {
            transitionValidator.validate(payment.getStatus(), PaymentStatus.COMPLETED);
            payment.setStatus(PaymentStatus.COMPLETED);
            payment.setCompletedAt(LocalDateTime.now());
            payment.setGatewayResponse("OK");
        } else {
            transitionValidator.validate(payment.getStatus(), PaymentStatus.FAILED);
            payment.setStatus(PaymentStatus.FAILED);
            payment.setFailureReason("Gateway declined");
        }
        payment.setUpdatedAt(LocalDateTime.now());
        payment = paymentRepository.save(payment);
        eventPublisher.publish(toEvent(ok ? PaymentEvent.Type.COMPLETED : PaymentEvent.Type.FAILED, payment));
        return paymentMapper.toResponse(payment);
    }

    @Override
    @Transactional
    public PaymentResponse handlePaymentWebhook(String paymentReference, PaymentWebhookRequest webhook) {
        Payment payment = paymentRepository.findByPaymentReference(paymentReference)
                .orElseThrow(() -> new ResourceNotFoundException("Payment", paymentReference));

        if (webhook.transactionId() != null) payment.setTransactionId(webhook.transactionId());
        payment.setGatewayResponse("Webhook: " + webhook.status());

        PaymentEvent.Type eventType;
        switch (webhook.status().toUpperCase()) {
            case "COMPLETED", "SUCCEEDED" -> {
                transitionValidator.validate(payment.getStatus(), PaymentStatus.COMPLETED);
                payment.setStatus(PaymentStatus.COMPLETED);
                payment.setCompletedAt(LocalDateTime.now());
                eventType = PaymentEvent.Type.COMPLETED;
            }
            case "FAILED" -> {
                transitionValidator.validate(payment.getStatus(), PaymentStatus.FAILED);
                payment.setStatus(PaymentStatus.FAILED);
                payment.setFailureReason(webhook.failureReason());
                eventType = PaymentEvent.Type.FAILED;
            }
            case "CANCELLED" -> {
                transitionValidator.validate(payment.getStatus(), PaymentStatus.CANCELLED);
                payment.setStatus(PaymentStatus.CANCELLED);
                eventType = PaymentEvent.Type.CANCELLED;
            }
            default -> throw new PaymentException("Unknown webhook status: " + webhook.status());
        }
        payment.setUpdatedAt(LocalDateTime.now());
        payment = paymentRepository.save(payment);
        eventPublisher.publish(toEvent(eventType, payment));
        return paymentMapper.toResponse(payment);
    }

    @Override
    @Transactional
    public PaymentResponse refundPayment(String paymentId, String reason, BigDecimal amount, String idempotencyKey) {
        String redisKey = null;
        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
            redisKey = "payment:refund:" + paymentId + ":" + idempotencyKey;
            Boolean acquired = redis.opsForValue().setIfAbsent(redisKey, "PENDING", Duration.ofMinutes(10));
            if (!Boolean.TRUE.equals(acquired)) {
                return getPayment(paymentId);
            }
        }

        try {
            Payment payment = paymentRepository.findById(paymentId)
                    .orElseThrow(() -> new ResourceNotFoundException("Payment", paymentId));
            if (payment.getStatus() != PaymentStatus.COMPLETED &&
                payment.getStatus() != PaymentStatus.PARTIALLY_REFUNDED) {
                throw new PaymentException("Only completed payments can be refunded");
            }

            BigDecimal alreadyRefunded = payment.getRefundedAmount() == null
                    ? BigDecimal.ZERO : payment.getRefundedAmount();
            BigDecimal remaining = payment.getAmount().subtract(alreadyRefunded);
            BigDecimal refundAmount = amount == null ? remaining : amount;
            if (refundAmount.signum() <= 0 || refundAmount.compareTo(remaining) > 0) {
                throw new PaymentException("Refund amount exceeds the remaining refundable amount of " + remaining);
            }
            BigDecimal cumulativeRefund = alreadyRefunded.add(refundAmount);
            boolean partial = cumulativeRefund.compareTo(payment.getAmount()) < 0;
            PaymentStatus nextStatus = partial ? PaymentStatus.PARTIALLY_REFUNDED : PaymentStatus.REFUNDED;
            transitionValidator.validate(payment.getStatus(), nextStatus);

            gateway.refund(payment, refundAmount, reason);

            payment.setStatus(nextStatus);
            payment.setRefundedAmount(cumulativeRefund);
            payment.setGatewayResponse("Refund: " + reason);
            payment.setUpdatedAt(LocalDateTime.now());
            payment = paymentRepository.save(payment);
            eventPublisher.publish(toEvent(
                    partial ? PaymentEvent.Type.PARTIALLY_REFUNDED : PaymentEvent.Type.REFUNDED, payment));
            if (redisKey != null) {
                redis.opsForValue().set(redisKey, payment.getId(), Duration.ofHours(24));
            }
            return paymentMapper.toResponse(payment);
        } catch (RuntimeException exception) {
            if (redisKey != null) {
                redis.delete(redisKey);
            }
            throw exception;
        }
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
            transitionValidator.validate(payment.getStatus(), PaymentStatus.CANCELLED);
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

}
