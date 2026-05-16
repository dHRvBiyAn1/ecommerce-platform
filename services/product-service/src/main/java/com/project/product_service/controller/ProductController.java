package com.project.product_service.controller;

import lombok.RequiredArgsConstructor;

import java.math.BigDecimal;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import com.project.product_service.dto.ProductRequest;
import com.project.product_service.dto.ProductResponse;
import com.project.product_service.dto.StockUpdateRequest;
import com.project.product_service.service.ProductService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1/products")
@RequiredArgsConstructor
public class ProductController {

    private final ProductService productService;

    @GetMapping
    public ResponseEntity<Page<ProductResponse>> getAllActiveProducts(
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return new ResponseEntity<>(productService.getAllActiveProducts(pageable), HttpStatus.OK);
    }

    @GetMapping("/search")
    public ResponseEntity<Page<ProductResponse>> searchProducts(
            @RequestParam String keyword,
            @PageableDefault(size = 20) Pageable pageable) {
        return new ResponseEntity<>(productService.searchProducts(keyword, pageable), HttpStatus.OK);
    }

    @GetMapping("/category/{categoryId}")
    public ResponseEntity<Page<ProductResponse>> getProductsByCategory(
            @PathVariable String categoryId,
            @PageableDefault(size = 20) Pageable pageable) {
        return new ResponseEntity<>(productService.getProductsByCategory(categoryId, pageable), HttpStatus.OK);
    }

    @GetMapping("/{id}")
    public ResponseEntity<ProductResponse> getProduct(@PathVariable String id) {
        return new ResponseEntity<>(productService.getProduct(id), HttpStatus.OK);
    }

    @GetMapping("/seller/{sellerId}")
    public ResponseEntity<Page<ProductResponse>> getProductsBySeller(
            @PathVariable UUID sellerId,
            @PageableDefault(size = 20) Pageable pageable) {
        return new ResponseEntity<>(productService.getProductsBySeller(sellerId, pageable), HttpStatus.OK);
    }

    @GetMapping("/filter")
    public ResponseEntity<Page<ProductResponse>> filterByPrice(
            @RequestParam BigDecimal minPrice,
            @RequestParam BigDecimal maxPrice,
            @PageableDefault(size = 20) Pageable pageable) {
        return new ResponseEntity<>(productService.getProductsByPriceRange(minPrice, maxPrice, pageable), HttpStatus.OK);
    }

    @PatchMapping("/{id}/stock")
    @PreAuthorize("hasRole('SELLER') or hasRole('ADMIN')")
    public ResponseEntity<ProductResponse> updateStock(
            @PathVariable String id,
            @Valid @RequestBody StockUpdateRequest request) {
        return new ResponseEntity<>(productService.updateStock(id, request.stockQuantity()), HttpStatus.OK);
    }

    @GetMapping("/seller")
    @PreAuthorize("hasRole('SELLER')")
    public ResponseEntity<Page<ProductResponse>> getSellerProducts(
            @RequestAttribute("userId") UUID sellerId,
            @PageableDefault(size = 20) Pageable pageable) {
        return new ResponseEntity<>(productService.getProductsBySeller(sellerId, pageable), HttpStatus.OK);
    }

    @PostMapping
    @PreAuthorize("hasRole('SELLER')")
    public ResponseEntity<ProductResponse> createProduct(
            @Valid @RequestBody ProductRequest request,
            @RequestAttribute("userId") UUID sellerId) {
        request.setSellerId(sellerId);
        return new ResponseEntity<>(productService.createProduct(request), HttpStatus.CREATED);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('SELLER')")
    public ResponseEntity<ProductResponse> updateProduct(
            @PathVariable String id,
            @Valid @RequestBody ProductRequest request,
            @RequestAttribute("userId") UUID sellerId) {
        request.setSellerId(sellerId);
        return new ResponseEntity<>(productService.updateProduct(id, request), HttpStatus.OK);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('SELLER')")
    public ResponseEntity<Void> deleteProduct(
            @PathVariable String id,
            @RequestAttribute("userId") UUID sellerId) {
        productService.deleteProduct(id, sellerId);
        return new ResponseEntity<>(HttpStatus.NO_CONTENT);
    }

    @PutMapping("/admin/{id}/toggle")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ProductResponse> toggleProductActive(@PathVariable String id, @RequestParam boolean active) {
        return new ResponseEntity<>(productService.setProductActiveStatus(id, active), HttpStatus.OK);
    }
}
