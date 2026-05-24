package com.project.product_service.service.impl;

import com.project.common.exception.DuplicateResourceException;
import com.project.common.exception.ForbiddenOperationException;
import com.project.common.exception.ResourceNotFoundException;
import com.project.product_service.dto.ProductRequest;
import com.project.product_service.dto.ProductResponse;
import com.project.product_service.model.Product;
import com.project.product_service.repository.ProductRepository;
import com.project.product_service.search.ProductDocument;
import com.project.product_service.search.ProductSearchRepository;
import com.project.product_service.service.CategoryService;
import com.project.product_service.service.ProductEventPublisher;
import com.project.product_service.service.ProductService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProductServiceImpl implements ProductService {

    private final ProductRepository productRepository;
    private final CategoryService categoryService;
    private final ProductEventPublisher eventPublisher;
    private final ProductSearchRepository productSearchRepository;

    @Override
    @Cacheable(value = "products")
    public Page<ProductResponse> getAllActiveProducts(Pageable pageable) {
        return productRepository.findByActiveTrue(pageable).map(this::mapToResponse);
    }

    @Override
    @Cacheable(value = "products", key = "#id")
    public ProductResponse getProduct(String id) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Product", id));
        if (!product.isActive()) throw new ResourceNotFoundException("Product", id);
        return mapToResponse(product);
    }

    @Override
    @Cacheable(value = "products")
    public Page<ProductResponse> getProductsByCategory(String categoryId, Pageable pageable) {
        return productRepository.findByCategoryIdAndActiveTrue(categoryId, pageable).map(this::mapToResponse);
    }

    @Override
    @Cacheable(value = "products")
    public Page<ProductResponse> searchProducts(String keyword, Pageable pageable) {
        try {
            var docs = productSearchRepository.search(keyword, pageable);
            var total = productSearchRepository.count(keyword);
            var products = docs.stream()
                    .map(doc -> productRepository.findById(doc.getId()))
                    .filter(Optional::isPresent)
                    .map(Optional::get)
                    .map(this::mapToResponse)
                    .toList();
            return new PageImpl<>(products, pageable, total);
        } catch (Exception e) {
            log.warn("Elasticsearch search failed, falling back to MongoDB text search: {}", e.getMessage());
            return productRepository.searchByText(keyword, pageable).map(this::mapToResponse);
        }
    }

    @Override
    public Page<ProductResponse> getProductsBySeller(UUID sellerId, Pageable pageable) {
        return productRepository.findBySellerIdAndActiveTrue(sellerId, pageable).map(this::mapToResponse);
    }

    @Override
    @CacheEvict(value = "products", allEntries = true)
    public ProductResponse createProduct(ProductRequest request) {
        categoryService.getCategory(request.getCategoryId());
        if (productRepository.findBySku(request.getSku()).isPresent()) {
            throw new DuplicateResourceException("SKU already exists: " + request.getSku());
        }

        Product product = new Product();
        product.setSku(request.getSku());
        product.setName(request.getName());
        product.setDescription(request.getDescription());
        product.setCategoryId(request.getCategoryId());
        product.setPrice(request.getPrice());
        product.setStockQuantity(request.getStockQuantity());
        // Image URLs intentionally null for now (CDN pipeline not yet wired).
        product.setImageUrls(null);
        product.setSellerId(request.getSellerId());
        product.setActive(true);

        product = productRepository.save(product);
        syncToElasticsearch(product);
        eventPublisher.publishCreated(product);
        return mapToResponse(product);
    }

    @Override
    @CacheEvict(value = "products", allEntries = true)
    public ProductResponse updateProduct(String id, ProductRequest request, boolean isAdmin) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Product", id));

        if (!isAdmin && !product.getSellerId().equals(request.getSellerId())) {
            throw new ForbiddenOperationException("You do not own this product");
        }

        boolean priceChanged = product.getPrice().compareTo(request.getPrice()) != 0;

        product.setName(request.getName());
        product.setDescription(request.getDescription());
        product.setCategoryId(request.getCategoryId());
        product.setPrice(request.getPrice());
        product.setStockQuantity(request.getStockQuantity());
        product = productRepository.save(product);
        syncToElasticsearch(product);

        eventPublisher.publishUpdated(product);
        if (priceChanged) eventPublisher.publishPriceChanged(product);

        return mapToResponse(product);
    }

    @Override
    @CacheEvict(value = "products", allEntries = true)
    public void deleteProduct(String id, UUID sellerId, boolean isAdmin) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Product", id));
        if (!isAdmin && !product.getSellerId().equals(sellerId)) {
            throw new ForbiddenOperationException("You do not own this product");
        }
        product.setActive(false);
        productRepository.save(product);
        removeFromElasticsearch(id);
        eventPublisher.publishDeleted(product);
    }

    @Override
    @CacheEvict(value = "products", allEntries = true)
    public ProductResponse setProductActiveStatus(String id, boolean active) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Product", id));
        product.setActive(active);
        product = productRepository.save(product);
        if (active) syncToElasticsearch(product); else removeFromElasticsearch(id);
        if (active) eventPublisher.publishActivated(product); else eventPublisher.publishDeactivated(product);
        return mapToResponse(product);
    }

    @Override
    @CacheEvict(value = "products", allEntries = true)
    public ProductResponse updateStock(String id, Integer stockQuantity) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Product", id));
        product.setStockQuantity(stockQuantity);
        product = productRepository.save(product);
        syncToElasticsearch(product);
        eventPublisher.publishStockChanged(product);
        return mapToResponse(product);
    }

    @Override
    @Cacheable(value = "products")
    public Page<ProductResponse> getProductsByPriceRange(BigDecimal min, BigDecimal max, Pageable pageable) {
        return productRepository.findByPriceBetweenAndActiveTrue(min, max, pageable).map(this::mapToResponse);
    }

    private void syncToElasticsearch(Product product) {
        try {
            ProductDocument doc = new ProductDocument();
            doc.setId(product.getId());
            doc.setName(product.getName());
            doc.setDescription(product.getDescription());
            doc.setCategoryId(product.getCategoryId());
            doc.setPrice(product.getPrice());
            doc.setStockQuantity(product.getStockQuantity());
            doc.setImageUrls(product.getImageUrls());
            doc.setSellerId(product.getSellerId());
            doc.setActive(product.isActive());
            productSearchRepository.save(doc);
        } catch (Exception e) {
            log.warn("ES sync failed for product {}: {}", product.getId(), e.getMessage());
        }
    }

    private void removeFromElasticsearch(String id) {
        try {
            productSearchRepository.deleteById(id);
        } catch (Exception e) {
            log.warn("ES delete failed for product {}: {}", id, e.getMessage());
        }
    }

    private ProductResponse mapToResponse(Product product) {
        ProductResponse response = new ProductResponse();
        response.setId(product.getId());
        response.setSku(product.getSku());
        response.setName(product.getName());
        response.setDescription(product.getDescription());
        response.setCategoryId(product.getCategoryId());
        response.setPrice(product.getPrice());
        response.setStockQuantity(product.getStockQuantity());
        response.setImageUrls(product.getImageUrls());
        response.setSellerId(product.getSellerId());
        response.setActive(product.isActive());
        return response;
    }
}
