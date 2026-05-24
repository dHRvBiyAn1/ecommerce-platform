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
@Builder(builderMethodName = "cartEventBuilder")
@JsonIgnoreProperties(ignoreUnknown = true)
public class CartEvent extends BaseEvent {

    public enum Type {
        ITEM_ADDED, ITEM_REMOVED, ITEM_UPDATED, CLEARED,
        ABANDONED, CHECKED_OUT, MERGED
    }

    private Type type;
    private String cartId;
    private UUID userId;
    private String guestSessionId;
    private String productId;
    private int quantity;
    private BigDecimal totalValue;
    private int totalItems;
}
