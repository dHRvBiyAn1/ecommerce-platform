package com.project.inventory.controller;

import com.project.common.constant.Permissions;
import com.project.common.constant.ServiceScopes;
import com.project.inventory.api.dto.request.InventoryRequest;
import com.project.inventory.api.dto.request.StockReservationRequest;
import com.project.inventory.api.dto.response.InventoryResponse;
import com.project.inventory.application.validator.InventoryValidator;
import com.project.inventory.service.InventoryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
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

import static com.project.inventory.config.InventoryOpenApiConfiguration.BEARER_AUTH;
import static com.project.inventory.config.InventoryOpenApiConfiguration.NOT_FOUND_ERROR;
import static com.project.inventory.config.InventoryOpenApiConfiguration.VALIDATION_ERROR;

@RestController
@RequestMapping("/api/v1/inventory")
@RequiredArgsConstructor
@Tag(name = "Inventory", description = "Stock availability and reservation operations")
@SecurityRequirement(name = BEARER_AUTH)
public class InventoryController {

    private final InventoryService inventoryService;
    private final InventoryValidator inventoryValidator;

    // Reads for sellers, admins, and authenticated callers.
    @GetMapping
    @Operation(summary = "List inventory")
    @PreAuthorize("hasAuthority('" + Permissions.INVENTORY_READ + "') or hasRole('ADMIN') or hasRole('SELLER')")
    public ResponseEntity<Page<InventoryResponse>> getAllInventory(
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return ResponseEntity.ok(inventoryService.getAllInventory(pageable));
    }

    @GetMapping("/{productId}")
    @Operation(summary = "Get inventory by product")
    @ApiResponse(responseCode = "404", ref = NOT_FOUND_ERROR)
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<InventoryResponse> getByProductId(@PathVariable String productId) {
        return ResponseEntity.ok(inventoryService.getByProductId(productId));
    }

    @GetMapping("/sku/{sku}")
    @Operation(summary = "Get inventory by SKU")
    @ApiResponse(responseCode = "404", ref = NOT_FOUND_ERROR)
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<InventoryResponse> getBySku(@PathVariable String sku) {
        return ResponseEntity.ok(inventoryService.getBySku(sku));
    }

    @GetMapping("/low-stock")
    @Operation(summary = "List low-stock inventory")
    @PreAuthorize("hasRole('ADMIN') or hasRole('SELLER')")
    public ResponseEntity<List<InventoryResponse>> getLowStockItems() {
        return ResponseEntity.ok(inventoryService.getLowStockItems());
    }

    // Writes — admin & seller
    @PostMapping
    @Operation(summary = "Create inventory")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Inventory created"),
            @ApiResponse(responseCode = "400", ref = VALIDATION_ERROR),
            @ApiResponse(responseCode = "409", description = "Product or SKU already has inventory", content = @Content)
    })
    @PreAuthorize("hasAuthority('" + Permissions.INVENTORY_WRITE + "')")
    public ResponseEntity<InventoryResponse> createInventory(@Valid @RequestBody InventoryRequest request) {
        return new ResponseEntity<>(inventoryService.createInventory(request), HttpStatus.CREATED);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update inventory")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Inventory updated"),
            @ApiResponse(responseCode = "400", ref = VALIDATION_ERROR),
            @ApiResponse(responseCode = "404", ref = NOT_FOUND_ERROR)
    })
    @PreAuthorize("hasAuthority('" + Permissions.INVENTORY_WRITE + "')")
    public ResponseEntity<InventoryResponse> updateInventory(@PathVariable String id,
                                                              @Valid @RequestBody InventoryRequest request) {
        return ResponseEntity.ok(inventoryService.updateInventory(id, request));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete inventory")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> deleteInventory(@PathVariable String id) {
        inventoryService.deleteInventory(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{productId}/add-stock")
    @Operation(summary = "Add stock")
    @PreAuthorize("hasAuthority('" + Permissions.INVENTORY_WRITE + "')")
    public ResponseEntity<InventoryResponse> addStock(@PathVariable String productId,
                                                       @RequestParam int quantity) {
        inventoryValidator.validatePositiveQuantity(quantity);
        return ResponseEntity.ok(inventoryService.addStock(productId, quantity));
    }

    // Reservation endpoints are machine-only operations within the order saga.
    @PostMapping("/{productId}/reserve")
    @Operation(summary = "Reserve stock for an order")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Stock reserved"),
            @ApiResponse(responseCode = "400", ref = VALIDATION_ERROR),
            @ApiResponse(responseCode = "404", ref = NOT_FOUND_ERROR),
            @ApiResponse(responseCode = "409", description = "Insufficient stock", content = @Content)
    })
    @PreAuthorize("T(com.project.common.security.CurrentUser).isService() and hasAuthority('" + ServiceScopes.AUTHORITY_INVENTORY_WRITE + "')")
    public ResponseEntity<InventoryResponse> reserveStock(@PathVariable String productId,
                                                           @Valid @RequestBody StockReservationRequest request) {
        inventoryValidator.validateReservation(request.quantity(), request.orderId());
        return ResponseEntity.ok(
                inventoryService.reserveStock(productId, request.quantity(), request.orderId()));
    }

    @PostMapping("/{productId}/commit")
    @Operation(summary = "Commit reserved stock after successful payment")
    @PreAuthorize("T(com.project.common.security.CurrentUser).isService() and hasAuthority('" + ServiceScopes.AUTHORITY_INVENTORY_WRITE + "')")
    public ResponseEntity<InventoryResponse> commitStock(@PathVariable String productId,
                                                          @Valid @RequestBody StockReservationRequest request) {
        inventoryValidator.validateReservation(request.quantity(), request.orderId());
        return ResponseEntity.ok(
                inventoryService.commitStock(productId, request.quantity(), request.orderId()));
    }

    @PostMapping("/{productId}/release")
    @Operation(summary = "Release an order stock reservation")
    @PreAuthorize("T(com.project.common.security.CurrentUser).isService() and hasAuthority('" + ServiceScopes.AUTHORITY_INVENTORY_WRITE + "')")
    public ResponseEntity<InventoryResponse> releaseStock(@PathVariable String productId,
                                                           @Valid @RequestBody StockReservationRequest request) {
        inventoryValidator.validateReservation(request.quantity(), request.orderId());
        return ResponseEntity.ok(
                inventoryService.releaseStock(productId, request.quantity(), request.orderId()));
    }

    @GetMapping("/{productId}/check")
    @Operation(summary = "Check stock availability")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Boolean> isInStock(@PathVariable String productId,
                                              @RequestParam int quantity) {
        return ResponseEntity.ok(inventoryService.isInStock(productId, quantity));
    }
}
