package com.project.product_service.kafka;

import com.project.common.event.ProductEvent;
import com.project.product_service.model.Product;
import com.project.product_service.model.ProductApprovalStatus;
import com.project.product_service.repository.ProductRepository;
import com.project.product_service.search.ProductSearchRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ProductSearchConsumerTest {

    @Test
    void approvalChangeRemovesTheExistingSearchDocumentWhenTheProductIsNoLongerPublic() {
        ProductRepository products = mock(ProductRepository.class);
        ProductSearchRepository search = mock(ProductSearchRepository.class);
        Product pending = new Product();
        pending.setId("product-1");
        pending.setActive(true);
        pending.setApprovalStatus(ProductApprovalStatus.PENDING);
        when(products.findById("product-1")).thenReturn(Optional.of(pending));

        new ProductSearchConsumer(search, products).indexProductEvent(event(ProductEvent.Type.UPDATED));

        verify(search).deleteById("product-1");
    }

    @Test
    void stockChangeReplacesTheSearchDocumentWithTheCurrentPublicStock() {
        ProductRepository products = mock(ProductRepository.class);
        ProductSearchRepository search = mock(ProductSearchRepository.class);
        Product product = new Product();
        product.setId("product-1");
        product.setName("Desk");
        product.setActive(true);
        product.setApprovalStatus(ProductApprovalStatus.APPROVED);
        product.setStockQuantity(3);
        when(products.findById("product-1")).thenReturn(Optional.of(product));

        new ProductSearchConsumer(search, products).indexProductEvent(event(ProductEvent.Type.STOCK_CHANGED));

        ArgumentCaptor<com.project.product_service.search.ProductDocument> document =
                ArgumentCaptor.forClass(com.project.product_service.search.ProductDocument.class);
        verify(search).save(document.capture());
        assertEquals(3, document.getValue().getStockQuantity());
    }

    private static ProductEvent event(ProductEvent.Type type) {
        return ProductEvent.productEventBuilder().type(type).productId("product-1").build();
    }
}
