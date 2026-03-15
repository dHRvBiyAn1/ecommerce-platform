package com.project.product_service.repository;

import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;

import com.project.product_service.model.Product;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.util.UUID;

public interface ProductRepository extends MongoRepository<Product, String> {

    Page<Product> findByActiveTrue(Pageable pageable);

    Page<Product> findByCategoryIdAndActiveTrue(String categoryId, Pageable pageable);

    Page<Product> findBySellerIdAndActiveTrue(UUID sellerId, Pageable pageable);

    // Text search
    @Query("{ $text: { $search: ?0 }, active: true }")
    Page<Product> searchByText(String keyword, Pageable pageable);

    // Filter by price range
    Page<Product> findByPriceBetweenAndActiveTrue(BigDecimal min, BigDecimal max, Pageable pageable);

    // You can combine multiple criteria using @Query or custom implementation.
}
