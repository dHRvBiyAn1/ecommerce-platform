package com.project.payment.repository;

import com.project.payment.model.PaymentOutboxEvent;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.time.LocalDateTime;
import java.util.List;

public interface PaymentOutboxRepository extends MongoRepository<PaymentOutboxEvent, String> {
    List<PaymentOutboxEvent> findByPublishedAtIsNullAndNextAttemptAtLessThanEqualOrderByCreatedAtAsc(LocalDateTime now);
    List<PaymentOutboxEvent> findByPaymentIdAndPublishedAtIsNull(String paymentId);
}
