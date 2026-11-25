package com.flowerchain.inventory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.flowerchain.support.PostgresIntegrationTestSupport;
import com.flowerchain.support.TestData;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

/**
 * TEST-009 (REQ-007): PostgreSQL itself refuses negative stock, even for SQL that bypasses the application.
 * CI only (Docker).
 */
class InventoryStockConstraintIT extends PostgresIntegrationTestSupport {

    @Test
    void directUpdateToNegativeAvailableStockIsRejectedByTheCheckConstraint() {
        UUID branch = data.branch("Constraint Branch");
        UUID product = data.product(TestData.unique("SKU"), "Orchid");
        data.stock(branch, product, 5);

        assertThatThrownBy(() -> jdbc.update(
                        "UPDATE branch_inventories SET available_stock = -1 WHERE branch_id = ? AND product_id = ?",
                        branch, product))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasStackTraceContaining("chk_branch_inventories_available_non_negative");

        assertThat(data.availableStock(branch, product)).isEqualTo(5);
    }

    @Test
    void directDecrementBelowZeroIsRejected() {
        UUID branch = data.branch("Constraint Branch");
        UUID product = data.product(TestData.unique("SKU"), "Lily");
        data.stock(branch, product, 1);

        assertThatThrownBy(() -> jdbc.update(
                        "UPDATE branch_inventories SET available_stock = available_stock - 2 "
                                + "WHERE branch_id = ? AND product_id = ?",
                        branch, product))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasStackTraceContaining("chk_branch_inventories_available_non_negative");

        assertThat(data.availableStock(branch, product)).isEqualTo(1);
    }

    @Test
    void insertingNegativeAvailableOrReservedStockIsRejected() {
        UUID branch = data.branch("Constraint Branch");
        UUID product = data.product(TestData.unique("SKU"), "Peony");
        UUID otherProduct = data.product(TestData.unique("SKU"), "Daisy");

        assertThatThrownBy(() -> data.stock(branch, product, -1))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasStackTraceContaining("chk_branch_inventories_available_non_negative");
        assertThatThrownBy(() -> jdbc.update(
                        "INSERT INTO branch_inventories (id, branch_id, product_id, available_stock, reserved_stock) "
                                + "VALUES (?, ?, ?, 0, -1)",
                        UUID.randomUUID(), branch, otherProduct))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasStackTraceContaining("chk_branch_inventories_reserved_non_negative");

        assertThat(jdbc.queryForObject(
                        "SELECT count(*) FROM branch_inventories WHERE branch_id = ?", Integer.class, branch))
                .isZero();
    }
}
