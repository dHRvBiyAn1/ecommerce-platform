package com.project.product_service.service.impl;

import com.project.common.exception.DuplicateResourceException;
import com.project.common.exception.ResourceNotFoundException;
import com.project.product_service.application.mapper.CategoryMapper;
import com.project.product_service.application.validator.CategoryIntegrityValidator;
import com.project.product_service.dto.CategoryRequest;
import com.project.product_service.dto.CategoryResponse;
import com.project.product_service.model.Category;
import com.project.product_service.repository.CategoryRepository;
import com.project.product_service.service.CategoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CategoryServiceImpl implements CategoryService {

    private final CategoryRepository categoryRepository;
    private final CategoryMapper categoryMapper;
    private final CategoryIntegrityValidator categoryIntegrityValidator;

    @Override
    @Cacheable("categories")
    public List<CategoryResponse> getAllCategories() {
        return categoryRepository.findAll().stream().map(categoryMapper::toResponse).collect(Collectors.toList());
    }

    @Override
    public CategoryResponse getCategory(String id) {
        Category category = categoryRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Category", id));
        return categoryMapper.toResponse(category);
    }

    @Override
    @CacheEvict(value = "categories", allEntries = true)
    @Transactional
    public CategoryResponse createCategory(CategoryRequest request) {
        if (categoryRepository.findByName(request.getName()).isPresent()) {
            throw new DuplicateResourceException("Category already exists: " + request.getName());
        }
        categoryIntegrityValidator.requireActiveParent(request.getParentCategoryId());
        Category category = new Category();
        category.setName(request.getName());
        category.setDescription(request.getDescription());
        category.setParentCategoryId(request.getParentCategoryId());
        // imageUrl null per requirements
        category.setImageUrl(request.getImageUrl());
        category = categoryRepository.save(category);
        return categoryMapper.toResponse(category);
    }

    @Override
    @CacheEvict(value = "categories", allEntries = true)
    @Transactional
    public CategoryResponse updateCategory(String id, CategoryRequest request) {
        Category category = categoryRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Category", id));
        categoryIntegrityValidator.requireActiveParent(request.getParentCategoryId());
        category.setName(request.getName());
        category.setDescription(request.getDescription());
        category.setParentCategoryId(request.getParentCategoryId());
        category.setImageUrl(request.getImageUrl());
        category = categoryRepository.save(category);
        return categoryMapper.toResponse(category);
    }

    @Override
    @CacheEvict(value = "categories", allEntries = true)
    @Transactional
    public void deleteCategory(String id) {
        if (!categoryRepository.existsById(id)) {
            throw new ResourceNotFoundException("Category", id);
        }
        categoryRepository.deleteById(id);
    }

}
