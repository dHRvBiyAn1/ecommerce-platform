package com.project.product_service.application.mapper;

import com.project.product_service.generated.model.CategoryResponse;
import com.project.product_service.model.Category;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface CategoryMapper {
    CategoryResponse toResponse(Category category);
}
