package com.project.product_service.service.impl;

import java.util.List;
import java.util.stream.Collectors;

import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import com.project.product_service.dto.CategoryRequest;
import com.project.product_service.dto.CategoryResponse;
import com.project.product_service.exception.DuplicateResourceException;
import com.project.product_service.exception.ResourceNotFoundException;
import com.project.product_service.model.Category;
import com.project.product_service.repository.CategoryRepository;
import com.project.product_service.service.CategoryService;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class CategoryServiceImpl implements CategoryService {

    private final CategoryRepository categoryRepository;

    @Override
    @Cacheable("categories")
    public List<CategoryResponse> getAllCategories() {
        return categoryRepository.findAll().stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    @Override
    public CategoryResponse getCategory(String id) {
        Category category = categoryRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Category not found with id: " + id));
        return mapToResponse(category);
    }

    @Override
    @CacheEvict(value = "categories", allEntries = true)
    public CategoryResponse createCategory(CategoryRequest request) {
        if (categoryRepository.findByName(request.getName()).isPresent()) {
            throw new DuplicateResourceException("Category with name '" + request.getName() + "' already exists");
        }
        Category category = new Category();
        category.setName(request.getName());
        category.setDescription(request.getDescription());
        category.setParentCategoryId(request.getParentCategoryId());
        category.setImageUrl(request.getImageUrl());
        category = categoryRepository.save(category);
        return mapToResponse(category);
    }

    @Override
    @CacheEvict(value = "categories", allEntries = true)
    public CategoryResponse updateCategory(String id, CategoryRequest request) {
        Category category = categoryRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Category not found with id: " + id));
        category.setName(request.getName());
        category.setDescription(request.getDescription());
        category.setParentCategoryId(request.getParentCategoryId());
        category.setImageUrl(request.getImageUrl());
        category = categoryRepository.save(category);
        return mapToResponse(category);
    }

    @Override
    @CacheEvict(value = "categories", allEntries = true)
    public void deleteCategory(String id) {
        if (!categoryRepository.existsById(id)) {
            throw new ResourceNotFoundException("Category not found with id: " + id);
        }
        categoryRepository.deleteById(id);
    }

    private CategoryResponse mapToResponse(Category category) {
        CategoryResponse response = new CategoryResponse();
        response.setId(category.getId());
        response.setName(category.getName());
        response.setDescription(category.getDescription());
        response.setParentCategoryId(category.getParentCategoryId());
        response.setImageUrl(category.getImageUrl());
        return response;
    }
}
