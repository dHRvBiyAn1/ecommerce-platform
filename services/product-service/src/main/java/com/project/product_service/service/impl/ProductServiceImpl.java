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
        return productRepository
                .findByActiveTrueAndApprovalStatus(
                        com.project.product_service.model.ProductApprovalStatus.APPROVED, pageable)
                .map(this::mapToResponse);
    }

    @Override
    @Cacheable(value = "products", key = "#id")
    public ProductResponse getProduct(String id) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Product", id));
        if (!product.isActive()) throw new ResourceNotFoundException("Product", id);
        // Public reads only see APPROVED. Owner / admin views go through other paths.
        if (product.getApprovalStatus() != null
                && product.getApprovalStatus()
                != com.project.product_service.model.ProductApprovalStatus.APPROVED) {
            throw new ResourceNotFoundException("Product", id);
        }
        return mapToResponse(product);
    }

    @Override
    @Cacheable(value = "products")
    public Page<ProductResponse> getProductsByCategory(String categoryId, Pageable pageable) {
        return productRepository
                .findByCategoryIdAndActiveTrueAndApprovalStatus(
                        categoryId,
                        com.project.product_service.model.ProductApprovalStatus.APPROVED,
                        pageable)
                .map(this::mapToResponse);
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
        // Sellers see ALL their products, regardless of active flag or approval status.
        return productRepository.findBySellerId(sellerId, pageable).map(this::mapToResponse);
    }

    @Override
    @CacheEvict(value = "products", allEntries = true)
    public ProductResponse createProduct(ProductRequest request) {
        return createProduct(request, false);
    }

    @Override
    @CacheEvict(value = "products", allEntries = true)
    public ProductResponse createProduct(ProductRequest request, boolean isAdmin) {
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
        // Admin-created products are auto-approved; seller-created start PENDING.
        product.setApprovalStatus(isAdmin
                ? com.project.product_service.model.ProductApprovalStatus.APPROVED
                : com.project.product_service.model.ProductApprovalStatus.PENDING);
        if (isAdmin) {
            product.setReviewedAt(java.time.LocalDateTime.now());
            product.setReviewedBy(request.getSellerId()); // treat the creator as reviewer
        }

        product = productRepository.save(product);
        // Only push APPROVED to ES so the public catalog stays clean.
        if (product.getApprovalStatus()
                == com.project.product_service.model.ProductApprovalStatus.APPROVED) {
            syncToElasticsearch(product);
        }
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

        // Seller edits to APPROVED listings revert them to PENDING for re-review.
        // Admins can edit without resetting approval.
        if (!isAdmin && product.getApprovalStatus()
                == com.project.product_service.model.ProductApprovalStatus.APPROVED) {
            product.setApprovalStatus(com.project.product_service.model.ProductApprovalStatus.PENDING);
            product.setRejectionReason(null);
            product.setReviewedAt(null);
            product.setReviewedBy(null);
            // Pull from ES while we wait for re-review.
            removeFromElasticsearch(id);
        }

        product = productRepository.save(product);
        if (product.getApprovalStatus()
                == com.project.product_service.model.ProductApprovalStatus.APPROVED) {
            syncToElasticsearch(product);
        }

        eventPublisher.publishUpdated(product);
        if (priceChanged) eventPublisher.publishPriceChanged(product);

        return mapToResponse(product);
    }

    @Override
    @CacheEvict(value = "products", allEntries = true)
    public ProductResponse setApprovalStatus(String id,
                                             com.project.product_service.model.ProductApprovalStatus status,
                                             UUID adminId,
                                             String rejectionReason) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Product", id));
        if (status == com.project.product_service.model.ProductApprovalStatus.PENDING) {
            throw new IllegalArgumentException("Cannot manually set status back to PENDING");
        }
        product.setApprovalStatus(status);
        product.setRejectionReason(
                status == com.project.product_service.model.ProductApprovalStatus.REJECTED
                        ? rejectionReason : null);
        product.setReviewedAt(java.time.LocalDateTime.now());
        product.setReviewedBy(adminId);
        product = productRepository.save(product);
        if (status == com.project.product_service.model.ProductApprovalStatus.APPROVED) {
            syncToElasticsearch(product);
        } else {
            removeFromElasticsearch(id);
        }
        log.info("Product {} {} by admin {}", id, status, adminId);
        return mapToResponse(product);
    }

    @Override
    public Page<ProductResponse> listByApprovalStatus(
            com.project.product_service.model.ProductApprovalStatus status, Pageable pageable) {
        return productRepository.findByApprovalStatus(status, pageable).map(this::mapToResponse);
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
    public ProductResponse setProductActiveStatus(String id, boolean active, UUID sellerId, boolean isAdmin) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Product", id));
        if (!isAdmin && !product.getSellerId().equals(sellerId)) {
            throw new ForbiddenOperationException("You do not own this product");
        }
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
        response.setApprovalStatus(product.getApprovalStatus() == null
                ? null : product.getApprovalStatus().name());
        response.setRejectionReason(product.getRejectionReason());
        return response;
    }
}
