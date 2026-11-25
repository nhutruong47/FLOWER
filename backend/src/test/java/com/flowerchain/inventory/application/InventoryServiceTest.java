package com.flowerchain.inventory.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.AdditionalAnswers.returnsFirstArg;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.flowerchain.branch.api.BranchApi;
import com.flowerchain.catalog.api.CatalogApi;
import com.flowerchain.inventory.application.InventoryService.GoodsReceiptDto;
import com.flowerchain.inventory.application.InventoryService.GoodsReceiptLineInput;
import com.flowerchain.inventory.application.InventoryService.StockAdjustmentDto;
import com.flowerchain.inventory.application.InventoryService.StockTransferDto;
import com.flowerchain.inventory.application.InventoryService.StockTransferLineInput;
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
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

/**
 * Every stock change writes an {@code inventory_movements} row with branch, product, reason, quantity delta,
 * after-values, source document, actor and UTC timestamp (REQ-008, TEST-010 at unit level). Also covers the
 * sequence-based GR-/TRF- numbers (TASK-018 defect 1) and branch checks on transfers (REQ-011, TEST-013).
 */
@ExtendWith(MockitoExtension.class)
class InventoryServiceTest {

    private static final Instant NOW = Instant.parse("2026-11-25T08:00:00Z");
    private static final UUID BRANCH_A = UUID.randomUUID();
    private static final UUID BRANCH_B = UUID.randomUUID();
    private static final UUID PRODUCT = UUID.randomUUID();
    private static final UUID STAFF = UUID.randomUUID();
    private static final UUID CUSTOMER = UUID.randomUUID();

    @Mock private BranchInventoryRepository inventoryRepository;
    @Mock private InventoryMovementRepository movementRepository;
    @Mock private GoodsReceiptRepository goodsReceiptRepository;
    @Mock private GoodsReceiptLineRepository goodsReceiptLineRepository;
    @Mock private StockAdjustmentRepository stockAdjustmentRepository;
    @Mock private StockTransferRepository stockTransferRepository;
    @Mock private StockTransferLineRepository stockTransferLineRepository;
    @Mock private BranchApi branchApi;
    @Mock private CatalogApi catalogApi;

    @Captor private ArgumentCaptor<InventoryMovementEntity> movementCaptor;

    private InventoryService service;

    @BeforeEach
    void setUp() {
        service = new InventoryService(
                inventoryRepository,
                movementRepository,
                goodsReceiptRepository,
                goodsReceiptLineRepository,
                stockAdjustmentRepository,
                stockTransferRepository,
                stockTransferLineRepository,
                branchApi,
                catalogApi,
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private static BranchInventoryEntity stock(UUID branchId, int available, int reserved) {
        return new BranchInventoryEntity(UUID.randomUUID(), branchId, PRODUCT, available, reserved, NOW.minusSeconds(3600));
    }

    private void givenLockedStock(BranchInventoryEntity inv) {
        when(inventoryRepository.findByBranchIdAndProductIdForUpdate(inv.getBranchId(), PRODUCT))
                .thenReturn(Optional.of(inv));
    }

    private void givenSavesReturnTheirArgument() {
        when(inventoryRepository.save(any(BranchInventoryEntity.class))).then(returnsFirstArg());
    }

    private InventoryMovementEntity singleRecordedMovement() {
        verify(movementRepository).save(movementCaptor.capture());
        return movementCaptor.getValue();
    }

    private static void assertMovement(
            InventoryMovementEntity movement,
            UUID branchId,
            String reasonCode,
            int quantityDelta,
            int availableAfter,
            int reservedAfter,
            String sourceDocumentType,
            UUID sourceDocumentId,
            UUID actorId,
            String actorType) {
        assertThat(movement.getId()).isNotNull();
        assertThat(movement.getBranchId()).isEqualTo(branchId);
        assertThat(movement.getProductId()).isEqualTo(PRODUCT);
        assertThat(movement.getReasonCode()).isEqualTo(reasonCode);
        assertThat(movement.getQuantityDelta()).isEqualTo(quantityDelta);
        assertThat(movement.getAvailableAfter()).isEqualTo(availableAfter);
        assertThat(movement.getReservedAfter()).isEqualTo(reservedAfter);
        assertThat(movement.getSourceDocumentType()).isEqualTo(sourceDocumentType);
        assertThat(movement.getSourceDocumentId()).isEqualTo(sourceDocumentId);
        assertThat(movement.getActorId()).isEqualTo(actorId);
        assertThat(movement.getActorType()).isEqualTo(actorType);
        assertThat(movement.getNote()).isNotBlank();
        assertThat(movement.getCreatedAt()).isEqualTo(NOW);
    }

    @Test
    void goodsReceiptAddsStockAndWritesAGoodsReceiptMovementWithASequenceNumber() {
        BranchInventoryEntity inv = stock(BRANCH_A, 0, 0);
        givenLockedStock(inv);
        givenSavesReturnTheirArgument();
        when(goodsReceiptRepository.nextReceiptNumber()).thenReturn(5001L);
        when(goodsReceiptRepository.save(any(GoodsReceiptEntity.class))).then(returnsFirstArg());

        GoodsReceiptDto receipt = service.recordGoodsReceipt(
                BRANCH_A, "Da Lat Farm", "INV-77", null, List.of(new GoodsReceiptLineInput(PRODUCT, 10)), STAFF);

        assertThat(receipt.receiptNumber()).isEqualTo("GR-5001");
        assertThat(inv.getAvailableStock()).isEqualTo(10);
        verify(goodsReceiptLineRepository).save(any(GoodsReceiptLineEntity.class));
        assertMovement(singleRecordedMovement(),
                BRANCH_A, "GOODS_RECEIPT", 10, 10, 0, "GOODS_RECEIPT", receipt.id(), STAFF, "STAFF");
    }

    @Test
    void goodsReceiptCreatesTheInventoryRowWhenTheBranchHasNoneYet() {
        when(inventoryRepository.findByBranchIdAndProductIdForUpdate(BRANCH_A, PRODUCT)).thenReturn(Optional.empty());
        givenSavesReturnTheirArgument();
        when(goodsReceiptRepository.nextReceiptNumber()).thenReturn(5002L);
        when(goodsReceiptRepository.save(any(GoodsReceiptEntity.class))).then(returnsFirstArg());

        GoodsReceiptDto receipt = service.recordGoodsReceipt(
                BRANCH_A, "Da Lat Farm", null, "first delivery", List.of(new GoodsReceiptLineInput(PRODUCT, 3)), STAFF);

        assertMovement(singleRecordedMovement(),
                BRANCH_A, "GOODS_RECEIPT", 3, 3, 0, "GOODS_RECEIPT", receipt.id(), STAFF, "STAFF");
    }

    @Test
    void orderReservationWritesAnOrderReservedMovement() {
        BranchInventoryEntity inv = stock(BRANCH_A, 10, 0);
        givenLockedStock(inv);
        givenSavesReturnTheirArgument();
        UUID orderId = UUID.randomUUID();

        service.reserveStockForOrder(BRANCH_A, orderId, Map.of(PRODUCT, 2), CUSTOMER, "CUSTOMER", "Reserved for order ORD-1");

        assertMovement(singleRecordedMovement(),
                BRANCH_A, "ORDER_RESERVED", -2, 8, 2, "ORDER", orderId, CUSTOMER, "CUSTOMER");
    }

    @Test
    void orderReservationBeyondAvailableStockIsRejectedWithoutAnyChange() {
        BranchInventoryEntity inv = stock(BRANCH_A, 1, 0);
        givenLockedStock(inv);

        assertThatThrownBy(() -> service.reserveStockForOrder(
                        BRANCH_A, UUID.randomUUID(), Map.of(PRODUCT, 2), CUSTOMER, "CUSTOMER", null))
                .isInstanceOfSatisfying(FlowerChainException.class, ex -> {
                    assertThat(ex.getStatus()).isEqualTo(HttpStatus.CONFLICT);
                    assertThat(ex.getErrorCode()).isEqualTo("INVENTORY_INSUFFICIENT_STOCK");
                });

        assertThat(inv.getAvailableStock()).isEqualTo(1);
        verify(inventoryRepository, never()).save(any());
        verifyNoInteractions(movementRepository);
    }

    @Test
    void orderReservationWithoutAnInventoryRowIsRejected() {
        when(inventoryRepository.findByBranchIdAndProductIdForUpdate(BRANCH_A, PRODUCT)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.reserveStockForOrder(
                        BRANCH_A, UUID.randomUUID(), Map.of(PRODUCT, 1), CUSTOMER, "CUSTOMER", null))
                .isInstanceOfSatisfying(FlowerChainException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo("INVENTORY_INSUFFICIENT_STOCK"));
        verifyNoInteractions(movementRepository);
    }

    @Test
    void releasingAReservationWritesAnOrderReleasedMovement() {
        BranchInventoryEntity inv = stock(BRANCH_A, 8, 2);
        givenLockedStock(inv);
        givenSavesReturnTheirArgument();
        UUID orderId = UUID.randomUUID();

        service.releaseOrderStock(BRANCH_A, orderId, Map.of(PRODUCT, 2), STAFF, "STAFF", "Order cancelled");

        assertMovement(singleRecordedMovement(),
                BRANCH_A, "ORDER_RELEASED", 2, 10, 0, "ORDER", orderId, STAFF, "STAFF");
    }

    @Test
    void confirmingAReservationWritesAnOrderConfirmedMovement() {
        BranchInventoryEntity inv = stock(BRANCH_A, 8, 2);
        givenLockedStock(inv);
        givenSavesReturnTheirArgument();
        UUID orderId = UUID.randomUUID();

        service.confirmOrderStockDeduction(BRANCH_A, orderId, Map.of(PRODUCT, 2), STAFF, "STAFF", null);

        assertMovement(singleRecordedMovement(),
                BRANCH_A, "ORDER_CONFIRMED", -2, 8, 0, "ORDER", orderId, STAFF, "STAFF");
    }

    @Test
    void spoilageAdjustmentWritesAMovementPointingAtTheAdjustment() {
        BranchInventoryEntity inv = stock(BRANCH_A, 10, 0);
        givenLockedStock(inv);
        givenSavesReturnTheirArgument();
        when(stockAdjustmentRepository.save(any(StockAdjustmentEntity.class))).then(returnsFirstArg());

        StockAdjustmentDto adjustment =
                service.recordStockAdjustment(BRANCH_A, PRODUCT, "SPOILAGE", -1, "Wilted roses", STAFF);

        assertThat(adjustment.availableStockAfter()).isEqualTo(9);
        assertMovement(singleRecordedMovement(),
                BRANCH_A, "SPOILAGE", -1, 9, 0, "STOCK_ADJUSTMENT", adjustment.id(), STAFF, "STAFF");
    }

    @Test
    void adjustmentThatWouldMakeStockNegativeIsRejectedWithoutAMovement() {
        BranchInventoryEntity inv = stock(BRANCH_A, 1, 0);
        givenLockedStock(inv);

        assertThatThrownBy(() -> service.recordStockAdjustment(BRANCH_A, PRODUCT, "DAMAGED", -2, "Crushed box", STAFF))
                .isInstanceOfSatisfying(FlowerChainException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo("INVENTORY_INSUFFICIENT_STOCK"));

        assertThat(inv.getAvailableStock()).isEqualTo(1);
        verifyNoInteractions(movementRepository, stockAdjustmentRepository);
    }

    @Test
    void availabilityChecksReadAPlainValueAndNeverLoadAManagedInventoryEntity() {
        when(inventoryRepository.findAvailableStock(BRANCH_A, PRODUCT)).thenReturn(Optional.of(3));
        when(inventoryRepository.findAvailableStock(BRANCH_B, PRODUCT)).thenReturn(Optional.empty());

        assertThat(service.hasSufficientStock(BRANCH_A, Map.of(PRODUCT, 3))).isTrue();
        assertThat(service.hasSufficientStock(BRANCH_A, Map.of(PRODUCT, 4))).isFalse();
        assertThat(service.getAvailableStock(BRANCH_B, PRODUCT)).isZero();
        assertThat(service.findBranchesWithSufficientStock(List.of(BRANCH_A, BRANCH_B), Map.of(PRODUCT, 1)))
                .containsExactly(BRANCH_A);

        verify(inventoryRepository, never()).findByBranchIdAndProductId(any(), any());
        verify(inventoryRepository, never()).findByBranchIdAndProductIdForUpdate(any(), any());
    }

    @Test
    void createdTransfersAreNumberedFromTheDatabaseSequence() {
        when(stockTransferRepository.nextTransferNumber()).thenReturn(7001L);
        when(stockTransferRepository.save(any(StockTransferEntity.class))).then(returnsFirstArg());
        when(stockTransferLineRepository.save(any(StockTransferLineEntity.class))).then(returnsFirstArg());

        StockTransferDto transfer = service.createStockTransfer(
                BRANCH_A, BRANCH_B, null, List.of(new StockTransferLineInput(PRODUCT, 3, null)), STAFF);

        assertThat(transfer.transferNumber()).isEqualTo("TRF-7001");
        assertThat(transfer.status()).isEqualTo("REQUESTED");
        verifyNoInteractions(movementRepository);
    }

    @Test
    void dispatchWritesATransferOutMovementAtTheSourceBranch() {
        StockTransferEntity transfer = requestedTransfer();
        when(stockTransferRepository.findById(transfer.getId())).thenReturn(Optional.of(transfer));
        when(stockTransferLineRepository.findByStockTransferId(transfer.getId()))
                .thenReturn(List.of(new StockTransferLineEntity(UUID.randomUUID(), transfer.getId(), PRODUCT, 3, NOW)));
        BranchInventoryEntity sourceInv = stock(BRANCH_A, 10, 0);
        givenLockedStock(sourceInv);
        givenSavesReturnTheirArgument();
        when(stockTransferRepository.save(any(StockTransferEntity.class))).then(returnsFirstArg());

        StockTransferDto dispatched = service.dispatchStockTransfer(
                transfer.getId(), null, List.of(new StockTransferLineInput(PRODUCT, 3, null)), STAFF, false, Set.of(BRANCH_A));

        assertThat(dispatched.status()).isEqualTo("IN_TRANSIT");
        assertMovement(singleRecordedMovement(),
                BRANCH_A, "TRANSFER_OUT", -3, 7, 0, "STOCK_TRANSFER", transfer.getId(), STAFF, "STAFF");
    }

    @Test
    void receiptWritesATransferInMovementAtTheTargetBranch() {
        StockTransferEntity transfer = requestedTransfer();
        transfer.markDispatched(STAFF, null, NOW);
        StockTransferLineEntity line = new StockTransferLineEntity(UUID.randomUUID(), transfer.getId(), PRODUCT, 3, NOW);
        line.recordDispatch(3, NOW);
        when(stockTransferRepository.findById(transfer.getId())).thenReturn(Optional.of(transfer));
        when(stockTransferLineRepository.findByStockTransferId(transfer.getId())).thenReturn(List.of(line));
        BranchInventoryEntity targetInv = stock(BRANCH_B, 2, 0);
        givenLockedStock(targetInv);
        givenSavesReturnTheirArgument();
        when(stockTransferRepository.save(any(StockTransferEntity.class))).then(returnsFirstArg());
        UUID receiver = UUID.randomUUID();

        StockTransferDto received = service.receiveStockTransfer(
                transfer.getId(), null, List.of(new StockTransferLineInput(PRODUCT, 3, null)), receiver, false, Set.of(BRANCH_B));

        assertThat(received.status()).isEqualTo("COMPLETED");
        assertMovement(singleRecordedMovement(),
                BRANCH_B, "TRANSFER_IN", 3, 5, 0, "STOCK_TRANSFER", transfer.getId(), receiver, "STAFF");
    }

    @Test
    void staffOfTheTargetBranchCannotDispatchFromTheSourceBranch() {
        StockTransferEntity transfer = requestedTransfer();
        when(stockTransferRepository.findById(transfer.getId())).thenReturn(Optional.of(transfer));

        assertThatThrownBy(() -> service.dispatchStockTransfer(
                        transfer.getId(), null, List.of(new StockTransferLineInput(PRODUCT, 3, null)), STAFF, false, Set.of(BRANCH_B)))
                .isInstanceOfSatisfying(FlowerChainException.class, ex -> {
                    assertThat(ex.getStatus()).isEqualTo(HttpStatus.FORBIDDEN);
                    assertThat(ex.getErrorCode()).isEqualTo("BRANCH_ACCESS_DENIED");
                });
        verifyNoInteractions(inventoryRepository, movementRepository);
    }

    @Test
    void staffOfTheSourceBranchCannotConfirmReceiptAtTheTargetBranch() {
        StockTransferEntity transfer = requestedTransfer();
        transfer.markDispatched(STAFF, null, NOW);
        when(stockTransferRepository.findById(transfer.getId())).thenReturn(Optional.of(transfer));

        assertThatThrownBy(() -> service.receiveStockTransfer(
                        transfer.getId(), null, List.of(new StockTransferLineInput(PRODUCT, 3, null)), STAFF, false, Set.of(BRANCH_A)))
                .isInstanceOfSatisfying(FlowerChainException.class,
                        ex -> assertThat(ex.getStatus()).isEqualTo(HttpStatus.FORBIDDEN));
        verifyNoInteractions(inventoryRepository, movementRepository);
    }

    private static StockTransferEntity requestedTransfer() {
        return new StockTransferEntity(UUID.randomUUID(), "TRF-1", BRANCH_A, BRANCH_B, "REQUESTED", STAFF, null, NOW);
    }
}
