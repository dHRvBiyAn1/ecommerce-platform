package com.project.product_service.service;

import com.project.product_service.model.Product;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionSynchronizationUtils;

import java.util.concurrent.CompletableFuture;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class ProductEventPublisherTest {

    private final KafkaTemplate<String, Object> kafkaTemplate = mock(KafkaTemplate.class);
    private final ProductEventPublisher publisher = new ProductEventPublisher(kafkaTemplate);

    @AfterEach
    void clearTransactionSynchronization() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
        TransactionSynchronizationManager.setActualTransactionActive(false);
    }

    @Test
    void publishesImmediatelyWhenNoTransactionIsActive() {
        stubSend();

        publisher.publishCreated(product());

        verify(kafkaTemplate).send(anyString(), anyString(), any());
    }

    @Test
    void publishesOnceAfterCommitWhenTransactionSynchronizationIsActive() {
        stubSend();
        beginTransactionSynchronization();

        publisher.publishCreated(product());

        verifyNoInteractions(kafkaTemplate);
        TransactionSynchronizationUtils.invokeAfterCommit(TransactionSynchronizationManager.getSynchronizations());
        verify(kafkaTemplate).send(anyString(), anyString(), any());
    }

    @Test
    void doesNotPublishWhenTransactionRollsBack() {
        stubSend();
        beginTransactionSynchronization();

        publisher.publishCreated(product());

        TransactionSynchronizationUtils.invokeAfterCompletion(
                TransactionSynchronizationManager.getSynchronizations(),
                org.springframework.transaction.support.TransactionSynchronization.STATUS_ROLLED_BACK);

        verifyNoInteractions(kafkaTemplate);
    }

    private void beginTransactionSynchronization() {
        TransactionSynchronizationManager.setActualTransactionActive(true);
        TransactionSynchronizationManager.initSynchronization();
    }

    private void stubSend() {
        when(kafkaTemplate.send(anyString(), anyString(), any()))
                .thenReturn(CompletableFuture.completedFuture((SendResult<String, Object>) null));
    }

    private Product product() {
        Product product = new Product();
        product.setId("product-1");
        product.setSku("SKU-1");
        product.setName("Desk");
        return product;
    }
}
