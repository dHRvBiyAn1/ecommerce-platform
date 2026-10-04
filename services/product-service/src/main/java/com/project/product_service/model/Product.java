package com.project.product_service.model;

import lombok.*;

import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.index.TextIndexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Document(collection = "products")
@org.springframework.data.mongodb.core.index.CompoundIndexes({
    @org.springframework.data.mongodb.core.index.CompoundIndex(name = "idx_category_active_approval", def = "{'categoryId': 1, 'active': 1, 'approvalStatus': 1}"),
    @org.springframework.data.mongodb.core.index.CompoundIndex(name = "idx_price_active", def = "{'price': 1, 'active': 1}"),
    @org.springframework.data.mongodb.core.index.CompoundIndex(name = "idx_seller_active", def = "{'sellerId': 1, 'active': 1}")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Product {
    @Id
    private String id;

    @Indexed(unique = true)
    private String sku;

    @TextIndexed
    private String name;

    @TextIndexed
    private String description;

    private String categoryId; // reference to Category.id

    private BigDecimal price;

    private Integer stockQuantity;

    private List<String> imageUrls; // array of image URLs

    private UUID sellerId; // references user-service user ID (UUID)

    /**
     * Flexible product attributes stored as a MongoDB sub-document.
     * Allows heterogeneous, category-specific fields (e.g. color, size,
     * material, wattage) without schema changes.
     * A wildcard index on {@code attributes.$**} enables efficient
     * queries on any nested key.
     */
    @org.springframework.data.mongodb.core.index.WildcardIndexed
    private Map<String, Object> attributes = new HashMap<>();

    private boolean active = true; // for soft delete / visibility

    /**
     * Marketplace moderation status. New seller-listed products default to
     * {@link ProductApprovalStatus#PENDING}; admin-created products are
     * auto-APPROVED. Public catalog endpoints filter by APPROVED.
     */
    @Indexed
    private ProductApprovalStatus approvalStatus = ProductApprovalStatus.PENDING;

    /** Free-text reason populated when the admin rejects the listing. */
    private String rejectionReason;

    private java.time.LocalDateTime reviewedAt;

    private UUID reviewedBy;

    @CreatedDate
    private java.time.LocalDateTime createdAt;

    @LastModifiedDate
    private java.time.LocalDateTime updatedAt;
}
