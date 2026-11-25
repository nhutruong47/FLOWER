package com.flowerchain.support;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * Inserts integration-test fixtures directly with SQL. Codes, SKUs and emails get a random suffix so tests sharing
 * one database never collide. Seeding through SQL keeps each test focused on the behaviour under test.
 */
public class TestData {

    private final JdbcTemplate jdbc;
    private final PasswordEncoder passwordEncoder;

    public TestData(JdbcTemplate jdbc, PasswordEncoder passwordEncoder) {
        this.jdbc = jdbc;
        this.passwordEncoder = passwordEncoder;
    }

    public static String unique(String prefix) {
        return prefix + "-" + UUID.randomUUID().toString().substring(0, 8);
    }

    public UUID branch(String name) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO branches (id, code, name, street_address, district, city, phone, latitude, longitude,
                                      max_delivery_radius_km, operating_hours, status)
                VALUES (?, ?, ?, '1 Flower St', 'District 1', 'Ho Chi Minh City', '0280000000', 10.776900, 106.700900,
                        15.00, '08:00-20:00', 'ACTIVE')
                """, id, unique("BR"), name);
        return id;
    }

    public UUID product(String sku, String name) {
        UUID categoryId = UUID.randomUUID();
        jdbc.update("INSERT INTO product_categories (id, slug, name) VALUES (?, ?, 'Roses')", categoryId, unique("roses"));
        UUID productId = UUID.randomUUID();
        jdbc.update("INSERT INTO products (id, category_id, sku, name) VALUES (?, ?, ?, ?)", productId, categoryId, sku, name);
        return productId;
    }

    public void branchPrice(UUID branchId, UUID productId, String unitPrice) {
        jdbc.update("""
                INSERT INTO branch_product_prices (id, branch_id, product_id, unit_price, currency_code, is_sellable)
                VALUES (?, ?, ?, ?, 'VND', true)
                """, UUID.randomUUID(), branchId, productId, new BigDecimal(unitPrice));
    }

    public void stock(UUID branchId, UUID productId, int available) {
        jdbc.update("""
                INSERT INTO branch_inventories (id, branch_id, product_id, available_stock, reserved_stock)
                VALUES (?, ?, ?, ?, 0)
                """, UUID.randomUUID(), branchId, productId, available);
    }

    public int availableStock(UUID branchId, UUID productId) {
        return jdbc.queryForObject(
                "SELECT available_stock FROM branch_inventories WHERE branch_id = ? AND product_id = ?",
                Integer.class, branchId, productId);
    }

    public int reservedStock(UUID branchId, UUID productId) {
        return jdbc.queryForObject(
                "SELECT reserved_stock FROM branch_inventories WHERE branch_id = ? AND product_id = ?",
                Integer.class, branchId, productId);
    }

    /** A staff row for foreign keys (goods receipts, transfers). It has no roles and cannot log in. */
    public UUID staffUser() {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO staff_users (id, email, password_hash, full_name, status)
                VALUES (?, ?, '{noop}not-used', 'Test Staff', 'ACTIVE')
                """, id, unique("staff") + "@flowerchain.test");
        return id;
    }

    public UUID customer(String email, String rawPassword, String status) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO customers (id, email, password_hash, full_name, phone, status)
                VALUES (?, ?, ?, 'Test Customer', '0901234567', ?)
                """, id, email, passwordEncoder.encode(rawPassword), status);
        return id;
    }

    public UUID customer() {
        return customer(unique("customer") + "@example.test", "Customer-Passw0rd", "ACTIVE");
    }

    /** An order with one line, inserted directly so its status and creation time can be chosen. */
    public UUID order(
            UUID customerId,
            UUID branchId,
            UUID productId,
            String fulfilmentType,
            String status,
            String unitPrice,
            int quantity,
            String deliveryFee,
            Instant createdAt) {
        UUID orderId = UUID.randomUUID();
        BigDecimal lineTotal = new BigDecimal(unitPrice).multiply(BigDecimal.valueOf(quantity));
        BigDecimal fee = new BigDecimal(deliveryFee);
        boolean delivery = "DELIVERY".equals(fulfilmentType);
        jdbc.update("""
                INSERT INTO orders (id, order_number, customer_id, processing_branch_id, fulfilment_type, status,
                                    payment_status, currency_code, items_subtotal, delivery_fee, discount_amount,
                                    total_amount, recipient_name, recipient_phone, delivery_street_address,
                                    delivery_district, delivery_city, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, 'PAID', 'VND', ?, ?, 0.00, ?, 'Recipient', '0901234567', ?, ?, ?, ?, ?)
                """,
                orderId, unique("ORD-T"), customerId, branchId, fulfilmentType, status, lineTotal, fee,
                lineTotal.add(fee), delivery ? "2 Flower St" : null, delivery ? "District 3" : null,
                delivery ? "Ho Chi Minh City" : null, utc(createdAt), utc(createdAt));
        jdbc.update("""
                INSERT INTO order_lines (id, order_id, product_id, sku_snapshot, product_name_snapshot,
                                         unit_price_snapshot, quantity, line_total_snapshot)
                VALUES (?, ?, ?, 'SKU-SNAPSHOT', 'Snapshot name', ?, ?, ?)
                """, UUID.randomUUID(), orderId, productId, new BigDecimal(unitPrice), quantity, lineTotal);
        return orderId;
    }

    private static OffsetDateTime utc(Instant instant) {
        return OffsetDateTime.ofInstant(instant, ZoneOffset.UTC);
    }
}
