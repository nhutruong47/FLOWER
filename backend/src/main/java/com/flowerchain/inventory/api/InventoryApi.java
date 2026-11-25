package com.flowerchain.inventory.api;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public interface InventoryApi {

    record BranchInventoryItemDto(
            UUID branchId,
            UUID productId,
            String sku,
            String productName,
            int availableStock,
            int reservedStock,
            Instant updatedAt) {}

    record InventoryMovementDto(
            UUID id,
            UUID branchId,
            UUID productId,
            String reasonCode,
            int quantityDelta,
            int availableAfter,
            int reservedAfter,
            String sourceDocumentType,
            UUID sourceDocumentId,
            UUID actorId,
            String actorType,
            String note,
            Instant createdAt) {}

    int getAvailableStock(UUID branchId, UUID productId);

    boolean hasSufficientStock(UUID branchId, Map<UUID, Integer> requiredQuantitiesByProduct);

    List<UUID> findBranchesWithSufficientStock(List<UUID> candidateBranchIds, Map<UUID, Integer> requiredQuantitiesByProduct);

    void reserveStockForOrder(
            UUID branchId,
            UUID orderId,
            Map<UUID, Integer> quantitiesByProduct,
            UUID actorId,
            String actorType,
            String note);

    void confirmOrderStockDeduction(
            UUID branchId,
            UUID orderId,
            Map<UUID, Integer> quantitiesByProduct,
            UUID actorId,
            String actorType,
            String note);

    void releaseOrderStock(
            UUID branchId,
            UUID orderId,
            Map<UUID, Integer> quantitiesByProduct,
            UUID actorId,
            String actorType,
            String note);

    List<BranchInventoryItemDto> listAllBranchInventoryItems(UUID branchId);

    List<InventoryMovementDto> listAllBranchMovements(UUID branchId);
}
