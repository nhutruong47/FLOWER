package com.flowerchain.branch.api;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record BranchDto(
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
        Instant createdAt,
        Instant updatedAt) {}
