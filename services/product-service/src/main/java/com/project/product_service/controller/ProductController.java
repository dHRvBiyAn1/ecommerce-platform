package com.project.product_service.controller;

import com.project.common.constant.Permissions;
import com.project.common.security.CurrentUser;
import com.project.product_service.dto.ProductRequest;
import com.project.product_service.dto.ProductResponse;
import com.project.product_service.dto.StockUpdateRequest;
import com.project.product_service.service.ProductService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/products")
@RequiredArgsConstructor
public class ProductController {

    private final ProductService productService;

    // ---- Public reads ----

    @GetMapping
    public ResponseEntity<Page<ProductResponse>> getAllActiveProducts(
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return ResponseEntity.ok(productService.getAllActiveProducts(pageable));
    }

    @GetMapping("/search")
    public ResponseEntity<Page<ProductResponse>> searchProducts(
            @RequestParam String keyword,
            @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(productService.searchProducts(keyword, pageable));
    }

    @GetMapping("/category/{categoryId}")
    public ResponseEntity<Page<ProductResponse>> getProductsByCategory(
            @PathVariable String categoryId,
            @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(productService.getProductsByCategory(categoryId, pageable));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ProductResponse> getProduct(@PathVariable String id) {
        return ResponseEntity.ok(productService.getProduct(id));
    }

    @GetMapping("/seller/{sellerId}")
    public ResponseEntity<Page<ProductResponse>> getProductsBySeller(
            @PathVariable UUID sellerId,
            @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(productService.getProductsBySeller(sellerId, pageable));
    }

    @GetMapping("/filter")
    public ResponseEntity<Page<ProductResponse>> filterByPrice(
            @RequestParam BigDecimal minPrice,
            @RequestParam BigDecimal maxPrice,
            @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(productService.getProductsByPriceRange(minPrice, maxPrice, pageable));
    }

    // ---- Authenticated seller endpoints ----

    @GetMapping("/seller")
    @PreAuthorize("hasRole('SELLER') or hasRole('ADMIN')")
    public ResponseEntity<Page<ProductResponse>> getMyProducts(
            @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(productService.getProductsBySeller(CurrentUser.requireId(), pageable));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('" + Permissions.PRODUCTS_CREATE + "')")
    public ResponseEntity<ProductResponse> createProduct(@Valid @RequestBody ProductRequest request) {
        UUID sellerId = CurrentUser.requireId();
        request.setSellerId(sellerId);
        return new ResponseEntity<>(
                productService.createProduct(request, CurrentUser.isAdmin()),
                HttpStatus.CREATED);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('" + Permissions.PRODUCTS_UPDATE + "')")
    public ResponseEntity<ProductResponse> updateProduct(
            @PathVariable String id,
            @Valid @RequestBody ProductRequest request) {
        UUID sellerId = CurrentUser.requireId();
        request.setSellerId(sellerId);
        return ResponseEntity.ok(productService.updateProduct(id, request, CurrentUser.isAdmin()));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('" + Permissions.PRODUCTS_DELETE + "')")
    public ResponseEntity<Void> deleteProduct(@PathVariable String id) {
        productService.deleteProduct(id, CurrentUser.requireId(), CurrentUser.isAdmin());
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/{id}/stock")
    @PreAuthorize("hasAuthority('" + Permissions.PRODUCTS_UPDATE + "')")
    public ResponseEntity<ProductResponse> updateStock(
            @PathVariable String id,
            @Valid @RequestBody StockUpdateRequest request) {
        return ResponseEntity.ok(productService.updateStock(id, request.stockQuantity()));
    }

    @PutMapping("/{id}/active")
    @PreAuthorize("hasAuthority('" + Permissions.PRODUCTS_UPDATE + "')")
    public ResponseEntity<ProductResponse> toggleProductActive(@PathVariable String id, @RequestParam boolean active) {
        return ResponseEntity.ok(productService.setProductActiveStatus(
                id, active, CurrentUser.requireId(), CurrentUser.isAdmin()));
    }

    /** Admin moderation: approve a pending product. */
    @PutMapping("/{id}/approve")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ProductResponse> approveProduct(@PathVariable String id) {
        return ResponseEntity.ok(productService.setApprovalStatus(
                id, com.project.product_service.model.ProductApprovalStatus.APPROVED,
                CurrentUser.requireId(), null));
    }

    /** Admin moderation: reject a pending product with a reason. */
    @PutMapping("/{id}/reject")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ProductResponse> rejectProduct(
            @PathVariable String id,
            @RequestParam String reason) {
        return ResponseEntity.ok(productService.setApprovalStatus(
                id, com.project.product_service.model.ProductApprovalStatus.REJECTED,
                CurrentUser.requireId(), reason));
    }

    /** Admin moderation list — paged products filtered by approval status. */
    @GetMapping("/admin/by-status")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Page<ProductResponse>> listByApprovalStatus(
            @RequestParam com.project.product_service.model.ProductApprovalStatus status,
            @PageableDefault(size = 50) Pageable pageable) {
        return ResponseEntity.ok(productService.listByApprovalStatus(status, pageable));
    }
}
