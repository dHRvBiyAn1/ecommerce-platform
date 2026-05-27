package com.project.product_service.service;

import com.project.product_service.dto.ProductRequest;
import com.project.product_service.dto.ProductResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.util.UUID;

public interface ProductService {

    Page<ProductResponse> getAllActiveProducts(Pageable pageable);

    Page<ProductResponse> searchProducts(String keyword, Pageable pageable);

    Page<ProductResponse> getProductsByCategory(String categoryId, Pageable pageable);

    ProductResponse getProduct(String id);

    Page<ProductResponse> getProductsBySeller(UUID sellerId, Pageable pageable);

    ProductResponse createProduct(ProductRequest request);

    /** Used when an admin creates a product directly so it auto-approves. */
    ProductResponse createProduct(ProductRequest request, boolean isAdmin);

    ProductResponse updateProduct(String id, ProductRequest request, boolean isAdmin);

    /** Admin moderation: APPROVE or REJECT a pending product. */
    ProductResponse setApprovalStatus(
            String id,
            com.project.product_service.model.ProductApprovalStatus status,
            UUID adminId,
            String rejectionReason);

    /** Admin moderation list — by approval status. */
    Page<ProductResponse> listByApprovalStatus(
            com.project.product_service.model.ProductApprovalStatus status,
            Pageable pageable);

    void deleteProduct(String id, UUID sellerId, boolean isAdmin);

    ProductResponse setProductActiveStatus(String id, boolean active, UUID sellerId, boolean isAdmin);

    ProductResponse updateStock(String id, Integer stockQuantity);

    Page<ProductResponse> getProductsByPriceRange(BigDecimal min, BigDecimal max, Pageable pageable);
}
