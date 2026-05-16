package com.project.inventory.controller;

import com.project.inventory.dto.InventoryRequest;
import com.project.inventory.dto.InventoryResponse;
import com.project.inventory.dto.StockReservationRequest;
import com.project.inventory.service.InventoryService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/inventory")
@RequiredArgsConstructor
public class InventoryController {

    private final InventoryService inventoryService;

    @GetMapping
    public ResponseEntity<Page<InventoryResponse>> getAllInventory(
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return new ResponseEntity<>(inventoryService.getAllInventory(pageable), HttpStatus.OK);
    }

    @GetMapping("/{productId}")
    public ResponseEntity<InventoryResponse> getByProductId(@PathVariable String productId) {
        return new ResponseEntity<>(inventoryService.getByProductId(productId), HttpStatus.OK);
    }

    @GetMapping("/sku/{sku}")
    public ResponseEntity<InventoryResponse> getBySku(@PathVariable String sku) {
        return new ResponseEntity<>(inventoryService.getBySku(sku), HttpStatus.OK);
    }

    @GetMapping("/low-stock")
    public ResponseEntity<List<InventoryResponse>> getLowStockItems() {
        return new ResponseEntity<>(inventoryService.getLowStockItems(), HttpStatus.OK);
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'SELLER')")
    public ResponseEntity<InventoryResponse> createInventory(@Valid @RequestBody InventoryRequest request) {
        return new ResponseEntity<>(inventoryService.createInventory(request), HttpStatus.CREATED);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'SELLER')")
    public ResponseEntity<InventoryResponse> updateInventory(@PathVariable String id,
                                                              @Valid @RequestBody InventoryRequest request) {
        return new ResponseEntity<>(inventoryService.updateInventory(id, request), HttpStatus.OK);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> deleteInventory(@PathVariable String id) {
        inventoryService.deleteInventory(id);
        return new ResponseEntity<>(HttpStatus.NO_CONTENT);
    }

    @PostMapping("/{productId}/reserve")
    public ResponseEntity<InventoryResponse> reserveStock(@PathVariable String productId,
                                                           @Valid @RequestBody StockReservationRequest request) {
        return new ResponseEntity<>(
                inventoryService.reserveStock(productId, request.getQuantity(), request.getOrderId()),
                HttpStatus.OK);
    }

    @PostMapping("/{productId}/release")
    public ResponseEntity<InventoryResponse> releaseStock(@PathVariable String productId,
                                                           @Valid @RequestBody StockReservationRequest request) {
        return new ResponseEntity<>(
                inventoryService.releaseStock(productId, request.getQuantity(), request.getOrderId()),
                HttpStatus.OK);
    }

    @PostMapping("/{productId}/add-stock")
    public ResponseEntity<InventoryResponse> addStock(@PathVariable String productId,
                                                       @RequestParam int quantity) {
        return new ResponseEntity<>(inventoryService.addStock(productId, quantity), HttpStatus.OK);
    }

    @GetMapping("/{productId}/check")
    public ResponseEntity<Boolean> isInStock(@PathVariable String productId,
                                              @RequestParam int quantity) {
        return new ResponseEntity<>(inventoryService.isInStock(productId, quantity), HttpStatus.OK);
    }
}
