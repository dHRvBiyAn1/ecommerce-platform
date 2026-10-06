package com.project.order.application.mapper;

import com.project.order.dto.BillingAddressRequest;
import com.project.order.dto.OrderItemRequest;
import com.project.order.dto.OrderRequest;
import com.project.order.dto.OrderResponse;
import com.project.order.dto.OrderStatusUpdateRequest;
import com.project.order.dto.ShippingAddressRequest;
import com.project.order.generated.model.ApiResponsePageOrderResponseData;
import org.mapstruct.Mapper;
import org.springframework.data.domain.Page;

@Mapper(componentModel = "spring", implementationPackage = "com.project.order.generated.mapper")
public interface OrderApiMapper {
    OrderRequest toDomain(com.project.order.generated.model.OrderRequest request);

    @org.mapstruct.Mapping(target = "quantity", expression = "java(request.getQuantity() == null ? 0 : request.getQuantity())")
    OrderItemRequest toDomain(com.project.order.generated.model.OrderItemRequest request);

    ShippingAddressRequest toDomain(com.project.order.generated.model.ShippingAddressRequest request);

    BillingAddressRequest toDomain(com.project.order.generated.model.BillingAddressRequest request);

    OrderStatusUpdateRequest toDomain(com.project.order.generated.model.OrderStatusUpdateRequest request);

    com.project.order.generated.model.OrderResponse toApi(OrderResponse response);

    com.project.order.generated.model.OrderItemResponse toApi(OrderResponse.OrderItemResponse item);

    com.project.order.generated.model.ShippingAddressResponse toApi(OrderResponse.ShippingAddressResponse address);

    com.project.order.generated.model.BillingAddressResponse toApi(OrderResponse.BillingAddressResponse address);

    ApiResponsePageOrderResponseData toApi(Page<OrderResponse> page);
}
