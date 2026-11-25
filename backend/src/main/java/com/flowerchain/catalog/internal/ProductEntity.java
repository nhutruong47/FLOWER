package com.flowerchain.catalog.internal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "products")
public class ProductEntity {

    @Id
    private UUID id;

    @Column(name = "category_id", nullable = false)
    private UUID categoryId;

    @Column(name = "sku", nullable = false, unique = true, length = 64)
    private String sku;

    @Column(name = "name", nullable = false, length = 200)
    private String name;

    @Column(name = "description", columnDefinition = "text")
    private String description;

    @Column(name = "image_url", length = 500)
    private String imageUrl;

    @Column(name = "unit_label", nullable = false, length = 32)
    private String unitLabel;

    @Column(name = "status", nullable = false, length = 16)
    private String status;

    @Column(name = "available_from")
    private Instant availableFrom;

    @Column(name = "available_until")
    private Instant availableUntil;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected ProductEntity() {}

    public ProductEntity(
            UUID id,
            UUID categoryId,
            String sku,
            String name,
            String description,
            String imageUrl,
            String unitLabel,
            String status,
            Instant availableFrom,
            Instant availableUntil,
            Instant now) {
        this.id = id;
        this.categoryId = categoryId;
        this.sku = sku;
        this.name = name;
        this.description = description;
        this.imageUrl = imageUrl;
        this.unitLabel = unitLabel;
        this.status = status;
        this.availableFrom = availableFrom;
        this.availableUntil = availableUntil;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public void updateDetails(
            String name, String description, String status, Instant now) {
        this.name = name;
        this.description = description;
        this.status = status;
        this.updatedAt = now;
    }

    public UUID getId() { return id; }
    public UUID getCategoryId() { return categoryId; }
    public String getSku() { return sku; }
    public String getName() { return name; }
    public String getDescription() { return description; }
    public String getImageUrl() { return imageUrl; }
    public String getUnitLabel() { return unitLabel; }
    public String getStatus() { return status; }
    public Instant getAvailableFrom() { return availableFrom; }
    public Instant getAvailableUntil() { return availableUntil; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
