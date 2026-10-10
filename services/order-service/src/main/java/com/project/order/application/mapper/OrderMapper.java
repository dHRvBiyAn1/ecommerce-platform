package com.project.order.application.mapper;

import com.project.order.generated.model.OrderResponse;
import com.project.order.model.BillingAddress;
import com.project.order.model.Order;
import com.project.order.model.OrderItem;
import com.project.order.model.OrderStatus;
import com.project.order.model.PaymentStatus;
import com.project.order.model.ShippingAddress;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface OrderMapper {
  OrderResponse toResponse(Order order);

  com.project.order.generated.model.OrderItemResponse toResponse(OrderItem item);

  com.project.order.generated.model.ShippingAddressResponse toResponse(ShippingAddress address);

  com.project.order.generated.model.BillingAddressResponse toResponse(BillingAddress address);

  ShippingAddress toEntity(com.project.order.generated.model.ShippingAddressRequest address);

  BillingAddress toEntity(com.project.order.generated.model.BillingAddressRequest address);

  default OrderResponse.StatusEnum toApiStatus(OrderStatus status) {
    return status == null ? null : OrderResponse.StatusEnum.valueOf(status.name());
  }

  default OrderResponse.PaymentStatusEnum toApiPaymentStatus(PaymentStatus status) {
    return status == null ? null : OrderResponse.PaymentStatusEnum.valueOf(status.name());
  }
}
