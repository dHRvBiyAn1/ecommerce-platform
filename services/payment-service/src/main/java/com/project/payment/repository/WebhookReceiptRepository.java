package com.project.payment.repository;

import com.project.payment.model.WebhookReceipt;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Optional;

public interface WebhookReceiptRepository extends MongoRepository<WebhookReceipt, String> {
    Optional<WebhookReceipt> findByProviderAndEventId(String provider, String eventId);
}
