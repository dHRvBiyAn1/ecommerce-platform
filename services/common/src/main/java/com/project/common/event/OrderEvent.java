package com.project.common.event;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@Builder(builderMethodName = "orderEventBuilder")
@JsonIgnoreProperties(ignoreUnknown = true)
public class OrderEvent extends BaseEvent {

    public enum Type {
        CREATED, CONFIRMED, PROCESSING, SHIPPED, DELIVERED,
        CANCELLED, REFUNDED, PAYMENT_PENDING, PAYMENT_COMPLETED, PAYMENT_FAILED
    }

    private Type type;
    private String orderId;
    private String orderNumber;
    private UUID userId;
    private String userEmail;
    private BigDecimal totalAmount;
    private String currency;
    private List<Item> items;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Item {
        private String productId;
        private String sku;
        private int quantity;
        private BigDecimal unitPrice;
        private UUID sellerId;
    }
}
