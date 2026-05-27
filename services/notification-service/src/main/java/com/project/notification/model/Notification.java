package com.project.notification.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Persisted notification. Replaces the previous in-memory ConcurrentHashMap so we
 * don't lose history on restart and so customers can list / mark-read their own.
 */
@Document(collection = "notifications")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Notification {

    @Id
    private String id;

    @Indexed
    private UUID userId;

    /** Recipient resolved address (email today; phone/push later). */
    @Indexed
    private String recipient;

    private String channel;       // EMAIL, SMS, PUSH, INAPP
    private String category;      // ORDER, PAYMENT, ACCOUNT, INVENTORY, MARKETING

    private String subject;
    private String body;

    private Status status;
    private String failureReason;
    private int retryCount;

    private LocalDateTime createdAt;
    private LocalDateTime sentAt;
    private LocalDateTime readAt;

    /** Idempotency: source eventId we processed; prevents duplicate notifications. */
    @Indexed(unique = true, sparse = true)
    private String sourceEventId;

    public enum Status { PENDING, SENT, FAILED, READ }
}
