package com.flowerchain.catalog.api;

import java.math.BigDecimal;
import java.util.UUID;

public record ProductSnapshotForBranch(
        UUID productId,
        UUID categoryId,
        UUID branchId,
        String sku,
        String productName,
        String description,
        String imageUrl,
        String unitLabel,
        BigDecimal unitPrice,
        String currencyCode,
        boolean sellable) {}
