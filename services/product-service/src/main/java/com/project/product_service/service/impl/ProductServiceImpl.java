package com.project.product_service.service.impl;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import com.project.product_service.dto.ProductRequest;
import com.project.product_service.dto.ProductResponse;
import com.project.product_service.exception.ResourceNotFoundException;
import com.project.product_service.exception.AccessDeniedException;
import com.project.product_service.model.Product;
import com.project.product_service.repository.ProductRepository;
import com.project.product_service.search.ProductDocument;
import com.project.product_service.search.ProductSearchRepository;
import com.project.product_service.service.CategoryService;
import com.project.product_service.service.ProductEventPublisher;
import com.project.product_service.service.ProductService;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ProductServiceImpl implements ProductService {

    private static final Logger log = LoggerFactory.getLogger(ProductServiceImpl.class);

    private final ProductRepository productRepository;
    private final CategoryService categoryService;
    private final ProductEventPublisher eventPublisher;
    private final ProductSearchRepository productSearchRepository;

    @Override
    @Cacheable(value = "products")
    public Page<ProductResponse> getAllActiveProducts(Pageable pageable) {
        log.debug("Fetching all active products with pageable: {}", pageable);
        return productRepository.findByActiveTrue(pageable)
                .map(this::mapToResponse);
    }

    @Override
    @Cacheable(value = "products", key = "#id")
    public ProductResponse getProduct(String id) {
        log.debug("Fetching product with id: {}", id);
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found with id: " + id));
        if (!product.isActive()) {
            throw new ResourceNotFoundException("Product not found");
        }
        return mapToResponse(product);
    }

    @Override
    @Cacheable(value = "products")
    public Page<ProductResponse> getProductsByCategory(String categoryId, Pageable pageable) {
        log.debug("Fetching products for category: {} with pageable: {}", categoryId, pageable);
        return productRepository.findByCategoryIdAndActiveTrue(categoryId, pageable)
                .map(this::mapToResponse);
    }

    @Override
    @Cacheable(value = "products")
    public Page<ProductResponse> searchProducts(String keyword, Pageable pageable) {
        log.debug("Searching products with keyword: {}", keyword);
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
            return productRepository.searchByText(keyword, pageable)
                    .map(this::mapToResponse);
        }
    }

    @Override
    public Page<ProductResponse> getProductsBySeller(UUID sellerId, Pageable pageable) {
        log.debug("Fetching products for seller: {}", sellerId);
        return productRepository.findBySellerIdAndActiveTrue(sellerId, pageable)
                .map(this::mapToResponse);
    }

    @Override
    @CacheEvict(value = "products", allEntries = true)
    public ProductResponse createProduct(ProductRequest request) {
        log.info("Creating product with SKU: {}", request.getSku());
        categoryService.getCategory(request.getCategoryId());

        Product product = new Product();
        product.setSku(request.getSku());
        product.setName(request.getName());
        product.setDescription(request.getDescription());
        product.setCategoryId(request.getCategoryId());
        product.setPrice(request.getPrice());
        product.setStockQuantity(request.getStockQuantity());
        product.setImageUrls(request.getImageUrls());
        product.setSellerId(request.getSellerId());
        product.setActive(true);

        product = productRepository.save(product);
        syncToElasticsearch(product);
        log.info("Product created with id: {}, SKU: {}", product.getId(), product.getSku());

        eventPublisher.publishEvent(
            ProductEventPublisher.Type.CREATED,
            product.getId(), product.getSku(), product.getSellerId()
        );

        return mapToResponse(product);
    }

    @Override
    @CacheEvict(value = "products", allEntries = true)
    public ProductResponse updateProduct(String id, ProductRequest request) {
        log.info("Updating product with id: {}", id);
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found with id: " + id));

        if (!product.getSellerId().equals(request.getSellerId())) {
            throw new AccessDeniedException("You do not have permission to update this product");
        }

        boolean priceChanged = product.getPrice().compareTo(request.getPrice()) != 0;

        product.setSku(request.getSku());
        product.setName(request.getName());
        product.setDescription(request.getDescription());
        product.setCategoryId(request.getCategoryId());
        product.setPrice(request.getPrice());
        product.setStockQuantity(request.getStockQuantity());
        product.setImageUrls(request.getImageUrls());

        product = productRepository.save(product);
        syncToElasticsearch(product);
        log.info("Product updated with id: {}", product.getId());

        eventPublisher.publishEvent(
            ProductEventPublisher.Type.UPDATED,
            product.getId(), product.getSku(), product.getSellerId()
        );

        if (priceChanged) {
            eventPublisher.publishEvent(
                ProductEventPublisher.Type.PRICE_CHANGED,
                product.getId(), product.getSku(), product.getSellerId()
            );
        }

        return mapToResponse(product);
    }

    @Override
    @CacheEvict(value = "products", allEntries = true)
    public void deleteProduct(String id, UUID sellerId) {
        log.info("Deleting product with id: {}", id);
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found with id: " + id));

        if (!product.getSellerId().equals(sellerId)) {
            throw new AccessDeniedException("You do not have permission to delete this product");
        }

        product.setActive(false);
        productRepository.save(product);
        removeFromElasticsearch(id);
        log.info("Product soft-deleted with id: {}", id);

        eventPublisher.publishEvent(
            ProductEventPublisher.Type.DELETED,
            id, product.getSku(), product.getSellerId()
        );
    }

    @Override
    @CacheEvict(value = "products", allEntries = true)
    public ProductResponse setProductActiveStatus(String id, boolean active) {
        log.info("Setting product {} active status to: {}", id, active);
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found with id: " + id));
        product.setActive(active);
        product = productRepository.save(product);
        if (active) {
            syncToElasticsearch(product);
        } else {
            removeFromElasticsearch(id);
        }

        ProductEventPublisher.Type eventType = active
                ? ProductEventPublisher.Type.ACTIVATED
                : ProductEventPublisher.Type.DEACTIVATED;
        eventPublisher.publishEvent(
            eventType,
            product.getId(), product.getSku(), product.getSellerId()
        );

        return mapToResponse(product);
    }

    @Override
    @CacheEvict(value = "products", allEntries = true)
    public ProductResponse updateStock(String id, Integer stockQuantity) {
        log.info("Updating stock for product: {} to quantity: {}", id, stockQuantity);
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found with id: " + id));

        product.setStockQuantity(stockQuantity);
        product = productRepository.save(product);
        syncToElasticsearch(product);

        eventPublisher.publishEvent(
            ProductEventPublisher.Type.STOCK_CHANGED,
            product.getId(), product.getSku(), product.getSellerId()
        );

        return mapToResponse(product);
    }

    @Override
    @Cacheable(value = "products")
    public Page<ProductResponse> getProductsByPriceRange(BigDecimal min, BigDecimal max, Pageable pageable) {
        log.debug("Fetching products in price range: {} - {}", min, max);
        return productRepository.findByPriceBetweenAndActiveTrue(min, max, pageable)
                .map(this::mapToResponse);
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
            log.warn("Failed to sync product {} to Elasticsearch: {}", product.getId(), e.getMessage());
        }
    }

    private void removeFromElasticsearch(String id) {
        try {
            productSearchRepository.deleteById(id);
        } catch (Exception e) {
            log.warn("Failed to remove product {} from Elasticsearch: {}", id, e.getMessage());
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
