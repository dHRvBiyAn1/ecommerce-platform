package com.project.product_service.repository;

import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;

import com.project.product_service.model.Product;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

public interface ProductRepository extends MongoRepository<Product, String> {

    Page<Product> findByActiveTrue(Pageable pageable);

    Page<Product> findByActiveTrueAndApprovalStatus(
            com.project.product_service.model.ProductApprovalStatus status, Pageable pageable);

    Page<Product> findByCategoryIdAndActiveTrue(String categoryId, Pageable pageable);

    Page<Product> findByCategoryIdAndActiveTrueAndApprovalStatus(
            String categoryId,
            com.project.product_service.model.ProductApprovalStatus status,
            Pageable pageable);

    Page<Product> findBySellerIdAndActiveTrue(UUID sellerId, Pageable pageable);

    /** Sellers see their own products regardless of active flag or approval status. */
    Page<Product> findBySellerId(UUID sellerId, Pageable pageable);

    Page<Product> findByApprovalStatus(
            com.project.product_service.model.ProductApprovalStatus status, Pageable pageable);

    // Text search — only APPROVED + active are public.
    @Query("{ $text: { $search: ?0 }, active: true, approvalStatus: 'APPROVED' }")
    Page<Product> searchByText(String keyword, Pageable pageable);

    // Filter by price range — public listing, only APPROVED.
    @Query("{ price: { $gte: ?0, $lte: ?1 }, active: true, approvalStatus: 'APPROVED' }")
    Page<Product> findByPriceBetweenAndActiveTrue(BigDecimal min, BigDecimal max, Pageable pageable);

    Optional<Product> findBySku(String sku);

    // You can combine multiple criteria using @Query or custom implementation.
}
