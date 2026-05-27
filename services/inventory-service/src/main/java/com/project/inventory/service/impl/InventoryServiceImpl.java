package com.project.inventory.service.impl;

import com.project.common.constant.Topics;
import com.project.common.event.InventoryEvent;
import com.project.common.exception.DuplicateResourceException;
import com.project.common.exception.ResourceNotFoundException;
import com.project.inventory.dto.InventoryRequest;
import com.project.inventory.dto.InventoryResponse;
import com.project.inventory.exception.InsufficientStockException;
import com.project.inventory.model.InventoryItem;
import com.project.inventory.repository.InventoryRepository;
import com.project.inventory.service.InventoryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bson.Document;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
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

@Slf4j
@Service
@RequiredArgsConstructor
public class InventoryServiceImpl implements InventoryService {

    private static final String REDIS_KEY_PREFIX = "inventory:";
    private static final long REDIS_TTL_HOURS = 2;

    private final InventoryRepository inventoryRepository;
    private final MongoTemplate mongoTemplate;
    private final RedisTemplate<String, Object> redisTemplate;
    private final KafkaTemplate<String, Object> kafkaTemplate;

    @Override
    public Page<InventoryResponse> getAllInventory(Pageable pageable) {
        return inventoryRepository.findAll(pageable).map(this::mapToResponse);
    }

    @Override
    public InventoryResponse getByProductId(String productId) {
        String cacheKey = REDIS_KEY_PREFIX + productId;
        InventoryItem cached = (InventoryItem) redisTemplate.opsForValue().get(cacheKey);
        if (cached != null) return mapToResponse(cached);

        InventoryItem item = inventoryRepository.findByProductId(productId)
                .orElseThrow(() -> new ResourceNotFoundException("Inventory for product", productId));
        redisTemplate.opsForValue().set(cacheKey, item, REDIS_TTL_HOURS, TimeUnit.HOURS);
        return mapToResponse(item);
    }

    @Override
    public InventoryResponse getBySku(String sku) {
        InventoryItem item = inventoryRepository.findBySku(sku)
                .orElseThrow(() -> new ResourceNotFoundException("Inventory for sku", sku));
        return mapToResponse(item);
    }

    @Override
    @Transactional
    public InventoryResponse createInventory(InventoryRequest request) {
        if (inventoryRepository.findByProductId(request.getProductId()).isPresent()) {
            throw new DuplicateResourceException("Inventory exists for product " + request.getProductId());
        }
        if (inventoryRepository.findBySku(request.getSku()).isPresent()) {
            throw new DuplicateResourceException("Inventory exists for sku " + request.getSku());
        }
        InventoryItem item = new InventoryItem();
        item.setProductId(request.getProductId());
        item.setSku(request.getSku());
        item.setQuantity(request.getQuantity());
        item.setReservedQuantity(0);
        item.setLowStockThreshold(request.getLowStockThreshold() > 0 ? request.getLowStockThreshold() : 10);
        item.setLocation(request.getLocation());
        item.setCreatedAt(LocalDateTime.now());
        item.setUpdatedAt(LocalDateTime.now());
        if (request.getQuantity() > 0) item.setLastRestockedAt(LocalDateTime.now());
        item = inventoryRepository.save(item);
        cacheItem(item);
        publish(InventoryEvent.Type.INVENTORY_CREATED, item, item.getQuantity(), null);
        return mapToResponse(item);
    }

    @Override
    @Transactional
    public InventoryResponse updateInventory(String id, InventoryRequest request) {
        InventoryItem item = inventoryRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Inventory", id));
        int delta = request.getQuantity() - item.getQuantity();
        item.setSku(request.getSku());
        item.setQuantity(request.getQuantity());
        item.setLowStockThreshold(request.getLowStockThreshold() > 0 ? request.getLowStockThreshold() : 10);
        item.setLocation(request.getLocation());
        item.setUpdatedAt(LocalDateTime.now());
        if (delta > 0) item.setLastRestockedAt(LocalDateTime.now());
        item = inventoryRepository.save(item);
        cacheItem(item);
        publish(InventoryEvent.Type.INVENTORY_UPDATED, item, delta, null);
        return mapToResponse(item);
    }

    @Override
    @Transactional
    public void deleteInventory(String id) {
        InventoryItem item = inventoryRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Inventory", id));
        inventoryRepository.delete(item);
        redisTemplate.delete(REDIS_KEY_PREFIX + item.getProductId());
        publish(InventoryEvent.Type.INVENTORY_DELETED, item, -item.getQuantity(), null);
    }

    /**
     * Atomic reservation. Uses Mongo {@code findAndModify} with a guard expression
     * {@code (quantity - reservedQuantity) >= qty} so concurrent reservations cannot
     * over-commit stock. Replaces the previous read-modify-write race condition.
     */
    @Override
    public InventoryResponse reserveStock(String productId, int quantity, String orderId) {
        if (quantity <= 0) throw new IllegalArgumentException("Quantity must be positive");

        Query query = new Query(Criteria.where("productId").is(productId)
                .andOperator(Criteria.where("$expr").is(
                        new Document("$gte", List.of(
                                new Document("$subtract", List.of("$quantity", "$reservedQuantity")),
                                quantity
                        )))));
        Update update = new Update()
                .inc("reservedQuantity", quantity)
                .set("updatedAt", LocalDateTime.now());

        InventoryItem updated = mongoTemplate.findAndModify(query, update,
                FindAndModifyOptions.options().returnNew(true), InventoryItem.class);

        if (updated == null) {
            // Either product not found OR insufficient stock; disambiguate.
            InventoryItem existing = inventoryRepository.findByProductId(productId).orElse(null);
            if (existing == null) {
                throw new ResourceNotFoundException("Inventory for product", productId);
            }
            int available = existing.getQuantity() - existing.getReservedQuantity();
            throw new InsufficientStockException(
                    "Insufficient stock for " + productId + ": available=" + available + ", requested=" + quantity);
        }

        invalidateCache(productId);
        publish(InventoryEvent.Type.STOCK_RESERVED, updated, -quantity, orderId);
        return mapToResponse(updated);
    }

    @Override
    public InventoryResponse releaseStock(String productId, int quantity, String orderId) {
        if (quantity <= 0) throw new IllegalArgumentException("Quantity must be positive");

        Query query = new Query(Criteria.where("productId").is(productId)
                .and("reservedQuantity").gte(quantity));
        Update update = new Update()
                .inc("reservedQuantity", -quantity)
                .set("updatedAt", LocalDateTime.now());

        InventoryItem updated = mongoTemplate.findAndModify(query, update,
                FindAndModifyOptions.options().returnNew(true), InventoryItem.class);
        if (updated == null) {
            log.warn("Release no-op for {} qty {}: nothing to release", productId, quantity);
            return mapToResponse(inventoryRepository.findByProductId(productId)
                    .orElseThrow(() -> new ResourceNotFoundException("Inventory for product", productId)));
        }

        invalidateCache(productId);
        publish(InventoryEvent.Type.STOCK_RELEASED, updated, quantity, orderId);
        return mapToResponse(updated);
    }

    @Override
    public InventoryResponse addStock(String productId, int quantity) {
        if (quantity <= 0) throw new IllegalArgumentException("Quantity must be positive");

        Query query = new Query(Criteria.where("productId").is(productId));
        Update update = new Update()
                .inc("quantity", quantity)
                .set("lastRestockedAt", LocalDateTime.now())
                .set("updatedAt", LocalDateTime.now());

        InventoryItem updated = mongoTemplate.findAndModify(query, update,
                FindAndModifyOptions.options().returnNew(true), InventoryItem.class);
        if (updated == null) throw new ResourceNotFoundException("Inventory for product", productId);

        invalidateCache(productId);
        publish(InventoryEvent.Type.STOCK_ADDED, updated, quantity, null);
        if (updated.getQuantity() > updated.getLowStockThreshold()) {
            publish(InventoryEvent.Type.RESTOCKED, updated, quantity, null);
        }
        return mapToResponse(updated);
    }

    /**
     * Mongo aggregation: items where quantity &lt;= lowStockThreshold. Replaces the
     * previous in-memory filter that scanned every record.
     */
    @Override
    public List<InventoryResponse> getLowStockItems() {
        Query q = new Query(Criteria.where("$expr").is(
                new Document("$lte", List.of("$quantity", "$lowStockThreshold"))));
        return mongoTemplate.find(q, InventoryItem.class).stream()
                .map(this::mapToResponse).collect(Collectors.toList());
    }

    @Override
    public boolean isInStock(String productId, int quantity) {
        try {
            InventoryResponse r = getByProductId(productId);
            return r.getAvailableQuantity() >= quantity;
        } catch (ResourceNotFoundException e) {
            return false;
        }
    }

    // ----- helpers -----

    private void cacheItem(InventoryItem item) {
        try {
            redisTemplate.opsForValue().set(REDIS_KEY_PREFIX + item.getProductId(), item,
                    REDIS_TTL_HOURS, TimeUnit.HOURS);
        } catch (Exception e) {
            log.warn("Cache write failed for {}: {}", item.getProductId(), e.getMessage());
        }
    }

    private void invalidateCache(String productId) {
        try {
            redisTemplate.delete(REDIS_KEY_PREFIX + productId);
        } catch (Exception e) {
            log.warn("Cache invalidate failed for {}: {}", productId, e.getMessage());
        }
    }

    private void publish(InventoryEvent.Type type, InventoryItem item, int delta, String orderId) {
        try {
            InventoryEvent event = InventoryEvent.inventoryEventBuilder()
                    .type(type)
                    .productId(item.getProductId())
                    .sku(item.getSku())
                    .warehouseId(item.getLocation())
                    .quantityChange(delta)
                    .newQuantity(item.getQuantity())
                    .reservedQuantity(item.getReservedQuantity())
                    .availableQuantity(item.getQuantity() - item.getReservedQuantity())
                    .orderId(orderId)
                    .build();
            kafkaTemplate.send(Topics.INVENTORY_EVENTS, item.getProductId(), event);
        } catch (Exception e) {
            log.error("Failed to publish inventory event {} for {}: {}", type, item.getProductId(), e.getMessage());
        }
    }

    private InventoryResponse mapToResponse(InventoryItem item) {
        InventoryResponse r = new InventoryResponse();
        r.setId(item.getId());
        r.setProductId(item.getProductId());
        r.setSku(item.getSku());
        r.setQuantity(item.getQuantity());
        r.setReservedQuantity(item.getReservedQuantity());
        r.setAvailableQuantity(item.getQuantity() - item.getReservedQuantity());
        r.setLowStockThreshold(item.getLowStockThreshold());
        r.setLocation(item.getLocation());
        r.setLastRestockedAt(item.getLastRestockedAt());
        r.setCreatedAt(item.getCreatedAt());
        r.setUpdatedAt(item.getUpdatedAt());
        return r;
    }
}
