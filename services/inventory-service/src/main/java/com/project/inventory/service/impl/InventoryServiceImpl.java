package com.project.inventory.service.impl;

import com.project.inventory.dto.InventoryRequest;
import com.project.inventory.dto.InventoryResponse;
import com.project.inventory.event.InventoryEvent;
import com.project.inventory.exception.DuplicateResourceException;
import com.project.inventory.exception.InsufficientStockException;
import com.project.inventory.exception.ResourceNotFoundException;
import com.project.inventory.model.InventoryItem;
import com.project.inventory.repository.InventoryRepository;
import com.project.inventory.service.InventoryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class InventoryServiceImpl implements InventoryService {

    private static final String REDIS_KEY_PREFIX = "inventory:";
    private static final long REDIS_TTL_HOURS = 2;
    private static final String INVENTORY_EVENTS_TOPIC = "inventory-events";

    private final InventoryRepository inventoryRepository;
    private final RedisTemplate<String, Object> redisTemplate;
    private final KafkaTemplate<String, InventoryEvent> kafkaTemplate;
    private final MongoTemplate mongoTemplate;

    @Override
    public Page<InventoryResponse> getAllInventory(Pageable pageable) {
        log.debug("Fetching all inventory with pageable: {}", pageable);
        return inventoryRepository.findAll(pageable)
                .map(this::mapToResponse);
    }

    @Override
    public InventoryResponse getByProductId(String productId) {
        log.debug("Fetching inventory for productId: {}", productId);
        String cacheKey = REDIS_KEY_PREFIX + productId;

        InventoryItem cached = (InventoryItem) redisTemplate.opsForValue().get(cacheKey);
        if (cached != null) {
            log.debug("Cache hit for inventory:{}", productId);
            return mapToResponse(cached);
        }

        InventoryItem item = inventoryRepository.findByProductId(productId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Inventory not found for product: " + productId, productId));

        redisTemplate.opsForValue().set(cacheKey, item, REDIS_TTL_HOURS, TimeUnit.HOURS);
        log.debug("Cached inventory:{} in Redis", productId);

        return mapToResponse(item);
    }

    @Override
    public InventoryResponse getBySku(String sku) {
        log.debug("Fetching inventory for sku: {}", sku);
        InventoryItem item = inventoryRepository.findBySku(sku)
                .orElseThrow(() -> new ResourceNotFoundException("Inventory not found for sku: " + sku));
        return mapToResponse(item);
    }

    @Override
    @Transactional
    public InventoryResponse createInventory(InventoryRequest request) {
        log.info("Creating inventory for productId: {}", request.getProductId());

        if (inventoryRepository.findByProductId(request.getProductId()).isPresent()) {
            throw new DuplicateResourceException(
                    "Inventory already exists for product: " + request.getProductId());
        }

        if (inventoryRepository.findBySku(request.getSku()).isPresent()) {
            throw new DuplicateResourceException(
                    "Inventory already exists for sku: " + request.getSku());
        }

        InventoryItem item = new InventoryItem();
        item.setProductId(request.getProductId());
        item.setSku(request.getSku());
        item.setQuantity(request.getQuantity());
        item.setReservedQuantity(0);
        item.setLowStockThreshold(request.getLowStockThreshold() > 0
                ? request.getLowStockThreshold() : 10);
        item.setLocation(request.getLocation());
        item.setCreatedAt(LocalDateTime.now());
        item.setUpdatedAt(LocalDateTime.now());

        if (request.getQuantity() > 0) {
            item.setLastRestockedAt(LocalDateTime.now());
        }

        item = inventoryRepository.save(item);

        cacheItem(item);
        publishEvent("INVENTORY_CREATED", item, 0, item.getQuantity(), null);

        log.info("Inventory created for productId: {} with id: {}", item.getProductId(), item.getId());
        return mapToResponse(item);
    }

    @Override
    @Transactional
    public InventoryResponse updateInventory(String id, InventoryRequest request) {
        log.info("Updating inventory with id: {}", id);

        InventoryItem item = inventoryRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Inventory not found with id: " + id));

        boolean quantityChanged = item.getQuantity() != request.getQuantity();
        int oldQuantity = item.getQuantity();

        item.setSku(request.getSku());
        item.setQuantity(request.getQuantity());
        item.setLowStockThreshold(request.getLowStockThreshold() > 0
                ? request.getLowStockThreshold() : 10);
        item.setLocation(request.getLocation());

        if (quantityChanged && request.getQuantity() > oldQuantity) {
            item.setLastRestockedAt(LocalDateTime.now());
        }

        item.setUpdatedAt(LocalDateTime.now());
        item = inventoryRepository.save(item);

        cacheItem(item);
        publishEvent("INVENTORY_UPDATED", item, request.getQuantity() - oldQuantity,
                item.getQuantity(), null);

        log.info("Inventory updated for id: {}", id);
        return mapToResponse(item);
    }

    @Override
    @Transactional
    public void deleteInventory(String id) {
        log.info("Deleting inventory with id: {}", id);

        InventoryItem item = inventoryRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Inventory not found with id: " + id));

        inventoryRepository.delete(item);

        String cacheKey = REDIS_KEY_PREFIX + item.getProductId();
        redisTemplate.delete(cacheKey);

        publishEvent("INVENTORY_DELETED", item, -item.getQuantity(), 0, null);

        log.info("Inventory deleted for productId: {}", item.getProductId());
    }

    @Override
    @Transactional
    public InventoryResponse reserveStock(String productId, int quantity, String orderId) {
        log.info("Reserving {} units for productId: {} (orderId: {})", quantity, productId, orderId);

        if (quantity <= 0) {
            throw new IllegalArgumentException("Reservation quantity must be positive");
        }

        InventoryItem item = inventoryRepository.findByProductId(productId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Inventory not found for product: " + productId, productId));

        int available = item.getQuantity() - item.getReservedQuantity();
        if (available < quantity) {
            log.warn("Insufficient stock for productId: {} - available: {}, requested: {}",
                    productId, available, quantity);
            throw new InsufficientStockException(
                    "Insufficient stock for product: " + productId
                            + ". Available: " + available + ", requested: " + quantity);
        }

        item.setReservedQuantity(item.getReservedQuantity() + quantity);
        item.setUpdatedAt(LocalDateTime.now());
        item = inventoryRepository.save(item);

        invalidateCache(productId);
        publishEvent("STOCK_RESERVED", item, -quantity, item.getQuantity() - item.getReservedQuantity(), orderId);

        log.info("Reserved {} units for productId: {}, remaining available: {}",
                quantity, productId, item.getQuantity() - item.getReservedQuantity());
        return mapToResponse(item);
    }

    @Override
    @Transactional
    public InventoryResponse releaseStock(String productId, int quantity, String orderId) {
        log.info("Releasing {} units for productId: {} (orderId: {})", quantity, productId, orderId);

        if (quantity <= 0) {
            throw new IllegalArgumentException("Release quantity must be positive");
        }

        InventoryItem item = inventoryRepository.findByProductId(productId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Inventory not found for product: " + productId, productId));

        if (item.getReservedQuantity() < quantity) {
            log.warn("Cannot release {} units for productId: {} - only {} reserved",
                    quantity, productId, item.getReservedQuantity());
            throw new InsufficientStockException(
                    "Cannot release " + quantity + " units for product: " + productId
                            + ". Only " + item.getReservedQuantity() + " reserved.");
        }

        item.setReservedQuantity(item.getReservedQuantity() - quantity);
        item.setUpdatedAt(LocalDateTime.now());
        item = inventoryRepository.save(item);

        invalidateCache(productId);
        publishEvent("STOCK_RELEASED", item, quantity, item.getQuantity() - item.getReservedQuantity(), orderId);

        log.info("Released {} units for productId: {}", quantity, productId);
        return mapToResponse(item);
    }

    @Override
    @Transactional
    public InventoryResponse addStock(String productId, int quantity) {
        log.info("Adding {} units to stock for productId: {}", quantity, productId);

        if (quantity <= 0) {
            throw new IllegalArgumentException("Quantity to add must be positive");
        }

        InventoryItem item = inventoryRepository.findByProductId(productId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Inventory not found for product: " + productId, productId));

        item.setQuantity(item.getQuantity() + quantity);
        item.setLastRestockedAt(LocalDateTime.now());
        item.setUpdatedAt(LocalDateTime.now());
        item = inventoryRepository.save(item);

        invalidateCache(productId);
        publishEvent("STOCK_ADDED", item, quantity, item.getQuantity(), null);

        log.info("Added {} units to productId: {}, new quantity: {}", quantity, productId, item.getQuantity());
        return mapToResponse(item);
    }

    @Override
    public List<InventoryResponse> getLowStockItems() {
        log.debug("Fetching low stock items");
        List<InventoryItem> items = inventoryRepository.findByQuantityLessThan(0);
        List<InventoryItem> lowStockItems = inventoryRepository.findAll().stream()
                .filter(item -> item.getQuantity() <= item.getLowStockThreshold())
                .collect(Collectors.toList());
        log.info("Found {} low stock items", lowStockItems.size());
        return lowStockItems.stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    @Override
    public boolean isInStock(String productId, int quantity) {
        log.debug("Checking stock for productId: {}, quantity: {}", productId, quantity);
        try {
            InventoryResponse response = getByProductId(productId);
            boolean inStock = response.getAvailableQuantity() >= quantity;
            log.debug("ProductId: {} in stock: {} (available: {}, requested: {})",
                    productId, inStock, response.getAvailableQuantity(), quantity);
            return inStock;
        } catch (ResourceNotFoundException e) {
            log.warn("Stock check failed - product not found: {}", productId);
            return false;
        }
    }

    private void cacheItem(InventoryItem item) {
        String cacheKey = REDIS_KEY_PREFIX + item.getProductId();
        try {
            redisTemplate.opsForValue().set(cacheKey, item, REDIS_TTL_HOURS, TimeUnit.HOURS);
        } catch (Exception e) {
            log.warn("Failed to cache inventory:{} in Redis: {}", item.getProductId(), e.getMessage());
        }
    }

    private void invalidateCache(String productId) {
        String cacheKey = REDIS_KEY_PREFIX + productId;
        try {
            redisTemplate.delete(cacheKey);
            log.debug("Invalidated Redis cache for inventory:{}", productId);
        } catch (Exception e) {
            log.warn("Failed to invalidate Redis cache for inventory:{}: {}", productId, e.getMessage());
        }
    }

    private void publishEvent(String eventType, InventoryItem item, int quantityChange,
                              int newQuantity, String orderId) {
        try {
            InventoryEvent event = InventoryEvent.builder()
                    .eventId(UUID.randomUUID().toString())
                    .eventType(eventType)
                    .productId(item.getProductId())
                    .sku(item.getSku())
                    .quantityChange(quantityChange)
                    .newQuantity(newQuantity)
                    .reservedQuantity(item.getReservedQuantity())
                    .orderId(orderId)
                    .location(item.getLocation())
                    .timestamp(LocalDateTime.now())
                    .build();

            kafkaTemplate.send(INVENTORY_EVENTS_TOPIC, item.getProductId(), event);
            log.debug("Published {} event for productId: {}", eventType, item.getProductId());
        } catch (Exception e) {
            log.error("Failed to publish {} event for productId: {}: {}",
                    eventType, item.getProductId(), e.getMessage());
        }
    }

    private InventoryResponse mapToResponse(InventoryItem item) {
        InventoryResponse response = new InventoryResponse();
        response.setId(item.getId());
        response.setProductId(item.getProductId());
        response.setSku(item.getSku());
        response.setQuantity(item.getQuantity());
        response.setReservedQuantity(item.getReservedQuantity());
        response.setAvailableQuantity(item.getQuantity() - item.getReservedQuantity());
        response.setLowStockThreshold(item.getLowStockThreshold());
        response.setLocation(item.getLocation());
        response.setLastRestockedAt(item.getLastRestockedAt());
        response.setCreatedAt(item.getCreatedAt());
        response.setUpdatedAt(item.getUpdatedAt());
        return response;
    }
}
