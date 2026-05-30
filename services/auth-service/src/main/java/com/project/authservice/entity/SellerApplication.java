package com.project.authservice.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * One row per user. Status flow:
 * <pre>
 *   PENDING ──approve──► APPROVED   (terminal)
 *           ──reject ──► REJECTED   (user may submit a fresh application;
 *                                    the old one is overwritten)
 * </pre>
 *
 * <p>{@link Address} is reused for the pickup address so the storage layout
 * is consistent with {@link User}'s shipping/billing.
 */
@Entity
@Table(name = "seller_applications")
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class SellerApplication {

    @Id
    @Column(columnDefinition = "uuid")
    private UUID id;

    @Column(name = "user_id", nullable = false, unique = true, columnDefinition = "uuid")
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    @Builder.Default
    private SellerApplicationStatus status = SellerApplicationStatus.PENDING;

    @Column(name = "business_name", nullable = false, length = 180)
    private String businessName;

    @Column(length = 15)
    private String gstin;

    @Column(name = "contact_phone", nullable = false, length = 20)
    private String contactPhone;

    @Embedded
    @AttributeOverrides({
            @AttributeOverride(name = "fullName", column = @Column(name = "pickup_full_name", length = 120)),
            @AttributeOverride(name = "phone",    column = @Column(name = "pickup_phone",     length = 20)),
            @AttributeOverride(name = "street",   column = @Column(name = "pickup_street",    nullable = false, length = 200)),
            @AttributeOverride(name = "city",     column = @Column(name = "pickup_city",      nullable = false, length = 80)),
            @AttributeOverride(name = "state",    column = @Column(name = "pickup_state",     nullable = false, length = 80)),
            @AttributeOverride(name = "zipCode",  column = @Column(name = "pickup_zip_code",  nullable = false, length = 20)),
            @AttributeOverride(name = "country",  column = @Column(name = "pickup_country",   nullable = false, length = 80)),
    })
    private Address pickupAddress;

    @Column(name = "bank_account_last4", length = 4)
    private String bankAccountLast4;

    @Column(columnDefinition = "TEXT")
    private String notes;

    @Column(name = "rejection_reason", columnDefinition = "TEXT")
    private String rejectionReason;

    @CreationTimestamp
    @Column(name = "submitted_at", updatable = false)
    private LocalDateTime submittedAt;

    @Column(name = "reviewed_at")
    private LocalDateTime reviewedAt;

    @Column(name = "reviewed_by", columnDefinition = "uuid")
    private UUID reviewedBy;

    @org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.JSON)
    @Column(name = "business_metadata", columnDefinition = "jsonb")
    @Builder.Default
    private java.util.Map<String, Object> businessMetadata = new java.util.HashMap<>();

    @PrePersist
    void ensureId() {
        if (id == null) id = UUID.randomUUID();
    }
}
