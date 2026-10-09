package com.project.inventory.controller;

import com.project.common.constant.Permissions;
import com.project.common.constant.ServiceScopes;
import com.project.inventory.application.mapper.InventoryApiMapper;
import com.project.inventory.application.validator.InventoryValidator;
import com.project.inventory.generated.api.InventoryApi;
import com.project.inventory.generated.model.InventoryRequest;
import com.project.inventory.generated.model.StockReservationRequest;
import com.project.inventory.service.InventoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
public class InventoryController implements InventoryApi {

    private final InventoryService inventoryService;
    private final InventoryValidator inventoryValidator;
    private final InventoryApiMapper apiMapper;

    @Override
    @PreAuthorize("hasAuthority('" + Permissions.INVENTORY_READ + "') or hasRole('ADMIN') or hasRole('SELLER')")
    public ResponseEntity<com.project.inventory.generated.model.PageInventoryResponse> listInventory(
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return ResponseEntity.ok(apiMapper.toApi(inventoryService.getAllInventory(pageable)));
    }

    @Override
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<com.project.inventory.generated.model.InventoryResponse> getInventoryByProduct(String id) {
        return ResponseEntity.ok(inventoryService.getByProductId(id));
    }

    @Override
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<com.project.inventory.generated.model.InventoryResponse> getInventoryBySku(String sku) {
        return ResponseEntity.ok(inventoryService.getBySku(sku));
    }

    @Override
    @PreAuthorize("hasRole('ADMIN') or hasRole('SELLER')")
    public ResponseEntity<List<com.project.inventory.generated.model.InventoryResponse>> listLowStockInventory() {
        return ResponseEntity.ok(inventoryService.getLowStockItems());
    }

    @Override
    @PreAuthorize("hasAuthority('" + Permissions.INVENTORY_WRITE + "')")
    public ResponseEntity<com.project.inventory.generated.model.InventoryResponse> createInventory(
            InventoryRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(inventoryService.createInventory(request));
    }

    @Override
    @PreAuthorize("hasAuthority('" + Permissions.INVENTORY_WRITE + "')")
    public ResponseEntity<com.project.inventory.generated.model.InventoryResponse> updateInventory(
            String id, InventoryRequest request) {
        return ResponseEntity.ok(inventoryService.updateInventory(id, request));
    }

    @Override
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> deleteInventory(String id) {
        inventoryService.deleteInventory(id);
        return ResponseEntity.noContent().build();
    }

    @Override
    @PreAuthorize("hasAuthority('" + Permissions.INVENTORY_WRITE + "')")
    public ResponseEntity<com.project.inventory.generated.model.InventoryResponse> addStock(
            String productId, Integer quantity) {
        inventoryValidator.validatePositiveQuantity(quantity);
        return ResponseEntity.ok(inventoryService.addStock(productId, quantity));
    }

    @Override
    @PreAuthorize("T(com.project.common.security.CurrentUser).isService() and hasAuthority('"
            + ServiceScopes.AUTHORITY_INVENTORY_WRITE + "')")
    public ResponseEntity<com.project.inventory.generated.model.InventoryResponse> reserveStock(
            String productId, StockReservationRequest request) {
        int quantity = request.getQuantity() == null ? 0 : request.getQuantity();
        inventoryValidator.validateReservation(quantity, request.getOrderId());
        return ResponseEntity.ok(inventoryService.reserveStock(
                productId, quantity, request.getOrderId()));
    }

    @Override
    @PreAuthorize("T(com.project.common.security.CurrentUser).isService() and hasAuthority('"
            + ServiceScopes.AUTHORITY_INVENTORY_WRITE + "')")
    public ResponseEntity<com.project.inventory.generated.model.InventoryResponse> commitStock(
            String productId, StockReservationRequest request) {
        int quantity = request.getQuantity() == null ? 0 : request.getQuantity();
        inventoryValidator.validateReservation(quantity, request.getOrderId());
        return ResponseEntity.ok(inventoryService.commitStock(
                productId, quantity, request.getOrderId()));
    }

    @Override
    @PreAuthorize("T(com.project.common.security.CurrentUser).isService() and hasAuthority('"
            + ServiceScopes.AUTHORITY_INVENTORY_WRITE + "')")
    public ResponseEntity<com.project.inventory.generated.model.InventoryResponse> releaseStock(
            String productId, StockReservationRequest request) {
        int quantity = request.getQuantity() == null ? 0 : request.getQuantity();
        inventoryValidator.validateReservation(quantity, request.getOrderId());
        return ResponseEntity.ok(inventoryService.releaseStock(
                productId, quantity, request.getOrderId()));
    }

    @Override
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Boolean> checkStockAvailability(String productId, Integer quantity) {
        return ResponseEntity.ok(inventoryService.isInStock(productId, quantity));
    }

}
