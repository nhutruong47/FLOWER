package com.flowerchain.inventory.application;

import com.flowerchain.branch.api.BranchApi;
import com.flowerchain.catalog.api.CatalogApi;
import com.flowerchain.catalog.api.CatalogApi.ProductSummaryDto;
import com.flowerchain.catalog.api.ProductSnapshotForBranch;
import com.flowerchain.inventory.api.InventoryApi;
import com.flowerchain.inventory.internal.BranchInventoryEntity;
import com.flowerchain.inventory.internal.BranchInventoryRepository;
import com.flowerchain.inventory.internal.GoodsReceiptEntity;
import com.flowerchain.inventory.internal.GoodsReceiptLineEntity;
import com.flowerchain.inventory.internal.GoodsReceiptLineRepository;
import com.flowerchain.inventory.internal.GoodsReceiptRepository;
import com.flowerchain.inventory.internal.InventoryMovementEntity;
import com.flowerchain.inventory.internal.InventoryMovementRepository;
import com.flowerchain.inventory.internal.StockAdjustmentEntity;
import com.flowerchain.inventory.internal.StockAdjustmentRepository;
import com.flowerchain.inventory.internal.StockTransferEntity;
import com.flowerchain.inventory.internal.StockTransferLineEntity;
import com.flowerchain.inventory.internal.StockTransferLineRepository;
import com.flowerchain.inventory.internal.StockTransferRepository;
import com.flowerchain.shared.api.FlowerChainException;
import com.flowerchain.shared.api.PageResponse;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class InventoryService implements InventoryApi {

    private static final AtomicLong DOCUMENT_SEQUENCE = new AtomicLong(1000L);

    private final BranchInventoryRepository inventoryRepository;
    private final InventoryMovementRepository movementRepository;
    private final GoodsReceiptRepository goodsReceiptRepository;
    private final GoodsReceiptLineRepository goodsReceiptLineRepository;
    private final StockAdjustmentRepository stockAdjustmentRepository;
    private final StockTransferRepository stockTransferRepository;
    private final StockTransferLineRepository stockTransferLineRepository;
    private final BranchApi branchApi;
    private final CatalogApi catalogApi;
    private final Clock clock;

    public InventoryService(
            BranchInventoryRepository inventoryRepository,
            InventoryMovementRepository movementRepository,
            GoodsReceiptRepository goodsReceiptRepository,
            GoodsReceiptLineRepository goodsReceiptLineRepository,
            StockAdjustmentRepository stockAdjustmentRepository,
            StockTransferRepository stockTransferRepository,
            StockTransferLineRepository stockTransferLineRepository,
            BranchApi branchApi,
            CatalogApi catalogApi,
            Clock clock) {
        this.inventoryRepository = inventoryRepository;
        this.movementRepository = movementRepository;
        this.goodsReceiptRepository = goodsReceiptRepository;
        this.goodsReceiptLineRepository = goodsReceiptLineRepository;
        this.stockAdjustmentRepository = stockAdjustmentRepository;
        this.stockTransferRepository = stockTransferRepository;
        this.stockTransferLineRepository = stockTransferLineRepository;
        this.branchApi = branchApi;
        this.catalogApi = catalogApi;
        this.clock = clock;
    }

    public record StorefrontProductViewDto(
            UUID productId,
            String sku,
            String name,
            String description,
            String imageUrl,
            String unitLabel,
            UUID categoryId,
            UUID branchId,
            String unitPrice,
            String currencyCode,
            int availableStock,
            boolean inStock) {}

    public record GoodsReceiptLineInput(UUID productId, int receivedQuantity) {}

    public record GoodsReceiptDto(
            UUID id,
            String receiptNumber,
            UUID branchId,
            String supplierName,
            String supplierReference,
            UUID receivedByStaffId,
            Instant receivedAt) {}

    public record StockAdjustmentDto(
            UUID id,
            UUID branchId,
            UUID productId,
            String adjustmentType,
            int quantityDelta,
            int availableStockAfter,
            Instant createdAt) {}

    public record StockTransferLineInput(UUID productId, int quantity, String discrepancyNote) {}

    public record StockTransferLineDto(
            UUID productId,
            int requestedQuantity,
            Integer dispatchedQuantity,
            Integer receivedQuantity,
            String discrepancyNote) {}

    public record StockTransferDto(
            UUID id,
            String transferNumber,
            UUID sourceBranchId,
            UUID targetBranchId,
            String status,
            List<StockTransferLineDto> lines,
            Instant createdAt) {}

    @Transactional(readOnly = true)
    public PageResponse<StorefrontProductViewDto> listStorefrontBranchCatalogProducts(
            UUID branchId, UUID categoryId, String query, int page, int size) {
        List<ProductSnapshotForBranch> snapshots = catalogApi.listSellableBranchProducts(branchId);
        String qLower = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        List<StorefrontProductViewDto> items = snapshots.stream()
                .filter(s -> categoryId == null || categoryId.equals(s.categoryId()))
                .filter(s -> qLower.isEmpty()
                        || s.productName().toLowerCase(Locale.ROOT).contains(qLower)
                        || s.sku().toLowerCase(Locale.ROOT).contains(qLower))
                .map(s -> {
                    int available = getAvailableStock(branchId, s.productId());
                    return new StorefrontProductViewDto(
                            s.productId(),
                            s.sku(),
                            s.productName(),
                            s.description(),
                            s.imageUrl(),
                            s.unitLabel(),
                            s.categoryId(),
                            s.branchId(),
                            s.unitPrice().toPlainString(),
                            s.currencyCode(),
                            available,
                            available > 0);
                })
                .toList();
        return PageResponse.of(items, page, size);
    }

    @Override
    @Transactional(readOnly = true)
    public int getAvailableStock(UUID branchId, UUID productId) {
        return inventoryRepository
                .findByBranchIdAndProductId(branchId, productId)
                .map(BranchInventoryEntity::getAvailableStock)
                .orElse(0);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean hasSufficientStock(UUID branchId, Map<UUID, Integer> requiredQuantitiesByProduct) {
        for (Map.Entry<UUID, Integer> entry : requiredQuantitiesByProduct.entrySet()) {
            int required = entry.getValue();
            if (required <= 0) {
                continue;
            }
            int available = getAvailableStock(branchId, entry.getKey());
            if (available < required) {
                return false;
            }
        }
        return true;
    }

    @Override
    @Transactional(readOnly = true)
    public List<UUID> findBranchesWithSufficientStock(
            List<UUID> candidateBranchIds, Map<UUID, Integer> requiredQuantitiesByProduct) {
        return candidateBranchIds.stream()
                .filter(branchId -> hasSufficientStock(branchId, requiredQuantitiesByProduct))
                .toList();
    }

    @Override
    @Transactional
    public void reserveStockForOrder(
            UUID branchId,
            UUID orderId,
            Map<UUID, Integer> quantitiesByProduct,
            UUID actorId,
            String actorType,
            String note) {
        Instant now = Instant.now(clock);
        List<UUID> sortedProducts = quantitiesByProduct.keySet().stream().sorted().toList();
        for (UUID productId : sortedProducts) {
            int qty = quantitiesByProduct.get(productId);
            if (qty <= 0) {
                throw FlowerChainException.badRequest("INVALID_QUANTITY", "Order item quantity must be positive.");
            }
            BranchInventoryEntity inv = inventoryRepository
                    .findByBranchIdAndProductIdForUpdate(branchId, productId)
                    .orElseThrow(() -> FlowerChainException.conflict(
                            "INVENTORY_INSUFFICIENT_STOCK",
                            "Insufficient branch inventory",
                            "Branch " + branchId + " has 0 available stock for product " + productId + "."));
            if (inv.getAvailableStock() < qty) {
                throw FlowerChainException.conflict(
                        "INVENTORY_INSUFFICIENT_STOCK",
                        "Insufficient branch inventory",
                        "Branch " + branchId + " only has " + inv.getAvailableStock()
                                + " available units for product " + productId + " (requested " + qty + ").");
            }
            inv.reserve(qty, now);
            inventoryRepository.save(inv);
            recordMovement(
                    branchId,
                    productId,
                    "ORDER_RESERVED",
                    -qty,
                    inv.getAvailableStock(),
                    inv.getReservedStock(),
                    "ORDER",
                    orderId,
                    actorId,
                    actorType,
                    note != null ? note : "Stock reserved for order",
                    now);
        }
    }

    @Override
    @Transactional
    public void confirmOrderStockDeduction(
            UUID branchId,
            UUID orderId,
            Map<UUID, Integer> quantitiesByProduct,
            UUID actorId,
            String actorType,
            String note) {
        Instant now = Instant.now(clock);
        List<UUID> sortedProducts = quantitiesByProduct.keySet().stream().sorted().toList();
        for (UUID productId : sortedProducts) {
            int qty = quantitiesByProduct.get(productId);
            BranchInventoryEntity inv = inventoryRepository
                    .findByBranchIdAndProductIdForUpdate(branchId, productId)
                    .orElseThrow(() -> FlowerChainException.conflict(
                            "INVENTORY_RESERVATION_MISSING",
                            "Missing stock reservation",
                            "No inventory row found for order stock confirmation."));
            if (inv.getReservedStock() < qty) {
                throw FlowerChainException.conflict(
                        "INVENTORY_RESERVATION_MISSING",
                        "Insufficient reserved stock",
                        "Reserved stock is less than order quantity for product " + productId + ".");
            }
            inv.confirmReservation(qty, now);
            inventoryRepository.save(inv);
            recordMovement(
                    branchId,
                    productId,
                    "ORDER_CONFIRMED",
                    -qty,
                    inv.getAvailableStock(),
                    inv.getReservedStock(),
                    "ORDER",
                    orderId,
                    actorId,
                    actorType,
                    note != null ? note : "Reserved stock permanently consumed for order",
                    now);
        }
    }

    @Override
    @Transactional
    public void releaseOrderStock(
            UUID branchId,
            UUID orderId,
            Map<UUID, Integer> quantitiesByProduct,
            UUID actorId,
            String actorType,
            String note) {
        Instant now = Instant.now(clock);
        List<UUID> sortedProducts = quantitiesByProduct.keySet().stream().sorted().toList();
        for (UUID productId : sortedProducts) {
            int qty = quantitiesByProduct.get(productId);
            BranchInventoryEntity inv = inventoryRepository
                    .findByBranchIdAndProductIdForUpdate(branchId, productId)
                    .orElseThrow(() -> FlowerChainException.conflict(
                            "INVENTORY_RESERVATION_MISSING",
                            "Missing stock reservation",
                            "No inventory row found for releasing order stock."));
            if (inv.getReservedStock() < qty) {
                throw FlowerChainException.conflict(
                        "INVENTORY_RESERVATION_MISSING",
                        "Insufficient reserved stock",
                        "Reserved stock is less than order quantity to release for product " + productId + ".");
            }
            inv.releaseReservation(qty, now);
            inventoryRepository.save(inv);
            recordMovement(
                    branchId,
                    productId,
                    "ORDER_RELEASED",
                    qty,
                    inv.getAvailableStock(),
                    inv.getReservedStock(),
                    "ORDER",
                    orderId,
                    actorId,
                    actorType,
                    note != null ? note : "Reserved stock released back to available stock",
                    now);
        }
    }

    @Transactional
    public GoodsReceiptDto recordGoodsReceipt(
            UUID branchId,
            String supplierName,
            String supplierReference,
            String note,
            List<GoodsReceiptLineInput> lines,
            UUID staffActorId) {
        branchApi.getBranchOrThrow(branchId);
        if (lines == null || lines.isEmpty()) {
            throw FlowerChainException.badRequest("EMPTY_RECEIPT_LINES", "At least one goods receipt line is required.");
        }
        Instant now = Instant.now(clock);
        UUID receiptId = UUID.randomUUID();
        String receiptNumber = "GR-" + DOCUMENT_SEQUENCE.incrementAndGet();
        GoodsReceiptEntity receipt = goodsReceiptRepository.save(new GoodsReceiptEntity(
                receiptId,
                branchId,
                receiptNumber,
                supplierName.trim(),
                supplierReference,
                staffActorId,
                note,
                now));

        List<GoodsReceiptLineInput> sortedLines = lines.stream()
                .sorted(Comparator.comparing(GoodsReceiptLineInput::productId))
                .toList();

        for (GoodsReceiptLineInput line : sortedLines) {
            if (line.receivedQuantity() <= 0) {
                throw FlowerChainException.badRequest("INVALID_QUANTITY", "Received quantity must be greater than zero.");
            }
            catalogApi.getProductSummaryOrThrow(line.productId());
            goodsReceiptLineRepository.save(new GoodsReceiptLineEntity(
                    UUID.randomUUID(), receiptId, line.productId(), line.receivedQuantity(), now));

            BranchInventoryEntity inv = lockOrCreateInventoryRow(branchId, line.productId(), now);
            inv.addAvailableStock(line.receivedQuantity(), now);
            BranchInventoryEntity savedInv = inventoryRepository.save(inv);

            recordMovement(
                    branchId,
                    line.productId(),
                    "GOODS_RECEIPT",
                    line.receivedQuantity(),
                    savedInv.getAvailableStock(),
                    savedInv.getReservedStock(),
                    "GOODS_RECEIPT",
                    receiptId,
                    staffActorId,
                    "STAFF",
                    note != null ? note : "Goods receipt " + receiptNumber,
                    now);
        }

        return new GoodsReceiptDto(
                receipt.getId(),
                receipt.getReceiptNumber(),
                receipt.getBranchId(),
                receipt.getSupplierName(),
                receipt.getSupplierReference(),
                receipt.getReceivedByStaffId(),
                receipt.getReceivedAt());
    }

    @Transactional
    public StockAdjustmentDto recordStockAdjustment(
            UUID branchId,
            UUID productId,
            String adjustmentType,
            int quantityDelta,
            String reasonNote,
            UUID staffActorId) {
        branchApi.getBranchOrThrow(branchId);
        catalogApi.getProductSummaryOrThrow(productId);
        if (quantityDelta == 0) {
            throw FlowerChainException.badRequest("INVALID_QUANTITY_DELTA", "Quantity delta must not be zero.");
        }
        String normalizedType = adjustmentType.trim().toUpperCase(Locale.ROOT);
        if (("SPOILAGE".equals(normalizedType) || "DAMAGED".equals(normalizedType)) && quantityDelta > 0) {
            throw FlowerChainException.badRequest(
                    "INVALID_ADJUSTMENT_DELTA",
                    "Spoilage and damaged flower adjustments must have a negative quantityDelta.");
        }

        Instant now = Instant.now(clock);
        BranchInventoryEntity inv = lockOrCreateInventoryRow(branchId, productId, now);
        if (inv.getAvailableStock() + quantityDelta < 0) {
            throw FlowerChainException.conflict(
                    "INVENTORY_INSUFFICIENT_STOCK",
                    "Cannot reduce stock below zero",
                    "Available stock (" + inv.getAvailableStock()
                            + ") cannot absorb adjustment of " + quantityDelta + " (REQ-007).");
        }
        inv.adjustAvailableStock(quantityDelta, now);
        BranchInventoryEntity savedInv = inventoryRepository.save(inv);

        UUID adjustmentId = UUID.randomUUID();
        StockAdjustmentEntity savedAdj = stockAdjustmentRepository.save(new StockAdjustmentEntity(
                adjustmentId,
                branchId,
                productId,
                normalizedType,
                quantityDelta,
                reasonNote.trim(),
                staffActorId,
                now));

        recordMovement(
                branchId,
                productId,
                normalizedType,
                quantityDelta,
                savedInv.getAvailableStock(),
                savedInv.getReservedStock(),
                "STOCK_ADJUSTMENT",
                adjustmentId,
                staffActorId,
                "STAFF",
                reasonNote.trim(),
                now);

        return new StockAdjustmentDto(
                savedAdj.getId(),
                savedAdj.getBranchId(),
                savedAdj.getProductId(),
                savedAdj.getAdjustmentType(),
                savedAdj.getQuantityDelta(),
                savedInv.getAvailableStock(),
                savedAdj.getCreatedAt());
    }

    @Transactional
    public StockTransferDto createStockTransfer(
            UUID sourceBranchId,
            UUID targetBranchId,
            String note,
            List<StockTransferLineInput> lines,
            UUID requestedByStaffId) {
        if (sourceBranchId.equals(targetBranchId)) {
            throw FlowerChainException.badRequest(
                    "INVALID_TRANSFER_BRANCHES", "Source branch and target branch must be different.");
        }
        branchApi.getBranchOrThrow(sourceBranchId);
        branchApi.getBranchOrThrow(targetBranchId);
        if (lines == null || lines.isEmpty()) {
            throw FlowerChainException.badRequest("EMPTY_TRANSFER_LINES", "At least one transfer line is required.");
        }

        Instant now = Instant.now(clock);
        UUID transferId = UUID.randomUUID();
        String transferNumber = "TRF-" + DOCUMENT_SEQUENCE.incrementAndGet();
        StockTransferEntity transfer = stockTransferRepository.save(new StockTransferEntity(
                transferId,
                transferNumber,
                sourceBranchId,
                targetBranchId,
                "REQUESTED",
                requestedByStaffId,
                note,
                now));

        List<StockTransferLineEntity> savedLines = new ArrayList<>();
        for (StockTransferLineInput line : lines) {
            if (line.quantity() <= 0) {
                throw FlowerChainException.badRequest("INVALID_QUANTITY", "Requested transfer quantity must be positive.");
            }
            catalogApi.getProductSummaryOrThrow(line.productId());
            savedLines.add(stockTransferLineRepository.save(new StockTransferLineEntity(
                    UUID.randomUUID(), transferId, line.productId(), line.quantity(), now)));
        }

        return toTransferDto(transfer, savedLines);
    }

    @Transactional
    public StockTransferDto dispatchStockTransfer(
            UUID transferId,
            String note,
            List<StockTransferLineInput> dispatchLines,
            UUID staffActorId,
            boolean callerHasGlobalAccess,
            Set<UUID> callerAuthorizedBranches) {
        StockTransferEntity transfer = stockTransferRepository
                .findById(transferId)
                .orElseThrow(() -> FlowerChainException.notFound("Stock transfer " + transferId + " not found."));
        assertBranchAuthorized(transfer.getSourceBranchId(), callerHasGlobalAccess, callerAuthorizedBranches);

        if (!"REQUESTED".equals(transfer.getStatus()) && !"APPROVED".equals(transfer.getStatus())) {
            throw FlowerChainException.conflict(
                    "INVALID_TRANSFER_STATE",
                    "Invalid transfer status",
                    "Stock transfer in status " + transfer.getStatus() + " cannot be dispatched.");
        }

        List<StockTransferLineEntity> existingLines =
                stockTransferLineRepository.findByStockTransferId(transferId);
        Map<UUID, Integer> dispatchQtyByProduct = dispatchLines.stream()
                .collect(java.util.stream.Collectors.toMap(
                        StockTransferLineInput::productId, StockTransferLineInput::quantity));

        Instant now = Instant.now(clock);
        List<StockTransferLineEntity> sortedLines = existingLines.stream()
                .sorted(Comparator.comparing(StockTransferLineEntity::getProductId))
                .toList();

        for (StockTransferLineEntity line : sortedLines) {
            Integer dispatchedQty = dispatchQtyByProduct.get(line.getProductId());
            if (dispatchedQty == null || dispatchedQty <= 0) {
                throw FlowerChainException.badRequest(
                        "INVALID_DISPATCH_QUANTITY",
                        "Dispatched quantity must be provided and positive for product " + line.getProductId() + ".");
            }
            BranchInventoryEntity sourceInv = inventoryRepository
                    .findByBranchIdAndProductIdForUpdate(transfer.getSourceBranchId(), line.getProductId())
                    .orElseThrow(() -> FlowerChainException.conflict(
                            "INVENTORY_INSUFFICIENT_STOCK",
                            "Insufficient source branch inventory",
                            "Source branch has 0 stock for product " + line.getProductId() + "."));
            if (sourceInv.getAvailableStock() < dispatchedQty) {
                throw FlowerChainException.conflict(
                        "INVENTORY_INSUFFICIENT_STOCK",
                        "Insufficient source branch inventory",
                        "Source branch only has " + sourceInv.getAvailableStock()
                                + " available units for product " + line.getProductId() + ".");
            }
            sourceInv.adjustAvailableStock(-dispatchedQty, now);
            inventoryRepository.save(sourceInv);

            line.recordDispatch(dispatchedQty, now);
            stockTransferLineRepository.save(line);

            recordMovement(
                    transfer.getSourceBranchId(),
                    line.getProductId(),
                    "TRANSFER_OUT",
                    -dispatchedQty,
                    sourceInv.getAvailableStock(),
                    sourceInv.getReservedStock(),
                    "STOCK_TRANSFER",
                    transfer.getId(),
                    staffActorId,
                    "STAFF",
                    note != null ? note : "Transfer dispatch " + transfer.getTransferNumber(),
                    now);
        }

        transfer.markDispatched(staffActorId, note, now);
        StockTransferEntity savedTransfer = stockTransferRepository.save(transfer);
        return toTransferDto(savedTransfer, sortedLines);
    }

    @Transactional
    public StockTransferDto receiveStockTransfer(
            UUID transferId,
            String note,
            List<StockTransferLineInput> receiveLines,
            UUID staffActorId,
            boolean callerHasGlobalAccess,
            Set<UUID> callerAuthorizedBranches) {
        StockTransferEntity transfer = stockTransferRepository
                .findById(transferId)
                .orElseThrow(() -> FlowerChainException.notFound("Stock transfer " + transferId + " not found."));
        assertBranchAuthorized(transfer.getTargetBranchId(), callerHasGlobalAccess, callerAuthorizedBranches);

        if (!"IN_TRANSIT".equals(transfer.getStatus())) {
            throw FlowerChainException.conflict(
                    "INVALID_TRANSFER_STATE",
                    "Invalid transfer status",
                    "Only IN_TRANSIT stock transfers can be received (current: " + transfer.getStatus() + ").");
        }

        List<StockTransferLineEntity> existingLines =
                stockTransferLineRepository.findByStockTransferId(transferId);
        Map<UUID, StockTransferLineInput> receiveInputByProduct = receiveLines.stream()
                .collect(java.util.stream.Collectors.toMap(
                        StockTransferLineInput::productId, item -> item));

        Instant now = Instant.now(clock);
        List<StockTransferLineEntity> sortedLines = existingLines.stream()
                .sorted(Comparator.comparing(StockTransferLineEntity::getProductId))
                .toList();

        for (StockTransferLineEntity line : sortedLines) {
            StockTransferLineInput input = receiveInputByProduct.get(line.getProductId());
            if (input == null || input.quantity() < 0) {
                throw FlowerChainException.badRequest(
                        "INVALID_RECEIVE_QUANTITY",
                        "Received quantity must be provided and non-negative for product " + line.getProductId() + ".");
            }
            int dispatched = line.getDispatchedQuantity() != null ? line.getDispatchedQuantity() : line.getRequestedQuantity();
            if (input.quantity() > dispatched) {
                throw FlowerChainException.badRequest(
                        "RECEIVE_EXCEEDS_DISPATCH",
                        "Received quantity (" + input.quantity() + ") cannot exceed dispatched quantity (" + dispatched + ").");
            }
            line.recordReceipt(input.quantity(), input.discrepancyNote(), now);
            stockTransferLineRepository.save(line);

            if (input.quantity() > 0) {
                BranchInventoryEntity targetInv =
                        lockOrCreateInventoryRow(transfer.getTargetBranchId(), line.getProductId(), now);
                targetInv.addAvailableStock(input.quantity(), now);
                BranchInventoryEntity savedTargetInv = inventoryRepository.save(targetInv);

                recordMovement(
                        transfer.getTargetBranchId(),
                        line.getProductId(),
                        "TRANSFER_IN",
                        input.quantity(),
                        savedTargetInv.getAvailableStock(),
                        savedTargetInv.getReservedStock(),
                        "STOCK_TRANSFER",
                        transfer.getId(),
                        staffActorId,
                        "STAFF",
                        input.discrepancyNote() != null
                                ? input.discrepancyNote()
                                : (note != null ? note : "Transfer receipt " + transfer.getTransferNumber()),
                        now);
            }
        }

        transfer.markCompleted(staffActorId, note, now);
        StockTransferEntity savedTransfer = stockTransferRepository.save(transfer);
        return toTransferDto(savedTransfer, sortedLines);
    }

    @Transactional(readOnly = true)
    public PageResponse<StockTransferDto> listStockTransfers(
            Set<UUID> effectiveBranches, String statusFilter, int page, int size) {
        List<StockTransferDto> items = stockTransferRepository.findAllByOrderByCreatedAtDesc().stream()
                .filter(t -> effectiveBranches.contains(t.getSourceBranchId())
                        || effectiveBranches.contains(t.getTargetBranchId()))
                .filter(t -> statusFilter == null || statusFilter.isBlank() || statusFilter.equalsIgnoreCase(t.getStatus()))
                .map(t -> toTransferDto(t, stockTransferLineRepository.findByStockTransferId(t.getId())))
                .toList();
        return PageResponse.of(items, page, size);
    }

    @Override
    @Transactional(readOnly = true)
    public List<BranchInventoryItemDto> listAllBranchInventoryItems(UUID branchId) {
        branchApi.getBranchOrThrow(branchId);
        return inventoryRepository.findByBranchIdOrderByUpdatedAtDesc(branchId).stream()
                .map(inv -> {
                    ProductSummaryDto prod = catalogApi.getProductSummaryOrThrow(inv.getProductId());
                    return new BranchInventoryItemDto(
                            inv.getBranchId(),
                            inv.getProductId(),
                            prod.sku(),
                            prod.name(),
                            inv.getAvailableStock(),
                            inv.getReservedStock(),
                            inv.getUpdatedAt());
                })
                .toList();
    }

    @Transactional(readOnly = true)
    public PageResponse<BranchInventoryItemDto> listBranchInventory(UUID branchId, int page, int size) {
        return PageResponse.of(listAllBranchInventoryItems(branchId), page, size);
    }

    @Override
    @Transactional(readOnly = true)
    public List<InventoryMovementDto> listAllBranchMovements(UUID branchId) {
        branchApi.getBranchOrThrow(branchId);
        return movementRepository.findByBranchIdOrderByCreatedAtDesc(branchId).stream()
                .map(m -> new InventoryMovementDto(
                        m.getId(),
                        m.getBranchId(),
                        m.getProductId(),
                        m.getReasonCode(),
                        m.getQuantityDelta(),
                        m.getAvailableAfter(),
                        m.getReservedAfter(),
                        m.getSourceDocumentType(),
                        m.getSourceDocumentId(),
                        m.getActorId(),
                        m.getActorType(),
                        m.getNote(),
                        m.getCreatedAt()))
                .toList();
    }

    @Transactional(readOnly = true)
    public PageResponse<InventoryMovementDto> listBranchInventoryMovements(
            UUID branchId, UUID productIdFilter, String reasonCodeFilter, int page, int size) {
        List<InventoryMovementDto> filtered = listAllBranchMovements(branchId).stream()
                .filter(m -> productIdFilter == null || productIdFilter.equals(m.productId()))
                .filter(m -> reasonCodeFilter == null
                        || reasonCodeFilter.isBlank()
                        || reasonCodeFilter.equalsIgnoreCase(m.reasonCode()))
                .toList();
        return PageResponse.of(filtered, page, size);
    }

    private BranchInventoryEntity lockOrCreateInventoryRow(UUID branchId, UUID productId, Instant now) {
        Optional<BranchInventoryEntity> locked =
                inventoryRepository.findByBranchIdAndProductIdForUpdate(branchId, productId);
        return locked.orElseGet(() -> inventoryRepository.save(
                new BranchInventoryEntity(UUID.randomUUID(), branchId, productId, 0, 0, now)));
    }

    private void recordMovement(
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
            Instant now) {
        movementRepository.save(new InventoryMovementEntity(
                UUID.randomUUID(),
                branchId,
                productId,
                reasonCode,
                quantityDelta,
                availableAfter,
                reservedAfter,
                sourceDocumentType,
                sourceDocumentId,
                actorId,
                actorType,
                note,
                now));
    }

    private void assertBranchAuthorized(
            UUID requiredBranchId, boolean callerHasGlobalAccess, Set<UUID> callerAuthorizedBranches) {
        if (!callerHasGlobalAccess && (callerAuthorizedBranches == null || !callerAuthorizedBranches.contains(requiredBranchId))) {
            throw FlowerChainException.forbidden(
                    "BRANCH_ACCESS_DENIED",
                    "You are not authorized to act on branch " + requiredBranchId + ".");
        }
    }

    private StockTransferDto toTransferDto(StockTransferEntity entity, List<StockTransferLineEntity> lines) {
        List<StockTransferLineDto> lineDtos = lines.stream()
                .map(l -> new StockTransferLineDto(
                        l.getProductId(),
                        l.getRequestedQuantity(),
                        l.getDispatchedQuantity(),
                        l.getReceivedQuantity(),
                        l.getDiscrepancyNote()))
                .toList();
        return new StockTransferDto(
                entity.getId(),
                entity.getTransferNumber(),
                entity.getSourceBranchId(),
                entity.getTargetBranchId(),
                entity.getStatus(),
                lineDtos,
                entity.getCreatedAt());
    }
}
