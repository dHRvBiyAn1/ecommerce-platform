package com.project.payment.repository;

import com.project.payment.model.Payment;
import com.project.payment.model.PaymentStatus;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PaymentRepository extends MongoRepository<Payment, String> {
    Optional<Payment> findByPaymentReference(String paymentReference);
    Optional<Payment> findByOrderId(String orderId);
    List<Payment> findByUserId(UUID userId);
    List<Payment> findByStatus(PaymentStatus status);
}
