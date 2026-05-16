package com.project.payment.service.impl;

import com.project.common.event.PaymentEvent;
import com.project.payment.dto.PaymentRequest;
import com.project.payment.dto.PaymentResponse;
import com.project.payment.dto.PaymentWebhookRequest;
import com.project.payment.exception.PaymentException;
import com.project.payment.exception.ResourceNotFoundException;
import com.project.payment.kafka.PaymentEventPublisher;
import com.project.payment.model.Payment;
import com.project.payment.model.PaymentStatus;
import com.project.payment.repository.PaymentRepository;
import com.project.payment.service.PaymentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class PaymentServiceImpl implements PaymentService {

    private final PaymentRepository paymentRepository;
    private final PaymentEventPublisher eventPublisher;
    private final Random random = new Random();

    @Override
    @Transactional
    public PaymentResponse createPayment(PaymentRequest request, UUID userId, String userEmail) {
        log.info("Creating payment for orderId={}, userId={}, amount={}",
                request.getOrderId(), userId, request.getAmount());

        Optional<Payment> existingPayment = paymentRepository.findByOrderId(request.getOrderId());
        if (existingPayment.isPresent()) {
            log.warn("Payment already exists for orderId={}", request.getOrderId());
            throw new PaymentException("A payment already exists for order: " + request.getOrderId());
        }

        Payment payment = Payment.builder()
                .paymentReference(generatePaymentReference())
                .orderId(request.getOrderId())
                .orderNumber(request.getOrderNumber())
                .userId(userId)
                .userEmail(userEmail)
                .status(PaymentStatus.PENDING)
                .paymentMethod(request.getPaymentMethod())
                .amount(request.getAmount() != null ? request.getAmount() : BigDecimal.ZERO)
                .currency(request.getCurrency() != null ? request.getCurrency() : "USD")
                .description(request.getDescription())
                .retryCount(0)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        payment = paymentRepository.save(payment);
        log.info("Payment created with reference={}, id={}", payment.getPaymentReference(), payment.getId());

        PaymentEvent event = new PaymentEvent(
                PaymentEvent.Type.INITIATED,
                payment.getId(),
                payment.getOrderId(),
                payment.getAmount(),
                payment.getCurrency(),
                payment.getCreatedAt()
        );
        eventPublisher.publish(event);

        return mapToResponse(payment);
    }

    @Override
    public PaymentResponse getPayment(String paymentId) {
        log.debug("Fetching payment by id={}", paymentId);
        Payment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> {
                    log.warn("Payment not found with id={}", paymentId);
                    return new ResourceNotFoundException("Payment not found with id: " + paymentId);
                });
        return mapToResponse(payment);
    }

    @Override
    public PaymentResponse getPaymentByReference(String reference) {
        log.debug("Fetching payment by reference={}", reference);
        Payment payment = paymentRepository.findByPaymentReference(reference)
                .orElseThrow(() -> {
                    log.warn("Payment not found with reference={}", reference);
                    return new ResourceNotFoundException("Payment not found with reference: " + reference);
                });
        return mapToResponse(payment);
    }

    @Override
    public PaymentResponse getPaymentByOrderId(String orderId) {
        log.debug("Fetching payment by orderId={}", orderId);
        Payment payment = paymentRepository.findByOrderId(orderId)
                .orElseThrow(() -> {
                    log.warn("Payment not found for orderId={}", orderId);
                    return new ResourceNotFoundException("Payment not found for order: " + orderId);
                });
        return mapToResponse(payment);
    }

    @Override
    public List<PaymentResponse> getUserPayments(UUID userId) {
        log.debug("Fetching payments for userId={}", userId);
        List<Payment> payments = paymentRepository.findByUserId(userId);
        return payments.stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public PaymentResponse processPayment(String paymentId) {
        log.info("Processing payment with id={}", paymentId);

        Payment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> {
                    log.warn("Payment not found with id={}", paymentId);
                    return new ResourceNotFoundException("Payment not found with id: " + paymentId);
                });

        if (payment.getStatus() != PaymentStatus.PENDING) {
            log.warn("Payment {} cannot be processed, current status={}", paymentId, payment.getStatus());
            throw new PaymentException(
                    "Payment cannot be processed. Current status: " + payment.getStatus());
        }

        payment.setStatus(PaymentStatus.PROCESSING);
        payment.setUpdatedAt(LocalDateTime.now());
        payment = paymentRepository.save(payment);

        PaymentEvent processingEvent = new PaymentEvent(
                PaymentEvent.Type.PROCESSING,
                payment.getId(),
                payment.getOrderId(),
                payment.getAmount(),
                payment.getCurrency(),
                payment.getUpdatedAt()
        );
        eventPublisher.publish(processingEvent);

        log.info("Simulating payment gateway call for paymentId={}", paymentId);

        boolean success = random.nextDouble() < 0.9;
        if (success) {
            payment.setStatus(PaymentStatus.COMPLETED);
            payment.setTransactionId("TXN-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase());
            payment.setGatewayResponse("Payment processed successfully");
            payment.setCompletedAt(LocalDateTime.now());
            log.info("Payment {} completed successfully, transactionId={}",
                    paymentId, payment.getTransactionId());

            PaymentEvent completedEvent = new PaymentEvent(
                    PaymentEvent.Type.COMPLETED,
                    payment.getId(),
                    payment.getOrderId(),
                    payment.getAmount(),
                    payment.getCurrency(),
                    payment.getCompletedAt()
            );
            eventPublisher.publish(completedEvent);
        } else {
            payment.setStatus(PaymentStatus.FAILED);
            payment.setFailureReason("Simulated gateway error");
            log.warn("Payment {} failed during processing", paymentId);

            PaymentEvent failedEvent = new PaymentEvent(
                    PaymentEvent.Type.FAILED,
                    payment.getId(),
                    payment.getOrderId(),
                    payment.getAmount(),
                    payment.getCurrency(),
                    LocalDateTime.now()
            );
            eventPublisher.publish(failedEvent);
        }

        payment.setUpdatedAt(LocalDateTime.now());
        payment = paymentRepository.save(payment);

        return mapToResponse(payment);
    }

    @Override
    @Transactional
    public PaymentResponse handlePaymentWebhook(String paymentReference, PaymentWebhookRequest webhook) {
        log.info("Handling webhook for paymentReference={}, status={}",
                paymentReference, webhook.getStatus());

        Payment payment = paymentRepository.findByPaymentReference(paymentReference)
                .orElseThrow(() -> {
                    log.warn("Payment not found with reference={}", paymentReference);
                    return new ResourceNotFoundException(
                            "Payment not found with reference: " + paymentReference);
                });

        payment.setTransactionId(webhook.getTransactionId());
        payment.setGatewayResponse("Webhook received: " + webhook.getStatus());

        PaymentEvent.Type eventType;
        switch (webhook.getStatus().toUpperCase()) {
            case "COMPLETED":
                payment.setStatus(PaymentStatus.COMPLETED);
                payment.setCompletedAt(LocalDateTime.now());
                eventType = PaymentEvent.Type.COMPLETED;
                log.info("Webhook marked payment {} as COMPLETED", paymentReference);
                break;
            case "FAILED":
                payment.setStatus(PaymentStatus.FAILED);
                payment.setFailureReason(webhook.getFailureReason());
                eventType = PaymentEvent.Type.FAILED;
                log.warn("Webhook marked payment {} as FAILED, reason={}",
                        paymentReference, webhook.getFailureReason());
                break;
            default:
                log.warn("Unknown webhook status={} for paymentReference={}",
                        webhook.getStatus(), paymentReference);
                throw new PaymentException("Unknown webhook status: " + webhook.getStatus());
        }

        payment.setUpdatedAt(LocalDateTime.now());
        payment = paymentRepository.save(payment);

        PaymentEvent event = new PaymentEvent(
                eventType,
                payment.getId(),
                payment.getOrderId(),
                payment.getAmount(),
                payment.getCurrency(),
                payment.getUpdatedAt()
        );
        eventPublisher.publish(event);

        return mapToResponse(payment);
    }

    @Override
    @Transactional
    public PaymentResponse refundPayment(String paymentId, String reason) {
        log.info("Processing refund for paymentId={}, reason={}", paymentId, reason);

        Payment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> {
                    log.warn("Payment not found with id={}", paymentId);
                    return new ResourceNotFoundException("Payment not found with id: " + paymentId);
                });

        if (payment.getStatus() != PaymentStatus.COMPLETED) {
            log.warn("Payment {} cannot be refunded, current status={}",
                    paymentId, payment.getStatus());
            throw new PaymentException(
                    "Only completed payments can be refunded. Current status: " + payment.getStatus());
        }

        payment.setStatus(PaymentStatus.REFUNDED);
        payment.setGatewayResponse("Refund processed: " + reason);
        payment.setUpdatedAt(LocalDateTime.now());
        payment = paymentRepository.save(payment);

        log.info("Payment {} refunded successfully", paymentId);

        PaymentEvent event = new PaymentEvent(
                PaymentEvent.Type.REFUNDED,
                payment.getId(),
                payment.getOrderId(),
                payment.getAmount(),
                payment.getCurrency(),
                payment.getUpdatedAt()
        );
        eventPublisher.publish(event);

        return mapToResponse(payment);
    }

    private String generatePaymentReference() {
        return "PAY-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
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
