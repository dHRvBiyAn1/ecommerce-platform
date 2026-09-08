package com.project.order.application.mapper;

import com.project.order.dto.OrderResponse;
import com.project.order.model.Order;
import com.project.order.model.OrderItem;
import com.project.order.model.ShippingAddress;
import com.project.order.model.BillingAddress;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface OrderMapper {
    OrderResponse toResponse(Order order);

    OrderResponse.OrderItemResponse toResponse(OrderItem item);

    OrderResponse.ShippingAddressResponse toResponse(ShippingAddress address);

    OrderResponse.BillingAddressResponse toResponse(BillingAddress address);
}
