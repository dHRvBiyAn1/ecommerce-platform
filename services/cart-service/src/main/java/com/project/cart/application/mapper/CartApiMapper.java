package com.project.cart.application.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring", implementationPackage = "com.project.cart.generated.mapper")
public interface CartApiMapper {

    com.project.cart.dto.AddCartItemRequest toDomain(
            com.project.cart.generated.model.AddCartItemRequest request);

    @Mapping(target = "quantity", expression = "java(request.getQuantity() == null ? 0 : request.getQuantity())")
    com.project.cart.dto.UpdateQuantityRequest toDomain(
            com.project.cart.generated.model.UpdateQuantityRequest request);

    com.project.cart.dto.ApplyCouponRequest toDomain(
            com.project.cart.generated.model.ApplyCouponRequest request);

    com.project.cart.generated.model.CartResponse toApi(com.project.cart.dto.CartResponse response);

    com.project.cart.generated.model.CartItem toApi(com.project.cart.dto.CartResponse.Item item);
}
