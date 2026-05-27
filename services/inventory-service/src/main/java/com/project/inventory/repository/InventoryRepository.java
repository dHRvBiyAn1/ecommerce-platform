package com.project.inventory.repository;

import com.project.inventory.model.InventoryItem;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;

public interface InventoryRepository extends MongoRepository<InventoryItem, String> {
    Optional<InventoryItem> findByProductId(String productId);

    Optional<InventoryItem> findBySku(String sku);

    List<InventoryItem> findByQuantityLessThan(int threshold);

    Page<InventoryItem> findAll(Pageable pageable);
}
