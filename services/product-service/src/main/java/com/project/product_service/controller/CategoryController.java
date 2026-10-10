package com.project.product_service.controller;

import com.project.product_service.generated.api.CategoriesApi;
import com.project.product_service.generated.model.CategoryRequest;
import com.project.product_service.generated.model.CategoryResponse;
import com.project.product_service.service.CategoryService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class CategoryController implements CategoriesApi {

  private final CategoryService categoryService;

  @Override
  public ResponseEntity<List<CategoryResponse>> getAllCategories() {
    return ResponseEntity.ok(categoryService.getAllCategories());
  }

  @Override
  public ResponseEntity<CategoryResponse> getCategory(String id) {
    return ResponseEntity.ok(categoryService.getCategory(id));
  }

  @Override
  @PreAuthorize("hasRole('ADMIN')")
  public ResponseEntity<CategoryResponse> createCategory(CategoryRequest categoryRequest) {
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(categoryService.createCategory(categoryRequest));
  }

  @Override
  @PreAuthorize("hasRole('ADMIN')")
  public ResponseEntity<CategoryResponse> updateCategory(
      String id, CategoryRequest categoryRequest) {
    return ResponseEntity.ok(categoryService.updateCategory(id, categoryRequest));
  }

  @Override
  @PreAuthorize("hasRole('ADMIN')")
  public ResponseEntity<Void> deleteCategory(String id) {
    categoryService.deleteCategory(id);
    return ResponseEntity.noContent().build();
  }
}
