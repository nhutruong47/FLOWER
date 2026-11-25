package com.flowerchain.inventory.web;

import com.flowerchain.branch.api.BranchApi;
import com.flowerchain.branch.api.BranchDto;
import com.flowerchain.inventory.api.InventoryApi.BranchInventoryItemDto;
import com.flowerchain.inventory.api.InventoryApi.InventoryMovementDto;
import com.flowerchain.inventory.application.InventoryService;
import com.flowerchain.inventory.application.InventoryService.GoodsReceiptDto;
import com.flowerchain.inventory.application.InventoryService.GoodsReceiptLineInput;
import com.flowerchain.inventory.application.InventoryService.StockAdjustmentDto;
import com.flowerchain.inventory.application.InventoryService.StockTransferDto;
import com.flowerchain.inventory.application.InventoryService.StockTransferLineInput;
import com.flowerchain.inventory.application.InventoryService.StorefrontProductViewDto;
import com.flowerchain.shared.api.BranchAuthorizationGuard;
import com.flowerchain.shared.api.FlowerChainException;
import com.flowerchain.shared.api.FlowerChainPrincipal;
import com.flowerchain.shared.api.PageResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class InventoryController {

    private final InventoryService inventoryService;
    private final BranchApi branchApi;
    private final BranchAuthorizationGuard branchGuard;

    public InventoryController(
            InventoryService inventoryService,
            BranchApi branchApi,
            BranchAuthorizationGuard branchGuard) {
        this.inventoryService = inventoryService;
        this.branchApi = branchApi;
        this.branchGuard = branchGuard;
    }

    public record GoodsReceiptLineRequest(@NotNull UUID productId, @Min(1) int receivedQuantity) {}

    public record CreateGoodsReceiptRequest(
            @NotBlank @Size(min = 2, max = 200) String supplierName,
            @Size(max = 100) String supplierReference,
            @Size(max = 500) String note,
            @NotEmpty List<@Valid GoodsReceiptLineRequest> lines) {}

    public record CreateStockAdjustmentRequest(
            @NotNull UUID productId,
            @NotBlank @Pattern(regexp = "SPOILAGE|DAMAGED|STOCKTAKE_ADJUSTMENT") String adjustmentType,
            int quantityDelta,
            @NotBlank @Size(min = 3, max = 500) String reasonNote) {}

    public record TransferRequestLine(@NotNull UUID productId, @Min(1) int requestedQuantity) {}

    public record CreateStockTransferRequest(
            @NotNull UUID sourceBranchId,
            @NotNull UUID targetBranchId,
            @Size(max = 500) String note,
            @NotEmpty List<@Valid TransferRequestLine> lines) {}

    public record TransferDispatchLine(@NotNull UUID productId, @Min(1) int dispatchedQuantity) {}

    public record DispatchStockTransferRequest(
            @Size(max = 500) String note,
            @NotEmpty List<@Valid TransferDispatchLine> lines) {}

    public record TransferReceiveLine(
            @NotNull UUID productId,
            @Min(0) int receivedQuantity,
            @Size(max = 500) String discrepancyNote) {}

    public record ReceiveStockTransferRequest(
            @Size(max = 500) String note,
            @NotEmpty List<@Valid TransferReceiveLine> lines) {}

    @GetMapping("/api/v1/storefront/branches/{branchId}/products")
    public PageResponse<StorefrontProductViewDto> listStorefrontBranchProducts(
            @PathVariable UUID branchId,
            @RequestParam(required = false) UUID categoryId,
            @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return inventoryService.listStorefrontBranchCatalogProducts(branchId, categoryId, q, page, size);
    }

    @GetMapping("/api/v1/admin/branches/{branchId}/inventory")
    public PageResponse<BranchInventoryItemDto> listBranchInventory(
            @PathVariable UUID branchId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        branchGuard.assertCanAccessBranch(branchId);
        return inventoryService.listBranchInventory(branchId, page, size);
    }

    @GetMapping("/api/v1/admin/branches/{branchId}/inventory/movements")
    public PageResponse<InventoryMovementDto> listBranchInventoryMovements(
            @PathVariable UUID branchId,
            @RequestParam(required = false) UUID productId,
            @RequestParam(required = false) String reasonCode,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        branchGuard.assertCanAccessBranch(branchId);
        return inventoryService.listBranchInventoryMovements(branchId, productId, reasonCode, page, size);
    }

    @PostMapping("/api/v1/admin/branches/{branchId}/goods-receipts")
    @ResponseStatus(HttpStatus.CREATED)
    public GoodsReceiptDto createGoodsReceipt(
            @PathVariable UUID branchId,
            @Valid @RequestBody CreateGoodsReceiptRequest request) {
        FlowerChainPrincipal principal = branchGuard.assertCanAccessBranch(branchId);
        branchGuard.requireAnyRole("SYSTEM_OWNER", "HEAD_OFFICE_ADMIN", "BRANCH_MANAGER", "INVENTORY_STAFF");
        List<GoodsReceiptLineInput> inputs = request.lines().stream()
                .map(l -> new GoodsReceiptLineInput(l.productId(), l.receivedQuantity()))
                .toList();
        return inventoryService.recordGoodsReceipt(
                branchId,
                request.supplierName(),
                request.supplierReference(),
                request.note(),
                inputs,
                principal.actorId());
    }

    @PostMapping("/api/v1/admin/branches/{branchId}/stock-adjustments")
    @ResponseStatus(HttpStatus.CREATED)
    public StockAdjustmentDto createStockAdjustment(
            @PathVariable UUID branchId,
            @Valid @RequestBody CreateStockAdjustmentRequest request) {
        FlowerChainPrincipal principal = branchGuard.assertCanAccessBranch(branchId);
        branchGuard.requireAnyRole("SYSTEM_OWNER", "HEAD_OFFICE_ADMIN", "BRANCH_MANAGER", "INVENTORY_STAFF");
        return inventoryService.recordStockAdjustment(
                branchId,
                request.productId(),
                request.adjustmentType(),
                request.quantityDelta(),
                request.reasonNote(),
                principal.actorId());
    }

    @GetMapping("/api/v1/admin/stock-transfers")
    public PageResponse<StockTransferDto> listStockTransfers(
            @RequestParam(required = false) UUID branchId,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Set<UUID> allBranchIds = branchApi.listActiveBranches().stream()
                .map(BranchDto::id)
                .collect(Collectors.toSet());
        Set<UUID> effectiveBranches = branchGuard.resolveEffectiveBranchFilter(Optional.ofNullable(branchId), allBranchIds);
        return inventoryService.listStockTransfers(effectiveBranches, status, page, size);
    }

    @PostMapping("/api/v1/admin/stock-transfers")
    @ResponseStatus(HttpStatus.CREATED)
    public StockTransferDto createStockTransfer(@Valid @RequestBody CreateStockTransferRequest request) {
        FlowerChainPrincipal principal = branchGuard.requireAnyRole(
                "SYSTEM_OWNER", "HEAD_OFFICE_ADMIN", "BRANCH_MANAGER", "INVENTORY_STAFF");
        if (!principal.canAccessBranch(request.sourceBranchId())
                && !principal.canAccessBranch(request.targetBranchId())) {
            throw FlowerChainException.forbidden(
                    "BRANCH_ACCESS_DENIED",
                    "You must be authorized for at least the source or target branch to request a stock transfer.");
        }
        List<StockTransferLineInput> lines = request.lines().stream()
                .map(l -> new StockTransferLineInput(l.productId(), l.requestedQuantity(), null))
                .toList();
        return inventoryService.createStockTransfer(
                request.sourceBranchId(),
                request.targetBranchId(),
                request.note(),
                lines,
                principal.actorId());
    }

    @PostMapping("/api/v1/admin/stock-transfers/{transferId}/dispatch")
    public StockTransferDto dispatchStockTransfer(
            @PathVariable UUID transferId,
            @Valid @RequestBody DispatchStockTransferRequest request) {
        FlowerChainPrincipal principal = branchGuard.requireAnyRole(
                "SYSTEM_OWNER", "HEAD_OFFICE_ADMIN", "BRANCH_MANAGER", "INVENTORY_STAFF");
        List<StockTransferLineInput> lines = request.lines().stream()
                .map(l -> new StockTransferLineInput(l.productId(), l.dispatchedQuantity(), null))
                .toList();
        return inventoryService.dispatchStockTransfer(
                transferId,
                request.note(),
                lines,
                principal.actorId(),
                principal.globalAccess(),
                principal.authorizedBranchIds());
    }

    @PostMapping("/api/v1/admin/stock-transfers/{transferId}/receive")
    public StockTransferDto receiveStockTransfer(
            @PathVariable UUID transferId,
            @Valid @RequestBody ReceiveStockTransferRequest request) {
        FlowerChainPrincipal principal = branchGuard.requireAnyRole(
                "SYSTEM_OWNER", "HEAD_OFFICE_ADMIN", "BRANCH_MANAGER", "INVENTORY_STAFF");
        List<StockTransferLineInput> lines = request.lines().stream()
                .map(l -> new StockTransferLineInput(l.productId(), l.receivedQuantity(), l.discrepancyNote()))
                .toList();
        return inventoryService.receiveStockTransfer(
                transferId,
                request.note(),
                lines,
                principal.actorId(),
                principal.globalAccess(),
                principal.authorizedBranchIds());
    }
}
