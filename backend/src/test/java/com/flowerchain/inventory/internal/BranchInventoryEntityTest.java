package com.flowerchain.inventory.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Domain guards that keep available and reserved stock at or above zero (REQ-007). TEST-008 (unit level). */
class BranchInventoryEntityTest {

    private static final Instant CREATED = Instant.parse("2026-11-25T08:00:00Z");
    private static final Instant LATER = Instant.parse("2026-11-25T09:00:00Z");

    private static BranchInventoryEntity stock(int available, int reserved) {
        return new BranchInventoryEntity(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), available, reserved, CREATED);
    }

    @Test
    void reserveMovesUnitsFromAvailableToReserved() {
        BranchInventoryEntity inv = stock(5, 0);

        inv.reserve(2, LATER);

        assertThat(inv.getAvailableStock()).isEqualTo(3);
        assertThat(inv.getReservedStock()).isEqualTo(2);
        assertThat(inv.getUpdatedAt()).isEqualTo(LATER);
    }

    @Test
    void reservingTheLastUnitLeavesZeroAvailable() {
        BranchInventoryEntity inv = stock(1, 0);

        inv.reserve(1, LATER);

        assertThat(inv.getAvailableStock()).isZero();
        assertThat(inv.getReservedStock()).isEqualTo(1);
    }

    @Test
    void reservingMoreThanAvailableIsRejectedAndChangesNothing() {
        BranchInventoryEntity inv = stock(1, 0);
        inv.reserve(1, LATER);

        assertThatThrownBy(() -> inv.reserve(1, LATER)).isInstanceOf(IllegalStateException.class);

        assertThat(inv.getAvailableStock()).isZero();
        assertThat(inv.getReservedStock()).isEqualTo(1);
    }

    @Test
    void reserveRejectsZeroAndNegativeQuantities() {
        BranchInventoryEntity inv = stock(5, 0);

        assertThatThrownBy(() -> inv.reserve(0, LATER)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> inv.reserve(-1, LATER)).isInstanceOf(IllegalArgumentException.class);
        assertThat(inv.getAvailableStock()).isEqualTo(5);
        assertThat(inv.getReservedStock()).isZero();
    }

    @Test
    void confirmReservationConsumesReservedStockOnly() {
        BranchInventoryEntity inv = stock(3, 2);

        inv.confirmReservation(2, LATER);

        assertThat(inv.getAvailableStock()).isEqualTo(3);
        assertThat(inv.getReservedStock()).isZero();
    }

    @Test
    void confirmingMoreThanReservedIsRejected() {
        BranchInventoryEntity inv = stock(10, 1);

        assertThatThrownBy(() -> inv.confirmReservation(2, LATER)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> inv.confirmReservation(0, LATER)).isInstanceOf(IllegalArgumentException.class);
        assertThat(inv.getReservedStock()).isEqualTo(1);
    }

    @Test
    void releaseReservationReturnsUnitsToAvailable() {
        BranchInventoryEntity inv = stock(3, 2);

        inv.releaseReservation(2, LATER);

        assertThat(inv.getAvailableStock()).isEqualTo(5);
        assertThat(inv.getReservedStock()).isZero();
    }

    @Test
    void releasingMoreThanReservedIsRejected() {
        BranchInventoryEntity inv = stock(3, 1);

        assertThatThrownBy(() -> inv.releaseReservation(2, LATER)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> inv.releaseReservation(-1, LATER)).isInstanceOf(IllegalArgumentException.class);
        assertThat(inv.getAvailableStock()).isEqualTo(3);
        assertThat(inv.getReservedStock()).isEqualTo(1);
    }

    @Test
    void adjustAvailableStockAllowsReachingZeroButNeverBelow() {
        BranchInventoryEntity inv = stock(2, 0);

        inv.adjustAvailableStock(-2, LATER);
        assertThat(inv.getAvailableStock()).isZero();

        assertThatThrownBy(() -> inv.adjustAvailableStock(-1, LATER)).isInstanceOf(IllegalStateException.class);
        assertThat(inv.getAvailableStock()).isZero();
    }

    @Test
    void addAvailableStockRequiresAPositiveDelta() {
        BranchInventoryEntity inv = stock(0, 0);

        inv.addAvailableStock(4, LATER);
        assertThat(inv.getAvailableStock()).isEqualTo(4);

        assertThatThrownBy(() -> inv.addAvailableStock(0, LATER)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> inv.addAvailableStock(-3, LATER)).isInstanceOf(IllegalArgumentException.class);
        assertThat(inv.getAvailableStock()).isEqualTo(4);
    }
}
