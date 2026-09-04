package com.project.inventory.service.impl;

import com.project.common.exception.ValidationException;
import com.project.inventory.application.mapper.InventoryMapper;
import com.project.inventory.application.validator.InventoryValidator;
import com.project.inventory.domain.model.InventoryItem;
import com.project.inventory.domain.model.ReservationStatus;
import com.project.inventory.domain.model.StockReservation;
import com.project.inventory.repository.InventoryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.kafka.core.KafkaTemplate;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InventoryServiceImplTest {

    @Mock private InventoryRepository inventoryRepository;
    @Mock private MongoTemplate mongoTemplate;
    @Mock private RedisTemplate<String, Object> redisTemplate;
    @Mock private KafkaTemplate<String, Object> kafkaTemplate;

    private InventoryServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new InventoryServiceImpl(inventoryRepository, mongoTemplate, redisTemplate, kafkaTemplate,
                new InventoryMapper(), new InventoryValidator());
    }

    @Test
    void repeatedReservationForTheSameOrderAndQuantityIsIdempotent() {
        InventoryItem item = inventory("product-1", 10, 3);
        item.getReservations().put("order-1",
                new StockReservation(3, ReservationStatus.RESERVED, LocalDateTime.now()));
        when(inventoryRepository.findByProductId("product-1")).thenReturn(Optional.of(item));

        var response = service.reserveStock("product-1", 3, "order-1");

        assertThat(response.reservedQuantity()).isEqualTo(3);
        verify(mongoTemplate, never()).findAndModify(
                any(Query.class), any(Update.class), any(FindAndModifyOptions.class), eq(InventoryItem.class));
    }

    @Test
    void commitConvertsReservedUnitsIntoSoldUnits() {
        InventoryItem updated = inventory("product-1", 7, 0);
        updated.getReservations().put("order-1",
                new StockReservation(3, ReservationStatus.COMMITTED, LocalDateTime.now()));
        when(inventoryRepository.findByProductId("product-1"))
                .thenReturn(Optional.of(inventoryWithReservation("product-1", 10, 3, "order-1", 3)));
        when(mongoTemplate.findAndModify(
                any(Query.class), any(Update.class), any(FindAndModifyOptions.class), eq(InventoryItem.class)))
                .thenReturn(updated);

        var response = service.commitStock("product-1", 3, "order-1");

        assertThat(response.quantity()).isEqualTo(7);
        assertThat(response.reservedQuantity()).isZero();
    }

    @Test
    void releaseRejectsAnOrderWithoutAnActiveReservation() {
        when(inventoryRepository.findByProductId("product-1"))
                .thenReturn(Optional.of(inventory("product-1", 10, 3)));

        assertThatThrownBy(() -> service.releaseStock("product-1", 3, "order-1"))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("active reservation");
        verify(mongoTemplate, never()).findAndModify(
                any(Query.class), any(Update.class), any(FindAndModifyOptions.class), eq(InventoryItem.class));
    }

    private InventoryItem inventoryWithReservation(String productId, int quantity, int reserved,
                                                    String orderId, int reservationQuantity) {
        InventoryItem item = inventory(productId, quantity, reserved);
        item.getReservations().put(orderId,
                new StockReservation(reservationQuantity, ReservationStatus.RESERVED, LocalDateTime.now()));
        return item;
    }

    private InventoryItem inventory(String productId, int quantity, int reserved) {
        InventoryItem item = new InventoryItem();
        item.setId("inventory-1");
        item.setProductId(productId);
        item.setSku("SKU-1");
        item.setQuantity(quantity);
        item.setReservedQuantity(reserved);
        item.setReservations(new HashMap<>());
        return item;
    }
}
