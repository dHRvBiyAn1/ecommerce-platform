package com.project.product_service.service.impl;

import com.project.common.exception.DuplicateResourceException;
import com.project.common.exception.ResourceNotFoundException;
import com.project.product_service.application.mapper.ProductMapper;
import com.project.product_service.application.validator.CategoryIntegrityValidator;
import com.project.product_service.application.validator.ProductAccessValidator;
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
import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProductServiceImpl implements ProductService {

    private final ProductRepository productRepository;
    private final ProductEventPublisher eventPublisher;
    private final ProductSearchRepository productSearchRepository;
    private final ProductMapper productMapper;
    private final ProductAccessValidator productAccessValidator;
    private final CategoryIntegrityValidator categoryIntegrityValidator;

    @Override
    public Page<ProductResponse> getAllActiveProducts(Pageable pageable) {
        return productRepository
                .findByActiveTrueAndApprovalStatus(
                        com.project.product_service.model.ProductApprovalStatus.APPROVED, pageable)
                .map(productMapper::toResponse);
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
        return productMapper.toResponse(product);
    }

    @Override
    public Page<ProductResponse> getProductsByCategory(String categoryId, Pageable pageable) {
        return productRepository
                .findByCategoryIdAndActiveTrueAndApprovalStatus(
                        categoryId,
                        com.project.product_service.model.ProductApprovalStatus.APPROVED,
                        pageable)
                .map(productMapper::toResponse);
    }

    @Override
    public Page<ProductResponse> searchProducts(String keyword, Pageable pageable) {
        try {
            var docs = productSearchRepository.search(keyword, pageable);
            var total = productSearchRepository.count(keyword);
            var products = docs.stream()
                    .map(doc -> productRepository.findById(doc.getId()))
                    .filter(Optional::isPresent)
                    .map(Optional::get)
                    .map(productMapper::toResponse)
                    .toList();
            return new PageImpl<>(products, pageable, total);
        } catch (Exception e) {
            log.warn("Elasticsearch search failed, falling back to MongoDB text search: {}", e.getMessage());
            return productRepository.searchByText(keyword, pageable).map(productMapper::toResponse);
        }
    }

    @Override
    public Page<ProductResponse> getProductsBySeller(UUID sellerId, Pageable pageable) {
        // Sellers see ALL their products, regardless of active flag or approval status.
        return productRepository.findBySellerId(sellerId, pageable).map(productMapper::toResponse);
    }

    @Override
    @CacheEvict(value = "products", allEntries = true)
    public ProductResponse createProduct(ProductRequest request) {
        return createProduct(request, false);
    }

    @Override
    @CacheEvict(value = "products", allEntries = true)
    public ProductResponse createProduct(ProductRequest request, boolean isAdmin) {
        categoryIntegrityValidator.requireActiveCategory(request.getCategoryId());
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
        product.setImageUrls(request.getImageUrls());
        product.setSellerId(request.getSellerId());
        product.setAttributes(request.getAttributes() != null
                ? request.getAttributes() : Collections.emptyMap());
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
        eventPublisher.publishCreated(product);
        return productMapper.toResponse(product);
    }

    @Override
    @CacheEvict(value = "products", allEntries = true)
    public ProductResponse updateProduct(String id, ProductRequest request, boolean isAdmin) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Product", id));

        productAccessValidator.requireSellerOrAdmin(product.getSellerId(), request.getSellerId(), isAdmin);
        categoryIntegrityValidator.requireActiveCategory(request.getCategoryId());

        boolean priceChanged = product.getPrice().compareTo(request.getPrice()) != 0;

        product.setName(request.getName());
        product.setDescription(request.getDescription());
        product.setCategoryId(request.getCategoryId());
        product.setPrice(request.getPrice());
        product.setStockQuantity(request.getStockQuantity());
        if (request.getAttributes() != null) {
            product.setAttributes(request.getAttributes());
        }

        // Seller edits to APPROVED listings revert them to PENDING for re-review.
        // Admins can edit without resetting approval.
        if (!isAdmin && product.getApprovalStatus()
                == com.project.product_service.model.ProductApprovalStatus.APPROVED) {
            product.setApprovalStatus(com.project.product_service.model.ProductApprovalStatus.PENDING);
            product.setRejectionReason(null);
            product.setReviewedAt(null);
            product.setReviewedBy(null);
        }

        product = productRepository.save(product);
        eventPublisher.publishUpdated(product);
        if (priceChanged) eventPublisher.publishPriceChanged(product);

        return productMapper.toResponse(product);
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
        log.info("Product {} {} by admin {}", id, status, adminId);
        return productMapper.toResponse(product);
    }

    @Override
    public Page<ProductResponse> listByApprovalStatus(
            com.project.product_service.model.ProductApprovalStatus status, Pageable pageable) {
        return productRepository.findByApprovalStatus(status, pageable).map(productMapper::toResponse);
    }

    @Override
    @CacheEvict(value = "products", allEntries = true)
    public void deleteProduct(String id, UUID sellerId, boolean isAdmin) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Product", id));
        productAccessValidator.requireSellerOrAdmin(product.getSellerId(), sellerId, isAdmin);
        product.setActive(false);
        productRepository.save(product);
        eventPublisher.publishDeleted(product);
    }

    @Override
    @CacheEvict(value = "products", allEntries = true)
    public ProductResponse setProductActiveStatus(String id, boolean active, UUID sellerId, boolean isAdmin) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Product", id));
        productAccessValidator.requireSellerOrAdmin(product.getSellerId(), sellerId, isAdmin);
        product.setActive(active);
        product = productRepository.save(product);
        if (active) eventPublisher.publishActivated(product); else eventPublisher.publishDeactivated(product);
        return productMapper.toResponse(product);
    }

    @Override
    @CacheEvict(value = "products", allEntries = true)
    public ProductResponse updateStock(String id, Integer stockQuantity, UUID sellerId, boolean isAdmin) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Product", id));
        productAccessValidator.requireSellerOrAdmin(product.getSellerId(), sellerId, isAdmin);
        product.setStockQuantity(stockQuantity);
        product = productRepository.save(product);
        eventPublisher.publishStockChanged(product);
        return productMapper.toResponse(product);
    }

    @Override
    public Page<ProductResponse> getProductsByPriceRange(BigDecimal min, BigDecimal max, Pageable pageable) {
        return productRepository.findByPriceBetweenAndActiveTrue(min, max, pageable).map(productMapper::toResponse);
    }

    @Override
    public Page<ProductResponse> filterByAttribute(String key, Object value, Pageable pageable) {
        return productRepository.findByAttribute(key, value, pageable).map(productMapper::toResponse);
    }
}
