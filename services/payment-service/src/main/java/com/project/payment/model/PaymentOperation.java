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

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Document(collection = "payment_operations")
@CompoundIndex(name = "operation_user_key_unique",
        def = "{'operation': 1, 'userId': 1, 'idempotencyKey': 1}", unique = true)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PaymentOperation {
    @Id
    private String id;
    @Version
    private Long version;
    private String operation;
    private UUID userId;
    private String idempotencyKey;
    private String paymentId;
    private String orderId;
    private String status;
    private BigDecimal amount;
    private String reason;
    private LocalDateTime createdAt;
    private LocalDateTime completedAt;
    private String eventType;
    private String outboxId;
    private String payload;
    private Long transitionSequence;
}
