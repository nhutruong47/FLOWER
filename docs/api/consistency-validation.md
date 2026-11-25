# End-to-End Consistency Validation (Requirements ↔ Architecture ↔ Database ↔ API)

- **Status:** COMPLETED (Phase 5 — P06 Consistency Audit)
- **Last updated:** 2026-11-24
- **Owner:** solution-architect & database-engineer · **Task:** TASK-012 (Phase 5 — P06 Database and API Design)

---

## 1. Cross-Artifact Consistency Matrix

Every baseline business rule (`REQ-001`..`017`) and functional requirement (`FR-001`..`038`) has been verified for 1:1 alignment across the 4 engineering layers:

| Requirement | Owning Architecture Module (`docs/architecture/module-map.md`) | Database Tables & Constraints (`docs/database/`) | OpenAPI Contract Endpoints (`docs/api/openapi.yaml`) | Verification Test (`docs/testing/TEST_CASES.md`) | Alignment Verdict |
|---|---|---|---|---|---|
| **REQ-001** (5–30 branches, 1 business) | `branch` (`L2`) | `branches` (`uq_branches_code`), `system_settings` | `GET /storefront/branches`, `GET/POST/PUT /admin/branches` | `TEST-001` | **PASS** |
| **REQ-002** & **REQ-016** (Branch-owned inventory, no central warehouse) | `inventory` (`L4`) | `branch_inventories` (`branch_id NOT NULL` FK to `branches(id)`), `goods_receipts` (`branch_id NOT NULL`) | `GET /admin/branches/{branchId}/inventory`, `POST /admin/branches/{branchId}/goods-receipts` | `TEST-002` | **PASS** |
| **REQ-003** (Central product catalog) | `catalog` (`L3`) | `product_categories`, `products` (`uq_products_sku`) | `GET /storefront/categories`, `GET/POST /admin/products` (POST restricted to Head Office) | `TEST-003` | **PASS** |
| **REQ-004** & **REQ-017** (Per-branch pricing & storefront price display) | `catalog` (`L3`) | `branch_product_prices` (`branch_id NOT NULL` FK, `uq_branch_product_price`, `CHECK (unit_price >= 0)`), `audit_logs` (`PRICE_CHANGE`) | `GET /storefront/branches/{branchId}/products`, `PUT /admin/branches/{branchId}/prices/{productId}`, `POST /storefront/orders/quote` | `TEST-004`, `TEST-005`, `TEST-019` | **PASS** |
| **REQ-005** (Traceable processing branch on every order) | `order` (`L5`) | `orders` (`processing_branch_id NOT NULL` FK to `branches(id)`), `order_status_history` | `POST /storefront/orders`, `GET /admin/orders`, `POST /admin/orders/{orderId}/reassign-branch` | `TEST-006` | **PASS** |
| **REQ-006** (Order product & price snapshots) | `order` (`L5`) | `order_lines` (`sku_snapshot NOT NULL`, `product_name_snapshot NOT NULL`, `unit_price_snapshot numeric(19,2) NOT NULL`, `line_total_snapshot`) | `OrderLineSnapshotResponse` in `GET /storefront/orders/{orderId}` & `GET /admin/orders/{orderId}` | `TEST-007` | **PASS** |
| **REQ-007** (Available inventory never negative) | `inventory` (`L4`) | `branch_inventories` (`CHECK (available_stock >= 0)`, `CHECK (reserved_stock >= 0)`, `version` + sorted `SELECT ... FOR UPDATE`) | `409 Conflict` (`INVENTORY_INSUFFICIENT_STOCK`) on `/storefront/orders`, `/stock-adjustments`, `/stock-transfers/{id}/dispatch` | `TEST-008`, `TEST-009` | **PASS** |
| **REQ-008** (Every inventory change auditable) | `inventory` (`L4`) | `inventory_movements` (`branch_id NOT NULL`, `reason_code`, `quantity_delta <> 0`, `available_after`, `source_document_type/id`, `actor_id`, `created_at`) | `GET /admin/branches/{branchId}/inventory/movements` | `TEST-010` | **PASS** |
| **REQ-009** (Server-side payment webhook verification) | `payment` (`L6`) | `payment_webhook_events` (`CHECK (signature_verified = true)`) | `POST /webhooks/payments/{provider}` requiring `X-Payment-Signature` header (`401` on invalid signature) | `TEST-011` | **PASS** |
| **REQ-010** (Same payment never processed twice) | `payment` (`L6`) & `shared` (`L0`) | `payment_webhook_events` (`UNIQUE (provider, provider_event_id)`), `payments` (`UNIQUE (provider, provider_transaction_id)`), `idempotency_records` | `POST /webhooks/payments/{provider}` (`WebhookAckResponse`), `Idempotency-Key` header on order & refund endpoints | `TEST-012` | **PASS** |
| **REQ-011** & **REQ-012** (Server-side branch authorization; untrusted client `branchId`) | `shared` (`BranchAuthorizationGuard`) & `identity` (`L3`) | `staff_branch_assignments` (`staff_user_id`, `branch_id NOT NULL`), `PlaceOrderRequest` ignores client `branchId` for `DELIVERY` | All `/api/v1/admin/**` endpoints enforce `BranchAuthorizationGuard`; `403` (`BRANCH_ACCESS_DENIED`) | `TEST-013`, `TEST-014` | **PASS** |
| **REQ-013** & **REQ-014** (Home delivery & pickup; automatic nearest branch with stock) | `order` (`L5`), `branch` (`L2`), `inventory` (`L4`), `delivery` (`L6`) | `orders` (`fulfilment_type IN ('DELIVERY', 'PICKUP')`), `branches` (`latitude`, `longitude`, `max_delivery_radius_km`), `order_deliveries` | `POST /storefront/orders/quote`, `POST /storefront/orders`, `POST /admin/orders/{orderId}/status` | `TEST-015`, `TEST-016`, `TEST-017` | **PASS** |
| **REQ-015** (Inter-branch stock transfers recorded) | `inventory` (`L4`) | `stock_transfers` (`source_branch_id NOT NULL`, `target_branch_id NOT NULL`, `CHECK (source <> target)`), `stock_transfer_lines` | `GET/POST /admin/stock-transfers`, `POST /admin/stock-transfers/{id}/dispatch`, `POST /admin/stock-transfers/{id}/receive` | `TEST-018` | **PASS** |

---

## 2. Verification of Zero Speculative Bloating

- **No speculative tables:** Eliminated separate `organizations`, `employees`, `product_variants`, `suppliers`, `purchase_orders`, and `stock_reservations` join tables where simpler 1:1 or in-row representations satisfy the approved requirements (`docs/database/erd.md` §1).
- **No speculative endpoints:** Every endpoint in `docs/api/openapi.yaml` maps directly to a documented `FR-xxx` and `REQ-xxx`.
- **No cyclic module dependencies:** Database foreign keys and API operations strictly follow the `L0` → `L7` topological module order from `docs/architecture/module-map.md`.
