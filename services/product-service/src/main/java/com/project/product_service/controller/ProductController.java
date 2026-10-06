package com.project.product_service.controller;

import com.project.common.constant.Permissions;
import com.project.common.security.CurrentUser;
import com.project.product_service.application.mapper.ProductApiMapper;
import com.project.product_service.generated.api.ProductsApi;
import com.project.product_service.generated.model.PageProductResponse;
import com.project.product_service.generated.model.ProductRequest;
import com.project.product_service.generated.model.ProductResponse;
import com.project.product_service.generated.model.StockUpdateRequest;
import com.project.product_service.service.ProductService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
public class ProductController implements ProductsApi {

    private final ProductService productService;
    private final ProductApiMapper apiMapper;

    @Override
    public ResponseEntity<PageProductResponse> getAllActiveProducts(
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return ResponseEntity.ok(apiMapper.toApi(productService.getAllActiveProducts(
                pageable)));
    }

    @Override
    public ResponseEntity<PageProductResponse> searchProducts(String keyword,
            @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(apiMapper.toApi(productService.searchProducts(
                keyword, pageable)));
    }

    @Override
    public ResponseEntity<PageProductResponse> getProductsByCategory(
            String categoryId, @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(apiMapper.toApi(productService.getProductsByCategory(
                categoryId, pageable)));
    }

    @Override
    public ResponseEntity<ProductResponse> getProduct(String id) {
        return ResponseEntity.ok(apiMapper.toApi(productService.getProduct(id)));
    }

    @Override
    @PreAuthorize("hasRole('ADMIN') or #sellerId.toString() == authentication.name")
    public ResponseEntity<PageProductResponse> getProductsBySeller(
            UUID sellerId, @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(apiMapper.toApi(productService.getProductsBySeller(
                sellerId, pageable)));
    }

    @Override
    public ResponseEntity<PageProductResponse> filterByPrice(
            BigDecimal minPrice, BigDecimal maxPrice, @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(apiMapper.toApi(productService.getProductsByPriceRange(
                minPrice, maxPrice, pageable)));
    }

    @Override
    public ResponseEntity<PageProductResponse> filterByAttribute(
            String key, String value, @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(apiMapper.toApi(productService.filterByAttribute(
                key, value, pageable)));
    }

    @Override
    @PreAuthorize("hasRole('SELLER') or hasRole('ADMIN')")
    public ResponseEntity<PageProductResponse> getMyProducts(@PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(apiMapper.toApi(productService.getProductsBySeller(
                CurrentUser.requireId(), pageable)));
    }

    @Override
    @PreAuthorize("hasAuthority('" + Permissions.PRODUCTS_CREATE + "')")
    public ResponseEntity<ProductResponse> createProduct(ProductRequest productRequest) {
        com.project.product_service.dto.ProductRequest request = apiMapper.toDomain(productRequest);
        request.setSellerId(CurrentUser.requireId());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(apiMapper.toApi(productService.createProduct(request, CurrentUser.isAdmin())));
    }

    @Override
    @PreAuthorize("hasAuthority('" + Permissions.PRODUCTS_UPDATE + "')")
    public ResponseEntity<ProductResponse> updateProduct(String id, ProductRequest productRequest) {
        com.project.product_service.dto.ProductRequest request = apiMapper.toDomain(productRequest);
        request.setSellerId(CurrentUser.requireId());
        return ResponseEntity.ok(apiMapper.toApi(productService.updateProduct(id, request, CurrentUser.isAdmin())));
    }

    @Override
    @PreAuthorize("hasAuthority('" + Permissions.PRODUCTS_DELETE + "')")
    public ResponseEntity<Void> deleteProduct(String id) {
        productService.deleteProduct(id, CurrentUser.requireId(), CurrentUser.isAdmin());
        return ResponseEntity.noContent().build();
    }

    @Override
    @PreAuthorize("hasAuthority('" + Permissions.PRODUCTS_UPDATE + "')")
    public ResponseEntity<ProductResponse> updateStock(String id, StockUpdateRequest stockUpdateRequest) {
        return ResponseEntity.ok(apiMapper.toApi(productService.updateStock(
                id, stockUpdateRequest.getStockQuantity(), CurrentUser.requireId(), CurrentUser.isAdmin())));
    }

    @Override
    @PreAuthorize("hasAuthority('" + Permissions.PRODUCTS_UPDATE + "')")
    public ResponseEntity<ProductResponse> toggleProductActive(String id, Boolean active) {
        return ResponseEntity.ok(apiMapper.toApi(productService.setProductActiveStatus(
                id, active, CurrentUser.requireId(), CurrentUser.isAdmin())));
    }

    @Override
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ProductResponse> approveProduct(String id) {
        return ResponseEntity.ok(apiMapper.toApi(productService.setApprovalStatus(
                id, com.project.product_service.model.ProductApprovalStatus.APPROVED, CurrentUser.requireId(), null)));
    }

    @Override
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ProductResponse> rejectProduct(String id, String reason) {
        return ResponseEntity.ok(apiMapper.toApi(productService.setApprovalStatus(
                id, com.project.product_service.model.ProductApprovalStatus.REJECTED,
                CurrentUser.requireId(), reason)));
    }

    @Override
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<PageProductResponse> listByApprovalStatus(
            com.project.product_service.generated.model.ProductApprovalStatus status,
            @PageableDefault(size = 50) Pageable pageable) {
        com.project.product_service.model.ProductApprovalStatus approvalStatus =
                com.project.product_service.model.ProductApprovalStatus.valueOf(status.getValue());
        return ResponseEntity.ok(apiMapper.toApi(productService.listByApprovalStatus(
                approvalStatus, pageable)));
    }

    @Override
    public ResponseEntity<PageProductResponse> getProductsByCategory1(
            String id, @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC)
                    Pageable pageable) {
        return ResponseEntity.ok(apiMapper.toApi(productService.getProductsByCategory(
                id, pageable)));
    }
}
