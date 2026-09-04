package com.project.payment.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.mongodb.core.mapping.Document;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Document(collection = "payments")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Payment {
    @Id
    private String id;
    private String paymentReference;
    private String orderId;
    private String orderNumber;
    private UUID userId;
    private String userEmail;
    private PaymentStatus status;
    private String paymentMethod;
    private BigDecimal amount;
    @Builder.Default
    private BigDecimal refundedAmount = BigDecimal.ZERO;
    private String currency;
    private String transactionId;
    private String gatewayResponse;
    private String failureReason;
    private int retryCount;
    private String description;
    @CreatedDate
    private LocalDateTime createdAt;
    @LastModifiedDate
    private LocalDateTime updatedAt;
    private LocalDateTime completedAt;
}
