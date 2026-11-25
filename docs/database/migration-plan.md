# Flyway Migration Plan and Verification Strategy

- **Status:** PROPOSED (awaiting owner approval at Phase 5 gate before P07)
- **Last updated:** 2026-11-24
- **Owner:** database-engineer (`database-design` skill) · **Task:** TASK-012 (Phase 5 — P06 Database and API Design)
- **Sources:** `CLAUDE.md` §6, §9, `DEC-004`, `DEC-015`, `DEC-018` (`ADR-0003`).

---

## 1. Flyway Migration Rules (`CLAUDE.md` §6)

1. **Location:** All SQL migrations live in `backend/src/main/resources/db/migration/`.
2. **Naming Convention:** `V<YYYYMMDDHHmm>__<short_description>.sql` (using `snake_case` description and double underscore `__`).
3. **Hibernate Validation Only:** `spring.jpa.hibernate.ddl-auto=validate`. Hibernate never creates or alters tables.
4. **Immutability:** Once a migration is merged, it is **never** edited, renamed, or deleted. Any schema change uses a new forward migration file.
5. **Safety Rule:** Never run migrations, DDL, or data changes against a shared, staging, or production database from a developer machine (`CLAUDE.md` §6, `.claude/settings.json`).

---

## 2. Planned Flyway Migration Sequence (Phase 7 Execution Order)

Migrations are ordered by topological module dependencies (`L0` → `L7`) so foreign keys always reference tables created in an earlier or same migration:

| Order | Migration File Name | Owning Module(s) | Tables Created & Key Constraints |
|---|---|---|---|
| **1** | `V202611240100__create_shared_and_branch_tables.sql` | `shared`, `branch` | `idempotency_records` (`uq_idempotency_key_scope`), `branches` (`uq_branches_code`, coordinate & radius `CHECK` constraints), `system_settings` (`uq_system_settings_key`). |
| **2** | `V202611240200__create_audit_logs_table.sql` | `audit` | `audit_logs` (immutable audit table with `branch_id` FK to `branches`, category `CHECK` constraint, and `idx_audit_logs_branch_cat_created`). |
| **3** | `V202611240300__create_identity_and_customer_tables.sql` | `identity`, `customer` | `staff_users` (`uq_staff_users_email`), `staff_user_roles`, `staff_branch_assignments` (`branch_id NOT NULL` FK to `branches`, `REQ-011`), `staff_refresh_sessions`, `customers` (`uq_customers_email`), `customer_addresses`. |
| **4** | `V202611240400__create_catalog_and_branch_prices_tables.sql` | `catalog` | `product_categories` (`uq_product_categories_slug`), `products` (`uq_products_sku`, `REQ-003`), `branch_product_prices` (`branch_id NOT NULL` FK, `uq_branch_product_price`, `CHECK (unit_price >= 0)`, `REQ-004`). |
| **5** | `V202611240500__create_inventory_and_transfers_tables.sql` | `inventory` | `branch_inventories` (`branch_id NOT NULL` FK `REQ-002`/`REQ-016`, `uq_branch_inventory_branch_product`, **`CHECK (available_stock >= 0)` `REQ-007`**, `CHECK (reserved_stock >= 0)`), `inventory_movements` (`branch_id NOT NULL` FK, `CHECK (quantity_delta <> 0)`, `REQ-008`), `goods_receipts`, `goods_receipt_lines`, `stock_adjustments`, `stock_transfers` (`CHECK (source_branch_id <> target_branch_id)`, `REQ-015`), `stock_transfer_lines`. |
| **6** | `V202611240600__create_orders_and_deliveries_tables.sql` | `order`, `delivery` | `orders` (**`processing_branch_id NOT NULL` FK `REQ-005`**, `fulfilment_type` `CHECK ('DELIVERY', 'PICKUP')` `REQ-013`, monetary `CHECK (>= 0)`), `order_lines` (**snapshot columns `sku_snapshot`, `product_name_snapshot`, `unit_price_snapshot`, `line_total_snapshot` `REQ-006`**), `order_status_history`, `order_returns`, `order_deliveries`. |
| **7** | `V202611240700__create_payments_webhooks_and_refunds_tables.sql` | `payment` | `payments` (**`uq_payments_provider_transaction_id UNIQUE (provider, provider_transaction_id)` `REQ-010`**), `payment_webhook_events` (**`uq_payment_webhook_provider_event_id UNIQUE (provider, provider_event_id)` `REQ-010`**, **`CHECK (signature_verified = true)` `REQ-009`**), `payment_refunds` (`CHECK (refund_amount > 0)`). |
| **8** | `V202611240800__create_notifications_table.sql` | `notification` | `notifications` (`branch_id` FK for branch-scoped staff alerts, `customer_id` FK for customer alerts, `FR-033`). |
| **9** *(Conditional on `B6`)* | `V202611240900__create_promotions_vouchers_table.sql` | `promotion` | Added only if owner includes promotional vouchers under `B6` (`FR-032`). |

---

## 3. Automated Migration & Constraint Verification Plan (`DEC-015`)

Per `CLAUDE.md` §9 and `DEC-015` (Testcontainers PostgreSQL 16 running in GitHub Actions CI, never H2):

1. **Clean Schema Bootstrap Test (`FlywayMigrationIT`):**
   - Starts a disposable `postgres:16-alpine` Testcontainers instance from an empty database.
   - Runs all Flyway migrations (`V202611240100`..`V202611240800`) and verifies Hibernate `ddl-auto=validate` succeeds with zero schema mismatches.
2. **Database Constraint Enforcement Tests (`DatabaseConstraintsIT`):**
   - **`TEST-002` (`REQ-002`, `REQ-016`):** Verifies inserting into `branch_inventories` with `branch_id = NULL` or a non-existent `branch_id` fails with a database constraint error.
   - **`TEST-006` (`REQ-005`):** Verifies inserting into `orders` with `processing_branch_id = NULL` fails.
   - **`TEST-009` (`REQ-007`):** Verifies updating `branch_inventories.available_stock` to `-1` fails with `chk_branch_inventories_available_non_negative`.
   - **`TEST-012` (`REQ-010`):** Verifies inserting duplicate `(provider, provider_event_id)` into `payment_webhook_events` or duplicate `(provider, provider_transaction_id)` into `payments` fails with `uq_payment_webhook_provider_event_id` / `uq_payments_provider_transaction_id`.
