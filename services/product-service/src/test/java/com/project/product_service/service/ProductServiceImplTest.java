package com.project.product_service.service;

import com.project.common.exception.ForbiddenOperationException;
import com.project.common.exception.DuplicateResourceException;
import com.project.common.exception.ResourceNotFoundException;
import com.project.common.exception.ValidationException;
import com.project.product_service.application.mapper.CategoryMapper;
import com.project.product_service.application.mapper.ProductMapper;
import com.project.product_service.application.validator.CategoryIntegrityValidator;
import com.project.product_service.application.validator.ProductAccessValidator;
import com.project.product_service.dto.ProductRequest;
import com.project.product_service.dto.CategoryResponse;
import com.project.product_service.dto.ProductResponse;
import com.project.product_service.model.Category;
import com.project.product_service.model.Product;
import com.project.product_service.model.ProductApprovalStatus;
import com.project.product_service.repository.CategoryRepository;
import com.project.product_service.repository.ProductRepository;
import com.project.product_service.search.ProductSearchRepository;
import com.project.product_service.service.impl.ProductServiceImpl;
import com.project.product_service.service.ProductEventPublisher;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ProductServiceImplTest {

    @Test
    void responseBoundariesAreImmutableRecords() {
        assertTrue(ProductResponse.class.isRecord());
        assertTrue(CategoryResponse.class.isRecord());
    }

    @Test
    void productMapperMapsDocumentWithoutReturningTheDocument() {
        Product product = new Product();
        product.setId("product-1");
        product.setSku("SKU-1");
        product.setName("Desk");

        ProductResponse response = Mappers.getMapper(ProductMapper.class).toResponse(product);

        assertEquals("product-1", response.id());
        assertEquals("SKU-1", response.sku());
        assertEquals("Desk", response.name());
    }

    @Test
    void categoryMapperMapsDocumentWithoutReturningTheDocument() {
        Category category = new Category();
        category.setId("category-1");
        category.setName("Furniture");

        CategoryResponse response = Mappers.getMapper(CategoryMapper.class).toResponse(category);

        assertEquals("category-1", response.id());
        assertEquals("Furniture", response.name());
    }

    @Test
    void productAccessValidatorRejectsNonOwnerUnlessActorIsAdmin() {
        ProductAccessValidator validator = new ProductAccessValidator();

        assertThrows(ForbiddenOperationException.class,
                () -> validator.requireSellerOrAdmin(java.util.UUID.randomUUID(), java.util.UUID.randomUUID(), false));
        assertDoesNotThrow(() -> validator.requireSellerOrAdmin(null, null, true));
    }

    @Test
    void productAndCategoryDocumentsExposeMongoAuditFields() throws NoSuchFieldException {
        assertTrue(auditedField(Product.class, "createdAt")
                .isAnnotationPresent(org.springframework.data.annotation.CreatedDate.class));
        assertTrue(auditedField(Product.class, "updatedAt")
                .isAnnotationPresent(org.springframework.data.annotation.LastModifiedDate.class));
        assertTrue(auditedField(Category.class, "createdAt")
                .isAnnotationPresent(org.springframework.data.annotation.CreatedDate.class));
        assertTrue(auditedField(Category.class, "updatedAt")
                .isAnnotationPresent(org.springframework.data.annotation.LastModifiedDate.class));
    }

    @Test
    void categoryIntegrityRejectsInactiveCategoriesWithSharedValidationException() {
        CategoryRepository repository = mock(CategoryRepository.class);
        Category inactive = new Category();
        inactive.setId("category-1");
        inactive.setActive(false);
        when(repository.findById("category-1")).thenReturn(java.util.Optional.of(inactive));

        assertThrows(ValidationException.class,
                () -> new CategoryIntegrityValidator(repository).requireActiveCategory("category-1"));
    }

    @Test
    void productUpdateRejectsASecondSellerBeforePersistence() {
        ProductRepository products = mock(ProductRepository.class);
        Product product = new Product();
        product.setId("product-1");
        product.setSellerId(java.util.UUID.randomUUID());
        when(products.findById("product-1")).thenReturn(java.util.Optional.of(product));

        ProductRequest request = new ProductRequest();
        request.setSellerId(java.util.UUID.randomUUID());
        request.setCategoryId("category-1");
        ProductServiceImpl service = new ProductServiceImpl(
                products,
                mock(ProductEventPublisher.class),
                mock(ProductSearchRepository.class),
                mock(ProductMapper.class),
                new ProductAccessValidator(),
                new CategoryIntegrityValidator(mock(CategoryRepository.class)));

        assertThrows(ForbiddenOperationException.class,
                () -> service.updateProduct("product-1", request, false));
        verify(products, never()).save(product);
    }

    @Test
    void stockUpdateRejectsASecondSellerBeforePersistence() {
        ProductRepository products = mock(ProductRepository.class);
        Product product = new Product();
        product.setId("product-1");
        product.setSellerId(java.util.UUID.randomUUID());
        when(products.findById("product-1")).thenReturn(java.util.Optional.of(product));

        ProductServiceImpl service = service(products);

        assertThrows(ForbiddenOperationException.class,
                () -> service.updateStock("product-1", 12, java.util.UUID.randomUUID(), false));
        verify(products, never()).save(product);
    }

    @Test
    void publicProductReadOnlyReturnsActiveApprovedProducts() {
        ProductRepository products = mock(ProductRepository.class);
        ProductServiceImpl service = serviceWithRealMapper(products, mock(ProductEventPublisher.class), activeCategories());
        Product approved = product("product-1", UUID.randomUUID());
        approved.setActive(true);
        approved.setApprovalStatus(ProductApprovalStatus.APPROVED);
        when(products.findById("product-1")).thenReturn(Optional.of(approved));

        ProductResponse response = service.getProduct("product-1");

        assertEquals("product-1", response.id());
        assertEquals("SKU-1", response.sku());
    }

    @Test
    void publicProductReadHidesInactiveOrUnapprovedProducts() {
        ProductRepository products = mock(ProductRepository.class);
        ProductServiceImpl service = serviceWithRealMapper(products, mock(ProductEventPublisher.class), activeCategories());
        Product inactive = product("inactive", UUID.randomUUID());
        inactive.setActive(false);
        inactive.setApprovalStatus(ProductApprovalStatus.APPROVED);
        Product pending = product("pending", UUID.randomUUID());
        pending.setActive(true);
        pending.setApprovalStatus(ProductApprovalStatus.PENDING);
        when(products.findById("inactive")).thenReturn(Optional.of(inactive));
        when(products.findById("pending")).thenReturn(Optional.of(pending));
        when(products.findById("missing")).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> service.getProduct("inactive"));
        assertThrows(ResourceNotFoundException.class, () -> service.getProduct("pending"));
        assertThrows(ResourceNotFoundException.class, () -> service.getProduct("missing"));
    }

    @Test
    void sellerCreatePersistsPendingProductWithEmptyAttributesAndPublishesCreated() {
        ProductRepository products = savingRepository();
        ProductEventPublisher events = mock(ProductEventPublisher.class);
        ProductServiceImpl service = serviceWithRealMapper(products, events, activeCategories());

        ProductResponse response = service.createProduct(request("SKU-NEW", UUID.randomUUID(), null), false);

        ArgumentCaptor<Product> saved = ArgumentCaptor.forClass(Product.class);
        verify(products).save(saved.capture());
        Product product = saved.getValue();
        assertEquals("SKU-NEW", response.sku());
        assertTrue(product.isActive());
        assertEquals(ProductApprovalStatus.PENDING, product.getApprovalStatus());
        assertEquals(Map.of(), product.getAttributes());
        assertNull(product.getReviewedAt());
        verify(events).publishCreated(product);
    }

    @Test
    void adminCreateAutoApprovesProductAndRejectsDuplicateSkuBeforePersistence() {
        ProductRepository products = savingRepository();
        ProductEventPublisher events = mock(ProductEventPublisher.class);
        ProductServiceImpl service = serviceWithRealMapper(products, events, activeCategories());
        UUID adminId = UUID.randomUUID();

        service.createProduct(request("SKU-ADMIN", adminId, Map.of("color", "black")), true);

        ArgumentCaptor<Product> saved = ArgumentCaptor.forClass(Product.class);
        verify(products).save(saved.capture());
        Product product = saved.getValue();
        assertEquals(ProductApprovalStatus.APPROVED, product.getApprovalStatus());
        assertEquals(adminId, product.getReviewedBy());
        assertNotNull(product.getReviewedAt());
        assertEquals(Map.of("color", "black"), product.getAttributes());
        verify(events).publishCreated(product);

        when(products.findBySku("SKU-ADMIN")).thenReturn(Optional.of(product));
        assertThrows(DuplicateResourceException.class,
                () -> service.createProduct(request("SKU-ADMIN", adminId, null), false));
        verify(products).save(product);
    }

    @Test
    void sellerUpdateApprovedProductResetsReviewAndPublishesPriceChange() {
        ProductRepository products = savingRepository();
        ProductEventPublisher events = mock(ProductEventPublisher.class);
        ProductServiceImpl service = serviceWithRealMapper(products, events, activeCategories());
        UUID sellerId = UUID.randomUUID();
        Product product = product("product-1", sellerId);
        product.setApprovalStatus(ProductApprovalStatus.APPROVED);
        product.setPrice(new BigDecimal("10.00"));
        product.setReviewedBy(UUID.randomUUID());
        product.setReviewedAt(java.time.LocalDateTime.now());
        product.setRejectionReason("old");
        when(products.findById("product-1")).thenReturn(Optional.of(product));

        service.updateProduct("product-1", request("SKU-1", sellerId, Map.of("size", "M")), false);

        assertEquals(ProductApprovalStatus.PENDING, product.getApprovalStatus());
        assertNull(product.getReviewedBy());
        assertNull(product.getReviewedAt());
        assertNull(product.getRejectionReason());
        assertEquals(Map.of("size", "M"), product.getAttributes());
        verify(events).publishUpdated(product);
        verify(events).publishPriceChanged(product);
    }

    @Test
    void adminUpdateKeepsReviewStateWhenPriceAndAttributesAreUnchanged() {
        ProductRepository products = savingRepository();
        ProductEventPublisher events = mock(ProductEventPublisher.class);
        ProductServiceImpl service = serviceWithRealMapper(products, events, activeCategories());
        UUID sellerId = UUID.randomUUID();
        UUID reviewer = UUID.randomUUID();
        Product product = product("product-1", sellerId);
        product.setApprovalStatus(ProductApprovalStatus.APPROVED);
        product.setPrice(new BigDecimal("19.99"));
        product.setAttributes(Map.of("color", "red"));
        product.setReviewedBy(reviewer);
        when(products.findById("product-1")).thenReturn(Optional.of(product));

        service.updateProduct("product-1", request("SKU-1", UUID.randomUUID(), null), true);

        assertEquals(ProductApprovalStatus.APPROVED, product.getApprovalStatus());
        assertEquals(reviewer, product.getReviewedBy());
        assertEquals(Map.of("color", "red"), product.getAttributes());
        verify(events).publishUpdated(product);
        verify(events, never()).publishPriceChanged(product);
    }

    @Test
    void approvalStatusRejectsPendingAndRecordsRejectionReasonOnlyForRejected() {
        ProductRepository products = savingRepository();
        ProductEventPublisher events = mock(ProductEventPublisher.class);
        ProductServiceImpl service = serviceWithRealMapper(products, events, activeCategories());
        UUID adminId = UUID.randomUUID();
        Product product = product("product-1", UUID.randomUUID());
        when(products.findById("product-1")).thenReturn(Optional.of(product));

        assertThrows(IllegalArgumentException.class,
                () -> service.setApprovalStatus("product-1", ProductApprovalStatus.PENDING, adminId, "later"));

        service.setApprovalStatus("product-1", ProductApprovalStatus.REJECTED, adminId, "bad image");
        assertEquals(ProductApprovalStatus.REJECTED, product.getApprovalStatus());
        assertEquals("bad image", product.getRejectionReason());
        assertEquals(adminId, product.getReviewedBy());
        assertNotNull(product.getReviewedAt());
        verify(events).publishUpdated(product);

        service.setApprovalStatus("product-1", ProductApprovalStatus.APPROVED, adminId, "ignored");
        assertNull(product.getRejectionReason());
    }

    @Test
    void deleteStockAndActiveStatusMutationsPersistAndPublishExpectedEvents() {
        ProductRepository products = savingRepository();
        ProductEventPublisher events = mock(ProductEventPublisher.class);
        ProductServiceImpl service = serviceWithRealMapper(products, events, activeCategories());
        UUID sellerId = UUID.randomUUID();
        Product product = product("product-1", sellerId);
        when(products.findById("product-1")).thenReturn(Optional.of(product));

        service.updateStock("product-1", 3, sellerId, false);
        assertEquals(3, product.getStockQuantity());
        verify(events).publishStockChanged(product);

        service.setProductActiveStatus("product-1", false, sellerId, false);
        assertFalse(product.isActive());
        verify(events).publishDeactivated(product);

        service.setProductActiveStatus("product-1", true, sellerId, false);
        assertTrue(product.isActive());
        verify(events).publishActivated(product);

        service.deleteProduct("product-1", sellerId, false);
        assertFalse(product.isActive());
        verify(events).publishDeleted(product);
    }

    @Test
    void productQueriesDelegateToApprovedPublicOrOwnerRepositoryMethods() {
        ProductRepository products = mock(ProductRepository.class);
        ProductServiceImpl service = serviceWithRealMapper(products, mock(ProductEventPublisher.class), activeCategories());
        UUID sellerId = UUID.randomUUID();
        var pageable = PageRequest.of(0, 2);
        var page = new PageImpl<>(List.of(product("product-1", sellerId)), pageable, 1);
        when(products.findByActiveTrueAndApprovalStatus(ProductApprovalStatus.APPROVED, pageable)).thenReturn(page);
        when(products.findByCategoryIdAndActiveTrueAndApprovalStatus("category-1", ProductApprovalStatus.APPROVED, pageable))
                .thenReturn(page);
        when(products.searchByText("desk", pageable)).thenReturn(page);
        when(products.findBySellerId(sellerId, pageable)).thenReturn(page);
        when(products.findByApprovalStatus(ProductApprovalStatus.PENDING, pageable)).thenReturn(page);
        when(products.findByPriceBetweenAndActiveTrue(new BigDecimal("1.00"), new BigDecimal("2.00"), pageable))
                .thenReturn(page);
        when(products.findByAttribute("color", "red", pageable)).thenReturn(page);

        assertEquals(1, service.getAllActiveProducts(pageable).getTotalElements());
        assertEquals(1, service.getProductsByCategory("category-1", pageable).getTotalElements());
        assertEquals(1, service.searchProducts("desk", pageable).getTotalElements());
        assertEquals(1, service.getProductsBySeller(sellerId, pageable).getTotalElements());
        assertEquals(1, service.listByApprovalStatus(ProductApprovalStatus.PENDING, pageable).getTotalElements());
        assertEquals(1, service.getProductsByPriceRange(new BigDecimal("1.00"), new BigDecimal("2.00"), pageable)
                .getTotalElements());
        assertEquals(1, service.filterByAttribute("color", "red", pageable).getTotalElements());
    }

    @Test
    void compatibilityCreateEntryPointDoesNotOpenTransactionAroundPublication() throws NoSuchMethodException {
        assertTrue(ProductServiceImpl.class
                .getMethod("createProduct", ProductRequest.class)
                .getAnnotation(org.springframework.transaction.annotation.Transactional.class) == null);
    }

    @Test
    void productMutationsDoNotPublishKafkaInsideADeclaredTransaction() throws NoSuchMethodException {
        for (var method : new java.lang.reflect.Method[]{
                ProductServiceImpl.class.getMethod("createProduct", ProductRequest.class),
                ProductServiceImpl.class.getMethod("createProduct", ProductRequest.class, boolean.class),
                ProductServiceImpl.class.getMethod("updateProduct", String.class, ProductRequest.class, boolean.class),
                ProductServiceImpl.class.getMethod("setApprovalStatus", String.class,
                        com.project.product_service.model.ProductApprovalStatus.class,
                        java.util.UUID.class, String.class),
                ProductServiceImpl.class.getMethod("deleteProduct", String.class, java.util.UUID.class, boolean.class),
                ProductServiceImpl.class.getMethod("setProductActiveStatus", String.class, boolean.class,
                        java.util.UUID.class, boolean.class),
                ProductServiceImpl.class.getMethod("updateStock", String.class, Integer.class,
                        java.util.UUID.class, boolean.class)
        }) {
            assertTrue(method.getAnnotation(org.springframework.transaction.annotation.Transactional.class) == null,
                    () -> method.getName() + " must not hold a transaction while publishing Kafka");
        }
    }

    private static ProductServiceImpl service(ProductRepository products) {
        return new ProductServiceImpl(
                products,
                mock(ProductEventPublisher.class),
                mock(ProductSearchRepository.class),
                mock(ProductMapper.class),
                new ProductAccessValidator(),
                new CategoryIntegrityValidator(mock(CategoryRepository.class)));
    }

    private static ProductServiceImpl serviceWithRealMapper(
            ProductRepository products, ProductEventPublisher events, CategoryRepository categories) {
        return new ProductServiceImpl(
                products,
                events,
                mock(ProductSearchRepository.class),
                Mappers.getMapper(ProductMapper.class),
                new ProductAccessValidator(),
                new CategoryIntegrityValidator(categories));
    }

    private static ProductRepository savingRepository() {
        ProductRepository products = mock(ProductRepository.class);
        when(products.findBySku(any())).thenReturn(Optional.empty());
        when(products.save(any(Product.class))).thenAnswer(invocation -> {
            Product product = invocation.getArgument(0);
            if (product.getId() == null) product.setId("saved-product");
            return product;
        });
        return products;
    }

    private static CategoryRepository activeCategories() {
        CategoryRepository categories = mock(CategoryRepository.class);
        Category category = new Category();
        category.setId("category-1");
        category.setActive(true);
        when(categories.findById("category-1")).thenReturn(Optional.of(category));
        return categories;
    }

    private static Product product(String id, UUID sellerId) {
        Product product = new Product();
        product.setId(id);
        product.setSku("SKU-1");
        product.setName("Desk");
        product.setDescription("Standing desk");
        product.setCategoryId("category-1");
        product.setPrice(new BigDecimal("19.99"));
        product.setStockQuantity(5);
        product.setImageUrls(List.of("https://example.test/desk.png"));
        product.setSellerId(sellerId);
        product.setAttributes(Map.of());
        product.setActive(true);
        return product;
    }

    private static ProductRequest request(String sku, UUID sellerId, Map<String, Object> attributes) {
        ProductRequest request = new ProductRequest();
        request.setSku(sku);
        request.setName("Desk");
        request.setDescription("Standing desk");
        request.setCategoryId("category-1");
        request.setPrice(new BigDecimal("19.99"));
        request.setStockQuantity(7);
        request.setImageUrls(List.of("https://example.test/desk.png"));
        request.setSellerId(sellerId);
        request.setAttributes(attributes);
        return request;
    }

    private static Field auditedField(Class<?> type, String name) throws NoSuchFieldException {
        return type.getDeclaredField(name);
    }
}
