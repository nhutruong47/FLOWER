package com.flowerchain.catalog.api;

import java.util.List;
import java.util.UUID;

public interface CatalogApi {

    record ProductSummaryDto(UUID productId, String sku, String name, String status) {}

    ProductSummaryDto getProductSummaryOrThrow(UUID productId);

    ProductSnapshotForBranch getSellableProductSnapshotForBranchOrThrow(UUID branchId, UUID productId);

    List<ProductSnapshotForBranch> listSellableBranchProducts(UUID branchId);
}
