package com.flowerchain.branch.internal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "branches")
public class BranchEntity {

    @Id
    private UUID id;

    @Column(name = "code", nullable = false, unique = true, length = 32)
    private String code;

    @Column(name = "name", nullable = false, length = 150)
    private String name;

    @Column(name = "street_address", nullable = false, length = 255)
    private String streetAddress;

    @Column(name = "district", nullable = false, length = 100)
    private String district;

    @Column(name = "city", nullable = false, length = 100)
    private String city;

    @Column(name = "phone", nullable = false, length = 32)
    private String phone;

    @Column(name = "latitude", nullable = false, precision = 9, scale = 6)
    private BigDecimal latitude;

    @Column(name = "longitude", nullable = false, precision = 9, scale = 6)
    private BigDecimal longitude;

    @Column(name = "max_delivery_radius_km", nullable = false, precision = 6, scale = 2)
    private BigDecimal maxDeliveryRadiusKm;

    @Column(name = "operating_hours", nullable = false, length = 128)
    private String operatingHours;

    @Column(name = "status", nullable = false, length = 16)
    private String status;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected BranchEntity() {}

    public BranchEntity(
            UUID id,
            String code,
            String name,
            String streetAddress,
            String district,
            String city,
            String phone,
            BigDecimal latitude,
            BigDecimal longitude,
            BigDecimal maxDeliveryRadiusKm,
            String operatingHours,
            String status,
            Instant now) {
        this.id = id;
        this.code = code;
        this.name = name;
        this.streetAddress = streetAddress;
        this.district = district;
        this.city = city;
        this.phone = phone;
        this.latitude = latitude;
        this.longitude = longitude;
        this.maxDeliveryRadiusKm = maxDeliveryRadiusKm;
        this.operatingHours = operatingHours;
        this.status = status;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public void update(
            String code,
            String name,
            String streetAddress,
            String district,
            String city,
            String phone,
            BigDecimal latitude,
            BigDecimal longitude,
            BigDecimal maxDeliveryRadiusKm,
            String operatingHours,
            String status,
            Instant now) {
        this.code = code;
        this.name = name;
        this.streetAddress = streetAddress;
        this.district = district;
        this.city = city;
        this.phone = phone;
        this.latitude = latitude;
        this.longitude = longitude;
        this.maxDeliveryRadiusKm = maxDeliveryRadiusKm;
        this.operatingHours = operatingHours;
        this.status = status;
        this.updatedAt = now;
    }

    public UUID getId() { return id; }
    public String getCode() { return code; }
    public String getName() { return name; }
    public String getStreetAddress() { return streetAddress; }
    public String getDistrict() { return district; }
    public String getCity() { return city; }
    public String getPhone() { return phone; }
    public BigDecimal getLatitude() { return latitude; }
    public BigDecimal getLongitude() { return longitude; }
    public BigDecimal getMaxDeliveryRadiusKm() { return maxDeliveryRadiusKm; }
    public String getOperatingHours() { return operatingHours; }
    public String getStatus() { return status; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
