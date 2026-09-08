package com.project.product_service.application.mapper;

import com.project.product_service.dto.ProductResponse;
import com.project.product_service.model.Product;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface ProductMapper {
    @Mapping(target = "categoryName", ignore = true)
    ProductResponse toResponse(Product product);
}
