package com.project.product_service.application.validator;

import com.project.common.exception.ResourceNotFoundException;
import com.project.common.exception.ValidationException;
import com.project.product_service.model.Category;
import com.project.product_service.repository.CategoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class CategoryIntegrityValidator {
    private final CategoryRepository categoryRepository;

    public Category requireActiveCategory(String categoryId) {
        Category category = categoryRepository.findById(categoryId)
                .orElseThrow(() -> new ResourceNotFoundException("Category", categoryId));
        if (!category.isActive()) {
            throw new ValidationException("Category is inactive: " + categoryId);
        }
        return category;
    }

    public void requireActiveParent(String parentCategoryId) {
        if (parentCategoryId != null) {
            requireActiveCategory(parentCategoryId);
        }
    }
}
