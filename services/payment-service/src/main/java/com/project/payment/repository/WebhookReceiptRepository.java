package com.project.payment.repository;

import com.project.payment.model.WebhookReceipt;
import java.util.Optional;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface WebhookReceiptRepository extends MongoRepository<WebhookReceipt, String> {
  Optional<WebhookReceipt> findByProviderAndEventId(String provider, String eventId);
}
