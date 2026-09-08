package com.project.cart.application.mapper;

import com.project.cart.dto.CartResponse;
import com.project.cart.model.Cart;
import com.project.cart.model.CartItem;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.math.BigDecimal;

@Mapper(componentModel = "spring")
public interface CartMapper {

    @Mapping(target = "subtotal", source = "subtotal")
    @Mapping(target = "total", source = "total")
    @Mapping(target = "itemCount", source = "itemCount")
    @Mapping(target = "appliedDiscountAmount", source = "discount")
    CartResponse toResponse(Cart cart, BigDecimal subtotal, BigDecimal total,
                            BigDecimal discount, int itemCount);

    CartResponse.Item toItem(CartItem item);
}
