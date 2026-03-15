package com.project.product_service.service;

import java.util.List;

import com.project.product_service.dto.CategoryRequest;
import com.project.product_service.dto.CategoryResponse;

public interface CategoryService {
    List<CategoryResponse> getAllCategories();

    CategoryResponse getCategory(String id);

    CategoryResponse createCategory(CategoryRequest request);

    CategoryResponse updateCategory(String id, CategoryRequest request);

    void deleteCategory(String id);
}
