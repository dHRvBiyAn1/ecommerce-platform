package com.project.common.event;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@Builder(builderMethodName = "paymentEventBuilder")
@JsonIgnoreProperties(ignoreUnknown = true)
public class PaymentEvent extends BaseEvent {

    public enum Type {
        INITIATED, PROCESSING, COMPLETED, FAILED, REFUNDED, PARTIALLY_REFUNDED, CANCELLED
    }

    private Type type;
    private String paymentId;
    private String paymentReference;
    private String orderId;
    private UUID userId;
    private String userEmail;
    private BigDecimal amount;
    private String currency;
    private String paymentMethod;
    private String failureReason;
}
