package com.project.payment.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Version;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

@Document(collection = "payment_webhook_receipts")
@CompoundIndex(name = "provider_event_unique", def = "{'provider': 1, 'eventId': 1}", unique = true)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WebhookReceipt {
    @Id
    private String id;
    @Version
    private Long version;
    private String provider;
    private String eventId;
    private String eventType;
    private String paymentReference;
    private LocalDateTime receivedAt;
    private String status;
    private String webhookStatus;
    private String transactionId;
    private String failureReason;
    private String outboxId;
    private String payload;
    private Long transitionSequence;
}
