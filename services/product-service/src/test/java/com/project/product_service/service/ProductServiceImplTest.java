package com.project.product_service.service;

import com.project.common.exception.ForbiddenOperationException;
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
import com.project.product_service.repository.CategoryRepository;
import com.project.product_service.repository.ProductRepository;
import com.project.product_service.search.ProductSearchRepository;
import com.project.product_service.service.impl.ProductServiceImpl;
import com.project.product_service.service.ProductEventPublisher;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;

import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;
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

    private static Field auditedField(Class<?> type, String name) throws NoSuchFieldException {
        return type.getDeclaredField(name);
    }
}
