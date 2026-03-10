package com.project.product_service.service.impl;

import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import com.project.product_service.dto.ProductRequest;
import com.project.product_service.dto.ProductResponse;
import com.project.product_service.exception.ResourceNotFoundException;
import com.project.product_service.exception.AccessDeniedException;
import com.project.product_service.model.Product;
import com.project.product_service.repository.ProductRepository;
import com.project.product_service.service.CategoryService;
import com.project.product_service.service.ProductService;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ProductServiceImpl implements ProductService {

private final ProductRepository productRepository;
    private final CategoryService categoryService;

    @Override
    public Page<ProductResponse> getAllActiveProducts(Pageable pageable) {
        return productRepository.findByActiveTrue(pageable)
                .map(this::mapToResponse);
    }

    @Override
    public ProductResponse getProduct(String id) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found with id: " + id));
        if (!product.isActive()) {
            throw new ResourceNotFoundException("Product not found"); // hide inactive products from public
        }
        return mapToResponse(product);
    }

    @Override
    public Page<ProductResponse> getProductsByCategory(String categoryId, Pageable pageable) {
        return productRepository.findByCategoryIdAndActiveTrue(categoryId, pageable)
                .map(this::mapToResponse);
    }

    @Override
    public Page<ProductResponse> searchProducts(String keyword, Pageable pageable) {
        return productRepository.searchByText(keyword, pageable)
                .map(this::mapToResponse);
    }

    @Override
    public Page<ProductResponse> getProductsBySeller(UUID sellerId, Pageable pageable) {
        return productRepository.findBySellerIdAndActiveTrue(sellerId, pageable)
                .map(this::mapToResponse);
    }

    @Override
    public ProductResponse createProduct(ProductRequest request) {
        // Validate category exists (optional)
        categoryService.getCategory(request.getCategoryId()); // would throw if not found

        Product product = new Product();
        product.setSku(request.getSku());
        product.setName(request.getName());
        product.setDescription(request.getDescription());
        product.setCategoryId(request.getCategoryId());
        product.setPrice(request.getPrice());
        product.setStockQuantity(request.getStockQuantity());
        product.setImageUrls(request.getImageUrls());
        product.setSellerId(request.getSellerId());
        product.setActive(true); // new products are active by default

        product = productRepository.save(product);
        return mapToResponse(product);
    }

    @Override
    public ProductResponse updateProduct(String id, ProductRequest request) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found with id: " + id));

        // Ensure seller owns this product
        if (!product.getSellerId().equals(request.getSellerId())) {
            throw new AccessDeniedException("You do not have permission to update this product");
        }

        // Update fields
        product.setSku(request.getSku());
        product.setName(request.getName());
        product.setDescription(request.getDescription());
        product.setCategoryId(request.getCategoryId());
        product.setPrice(request.getPrice());
        product.setStockQuantity(request.getStockQuantity());
        product.setImageUrls(request.getImageUrls());
        // sellerId and active status cannot be changed via this method

        product = productRepository.save(product);
        return mapToResponse(product);
    }

    @Override
    public void deleteProduct(String id, UUID sellerId) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found with id: " + id));

        if (!product.getSellerId().equals(sellerId)) {
            throw new AccessDeniedException("You do not have permission to delete this product");
        }

        // Soft delete (set active=false) or hard delete? We'll use soft delete.
        product.setActive(false);
        productRepository.save(product);
    }

    @Override
    public ProductResponse setProductActiveStatus(String id, boolean active) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found with id: " + id));
        product.setActive(active);
        product = productRepository.save(product);
        return mapToResponse(product);
    }

    private ProductResponse mapToResponse(Product product) {
        ProductResponse response = new ProductResponse();
        response.setId(product.getId());
        response.setSku(product.getSku());
        response.setName(product.getName());
        response.setDescription(product.getDescription());
        response.setCategoryId(product.getCategoryId());
        // Optionally fetch category name
        // response.setCategoryName(categoryService.getCategoryName(product.getCategoryId()));
        response.setPrice(product.getPrice());
        response.setStockQuantity(product.getStockQuantity());
        response.setImageUrls(product.getImageUrls());
        response.setSellerId(product.getSellerId());
        response.setActive(product.isActive());
        return response;
    }
}
