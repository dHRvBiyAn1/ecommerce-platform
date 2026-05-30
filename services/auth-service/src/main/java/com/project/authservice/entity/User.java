package com.project.authservice.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

@Entity
@Table(name = "users")
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class User {
    @Id
    private UUID id;

    /**
     * Assign a UUID if the caller didn't pre-set one. Replaces
     * {@code @GeneratedValue(UUID)} so seed code can supply deterministic IDs
     * without Hibernate overwriting them. Normal registration paths leave id
     * null and get a random UUID here.
     */
    @PrePersist
    void ensureId() {
        if (id == null) id = UUID.randomUUID();
    }

    @Column(nullable = false, unique = true)
    private String email;

    private String displayName;

    private String imageUrl;

    @Column(length = 20)
    private String phone;

    private boolean active = true;

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;

    @ManyToMany(fetch = FetchType.EAGER)
    @JoinTable(name = "user_roles", joinColumns = @JoinColumn(name = "user_id"), inverseJoinColumns = @JoinColumn(name = "role_id"))
    private Set<Role> roles = new HashSet<>();

    /**
     * Default shipping address. Columns stored on the {@code users} table
     * with a {@code shipping_} prefix.
     */
    @Embedded
    @AttributeOverrides({
            @AttributeOverride(name = "fullName", column = @Column(name = "shipping_full_name", length = 120)),
            @AttributeOverride(name = "phone",    column = @Column(name = "shipping_phone",     length = 20)),
            @AttributeOverride(name = "street",   column = @Column(name = "shipping_street",    length = 200)),
            @AttributeOverride(name = "city",     column = @Column(name = "shipping_city",      length = 80)),
            @AttributeOverride(name = "state",    column = @Column(name = "shipping_state",     length = 80)),
            @AttributeOverride(name = "zipCode",  column = @Column(name = "shipping_zip_code",  length = 20)),
            @AttributeOverride(name = "country",  column = @Column(name = "shipping_country",   length = 80)),
    })
    private Address shippingAddress;

    /** Default billing address; columns prefixed {@code billing_}. */
    @Embedded
    @AttributeOverrides({
            @AttributeOverride(name = "fullName", column = @Column(name = "billing_full_name", length = 120)),
            @AttributeOverride(name = "phone",    column = @Column(name = "billing_phone",     length = 20)),
            @AttributeOverride(name = "street",   column = @Column(name = "billing_street",    length = 200)),
            @AttributeOverride(name = "city",     column = @Column(name = "billing_city",      length = 80)),
            @AttributeOverride(name = "state",    column = @Column(name = "billing_state",     length = 80)),
            @AttributeOverride(name = "zipCode",  column = @Column(name = "billing_zip_code",  length = 20)),
            @AttributeOverride(name = "country",  column = @Column(name = "billing_country",   length = 80)),
    })
    private Address billingAddress;

    @org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.JSON)
    @Column(name = "metadata", columnDefinition = "jsonb")
    private java.util.Map<String, Object> metadata = new java.util.HashMap<>();
}
