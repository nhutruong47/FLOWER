package com.flowerchain.catalog.internal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "product_categories")
public class ProductCategoryEntity {

    @Id
    private UUID id;

    @Column(name = "slug", nullable = false, unique = true, length = 100)
    private String slug;

    @Column(name = "name", nullable = false, length = 150)
    private String name;

    @Column(name = "description", length = 500)
    private String description;

    @Column(name = "display_order", nullable = false)
    private int displayOrder;

    @Column(name = "status", nullable = false, length = 16)
    private String status;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected ProductCategoryEntity() {}

    public ProductCategoryEntity(
            UUID id, String slug, String name, String description, int displayOrder, String status, Instant now) {
        this.id = id;
        this.slug = slug;
        this.name = name;
        this.description = description;
        this.displayOrder = displayOrder;
        this.status = status;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public UUID getId() { return id; }
    public String getSlug() { return slug; }
    public String getName() { return name; }
    public String getDescription() { return description; }
    public int getDisplayOrder() { return displayOrder; }
    public String getStatus() { return status; }
}
