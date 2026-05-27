package com.project.inventory.service;

import com.project.inventory.dto.InventoryRequest;
import com.project.inventory.dto.InventoryResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;

public interface InventoryService {
    Page<InventoryResponse> getAllInventory(Pageable pageable);

    InventoryResponse getByProductId(String productId);

    InventoryResponse getBySku(String sku);

    InventoryResponse createInventory(InventoryRequest request);

    InventoryResponse updateInventory(String id, InventoryRequest request);

    void deleteInventory(String id);

    InventoryResponse reserveStock(String productId, int quantity, String orderId);

    InventoryResponse releaseStock(String productId, int quantity, String orderId);

    InventoryResponse addStock(String productId, int quantity);

    List<InventoryResponse> getLowStockItems();

    boolean isInStock(String productId, int quantity);
}
