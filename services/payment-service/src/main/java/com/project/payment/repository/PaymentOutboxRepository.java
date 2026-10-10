package com.project.payment.repository;

import com.project.payment.model.PaymentOutboxEvent;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface PaymentOutboxRepository extends MongoRepository<PaymentOutboxEvent, String> {
  List<PaymentOutboxEvent> findByPublishedAtIsNullAndNextAttemptAtLessThanEqualOrderByCreatedAtAsc(
      LocalDateTime now);

  List<PaymentOutboxEvent> findByPaymentIdAndPublishedAtIsNull(String paymentId);
}
