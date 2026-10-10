package com.project.inventory.repository;

import com.project.inventory.domain.model.InventoryItem;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface InventoryRepository extends MongoRepository<InventoryItem, String> {
  Optional<InventoryItem> findByProductId(String productId);

  Optional<InventoryItem> findBySku(String sku);

  List<InventoryItem> findByQuantityLessThan(int threshold);

  Page<InventoryItem> findAll(Pageable pageable);
}
