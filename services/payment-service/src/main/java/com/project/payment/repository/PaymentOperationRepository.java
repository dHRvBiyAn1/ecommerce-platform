package com.project.payment.repository;

import com.project.payment.model.PaymentOperation;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Optional;
import java.util.UUID;

public interface PaymentOperationRepository extends MongoRepository<PaymentOperation, String> {
    Optional<PaymentOperation> findByOperationAndUserIdAndIdempotencyKey(
            String operation, UUID userId, String idempotencyKey);
}
