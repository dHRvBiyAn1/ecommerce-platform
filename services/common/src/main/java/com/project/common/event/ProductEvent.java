package com.project.common.event;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@Builder(builderMethodName = "productEventBuilder")
@JsonIgnoreProperties(ignoreUnknown = true)
public class ProductEvent extends BaseEvent {

  public enum Type {
    CREATED,
    UPDATED,
    DELETED,
    STOCK_CHANGED,
    PRICE_CHANGED,
    ACTIVATED,
    DEACTIVATED,
    VARIANT_ADDED,
    VARIANT_REMOVED
  }

  private Type type;
  private String productId;
  private String sku;
  private String name;
  private UUID sellerId;
}
