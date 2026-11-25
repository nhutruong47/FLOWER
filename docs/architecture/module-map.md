# Module Map and Boundary Specification

- **Status:** PROPOSED (awaiting owner approval at Phase 4 gate before P06)
- **Last updated:** 2026-11-24
- **Owner:** solution-architect · **Task:** TASK-011 (Phase 4 — P05 Solution Architecture)
- **Sources:** `CLAUDE.md` §4, P04 `functional-requirements.md` (`FR-001`..`038`), P04 `traceability-matrices.md`.

---

## 1. Evaluation of the 13 Candidate Capability Areas

Per the P05 brief, all 13 candidate domain areas were evaluated against `CLAUDE.md` §4 (*"Prefer the simplest solution that meets the requirement"* and *"Do not create modules before the requirements behind them are approved"*):

| Candidate Area | Architectural Placement Decision | Rationale |
|---|---|---|
| 1. **Identity and Access** | Dedicated module: **`identity`** (`com.flowerchain.identity`) | Owns staff accounts, roles, staff-to-branch assignments (`REQ-011`), authentication (`FR-001`..`006`), and security principal resolution. |
| 2. **Organization and Branch** | Dedicated module: **`branch`** (`com.flowerchain.branch`) | Owns branches (`REQ-001`), coordinates/delivery zones (`REQ-014`), operating hours, and chain settings (`FR-007`..`009`, `FR-037`). |
| 3. **Catalog** | Dedicated module: **`catalog`** (`com.flowerchain.catalog`) | Owns central categories, products, SKUs (`REQ-003`), and per-branch product prices (`REQ-004`, `REQ-017`, `FR-010`..`014`). Grouping branch pricing with catalog keeps product + branch price queries cohesive. |
| 4. **Inventory** | Dedicated module: **`inventory`** (`com.flowerchain.inventory`) | Owns branch stock (`REQ-002`, `REQ-016`), non-negative stock locking (`REQ-007`), the immutable inventory audit ledger (`REQ-008`), reservations (`FR-017`), spoilage/adjustments (`FR-019`), and inter-branch stock transfers (`REQ-015`, `FR-018`). |
| 5. **Procurement (Goods Receiving)** | Sub-capability inside **`inventory`** (or conditional module if full Supplier PO is requested under `B19`) | In MVP (`FR-020`), there is no central warehouse (`REQ-016`) and branches record Goods Receipts (stock-in) directly into branch inventory (`+qty` audited). Keeping Goods Receipts inside `inventory` avoids a 1-table cross-module cycle with `inventory`. |
| 6. **Order** | Dedicated module: **`order`** (`com.flowerchain.order`) | Owns online orders, order line snapshots (`REQ-006`), nearest-branch allocation orchestration (`REQ-005`, `REQ-013`, `REQ-014`), order lifecycle status machine, cancellation, and exception branch reassignment (`FR-021`..`024`, `FR-030`, `FR-038`). |
| 7. **Payment** | Dedicated module: **`payment`** (`com.flowerchain.payment`) | Owns payment transactions, server-side webhook signature verification (`REQ-009`), idempotent payment event deduplication (`REQ-010`), and refunds (`FR-025`..`027`). |
| 8. **Delivery (& Pickup Fulfilment)** | Dedicated sub-component / module boundary **`delivery`** (`com.flowerchain.delivery`) or coordinated with `order` | Owns delivery dispatch assignments, delivery fee/zone calculation (`B3e`, `B15`), delivery hand-over tracking (`FR-028`), and pickup collection verification (`FR-029`). |
| 9. **Customer** | Dedicated module: **`customer`** (`com.flowerchain.customer`) | Owns storefront customer profiles and saved delivery addresses (`FR-002`, `FR-031`, `BRULE-027`). |
| 10. **Promotion** | Conditional module: **`promotion`** (`com.flowerchain.promotion`) — *Created only if owner approves vouchers under `B6` (`FR-032`)* | Isolates optional voucher codes and usage limits (`FR-032`) so core ordering stays clean if `B6` defers vouchers. |
| 11. **Notification** | Dedicated module: **`notification`** (`com.flowerchain.notification`) | Subscribes to domain events (`OrderPlacedEvent`, `OrderStatusChangedEvent`, `PaymentConfirmedEvent`) to persist in-app branch/customer alerts and dispatch outbound messages (`FR-033`). |
| 12. **Reporting** | Dedicated read-model module: **`reporting`** (`com.flowerchain.reporting`) | Provides branch-scoped and chain-wide revenue, order fulfilment, and inventory movement aggregations (`FR-034`, `FR-035`) via read-only queries/APIs without mutating operational tables. |
| 13. **Audit** | Dedicated foundational module: **`audit`** (`com.flowerchain.audit`) | Owns the cross-cutting immutable audit log for logins, role changes, price changes, order status changes, and refunds (`CLAUDE.md` §8, `FR-036`), while `inventory` owns the high-volume `inventory_movements` ledger (`REQ-008`) exposed to `audit`/`reporting` via `InventoryApi`. |

---

## 2. Detailed Module Specifications

| Module (`com.flowerchain.<module>`) | Responsibilities | Owned Database Tables | Public API (`<module>.api`) & Published Events | Allowed Dependencies | Forbidden Dependencies | Mapped `REQ` & `FR` |
|---|---|---|---|---|---|---|
| **`shared`** *(Technical kernel)* | Security context holder, `BranchAuthorizationGuard`, RFC 9457 `GlobalExceptionHandler`, pagination DTOs, idempotency filter, UTC `Clock`. | `idempotency_records` | `BranchScopeContext`, `BranchAuthorizationGuard`, `PageResponse<T>` | None (leaf package) | All business modules | `REQ-011`, `REQ-012`, `NFR-004`, `NFR-006`, `NFR-008` |
| **`audit`** | Persist and query immutable security, price, order-status, and refund audit entries (`CLAUDE.md` §8). | `audit_logs` | `AuditApi.record(AuditEntryCommand)`, `AuditApi.query(...)` | `shared` | All other modules (`branch`, `identity`, `catalog`, `inventory`, `order`, `payment`, etc.) | `FR-036`, `BRULE-019`, `BRULE-023`, `BRULE-025`, `BRULE-030`, `NFR-010` |
| **`branch`** | Manage branches (`5–30`), coordinates, delivery radius/zones, operating hours, and chain settings. | `branches`, `system_settings` | `BranchApi.getActiveBranch(id)`, `BranchApi.listActiveBranches()`, `BranchApi.findBranchesCoveringLocation(lat, lng)` | `shared`, `audit` | `identity`, `catalog`, `inventory`, `order`, `payment`, `delivery`, `customer`, `promotion`, `notification`, `reporting` | `REQ-001`, `REQ-014`, `FR-007`, `FR-008`, `FR-009`, `FR-037` |
| **`identity`** | Staff accounts, authentication, password hashing, RBAC roles, and staff-to-branch assignments. | `staff_users`, `staff_roles`, `staff_branch_assignments` | `IdentityApi.resolveStaffPrincipal(userId)`, `StaffAuthenticatedEvent`, `StaffRoleChangedEvent` | `shared`, `audit`, `branch` | `catalog`, `inventory`, `order`, `payment`, `delivery`, `customer`, `promotion`, `notification`, `reporting` | `REQ-011`, `REQ-012`, `FR-001`, `FR-003`, `FR-004`, `FR-005`, `FR-006` |
| **`customer`** | Storefront customer registration/login, profile, and saved delivery addresses. | `customers`, `customer_addresses` | `CustomerApi.getCustomerForOrder(customerId)` | `shared`, `audit` | `branch`, `identity`, `catalog`, `inventory`, `order`, `payment`, `delivery`, `promotion`, `notification`, `reporting` | `FR-002`, `FR-031`, `BRULE-027` |
| **`catalog`** | Central product categories, products, SKUs (`REQ-003`), optional seasonal windows (`FR-011`), and per-branch product prices (`REQ-004`, `REQ-017`). | `product_categories`, `products`, `branch_product_prices` | `CatalogApi.getProductSnapshotForBranch(branchId, productId)` (returns SKU, name, branch `BigDecimal` price, sellable flag), `BranchPriceChangedEvent` | `shared`, `audit`, `branch` | `identity`, `customer`, `inventory`, `order`, `payment`, `delivery`, `promotion`, `notification`, `reporting` | `REQ-003`, `REQ-004`, `REQ-017`, `FR-010`, `FR-011`, `FR-012`, `FR-013`, `FR-014` |
| **`inventory`** | Branch stock (`REQ-002`, `REQ-016`), non-negative locking (`REQ-007`), inventory audit ledger (`REQ-008`), order stock reservation/release (`FR-017`), goods receipts (`FR-020`), spoilage/adjustments (`FR-019`), and inter-branch stock transfers (`REQ-015`, `FR-018`). | `branch_inventories`, `inventory_movements`, `stock_transfers`, `stock_transfer_lines`, `goods_receipts`, `goods_receipt_lines`, `stock_adjustments` | `InventoryApi.findBranchesWithSufficientStock(branchIds, itemQuantities)`, `InventoryApi.reserveStockForOrder(...)`, `InventoryApi.confirmOrderStockDeduction(...)`, `InventoryApi.releaseOrderStock(...)`, `StockChangedEvent` | `shared`, `audit`, `branch`, `catalog` | `identity`, `customer`, `order`, `payment`, `delivery`, `promotion`, `notification`, `reporting` | `REQ-002`, `REQ-007`, `REQ-008`, `REQ-015`, `REQ-016`, `FR-015`, `FR-016`, `FR-017`, `FR-018`, `FR-019`, `FR-020` |
| **`promotion`** *(Conditional on `B6`)* | Promotional voucher definitions, validity rules, and atomic voucher redemption. | `promotions_vouchers`, `voucher_redemptions` | `PromotionApi.validateAndRedeemVoucher(code, branchId, subtotal)` | `shared`, `audit`, `branch` | `inventory`, `order`, `payment`, `delivery`, `notification`, `reporting` | `FR-032` |
| **`order`** | Online order placement, automatic nearest-branch allocation (`REQ-005`, `REQ-014`), order line snapshots (`REQ-006`), order status lifecycle, cancellation, exception branch reassignment, and post-delivery returns. | `orders`, `order_lines`, `order_status_history`, `order_returns` | `OrderApi.getOrderForPayment(orderId)`, `OrderApi.markOrderPaid(orderId, paymentId)`, `OrderApi.recordOrderRefunded(...)`, `OrderPlacedEvent`, `OrderStatusChangedEvent`, `OrderCancelledEvent` | `shared`, `audit`, `branch`, `customer`, `catalog`, `inventory`, (`promotion` if enabled) | `payment` (Notice: `payment` depends on `order`, never `order` on `payment` — keeps graph acyclic!), `notification`, `reporting` | `REQ-005`, `REQ-006`, `REQ-013`, `REQ-014`, `FR-021`, `FR-022`, `FR-023`, `FR-024`, `FR-030`, `FR-038` |
| **`delivery`** | Delivery dispatch tracking, delivery fee/slot validation, courier/rider assignment (`FR-028`), and in-store pickup collection hand-over (`FR-029`). | `order_deliveries` | `DeliveryApi.calculateDeliveryQuote(branchId, address)`, `DeliveryApi.updateDeliveryState(...)`, `OrderDeliveredEvent`, `OrderPickedUpEvent` | `shared`, `audit`, `branch`, `order` | `payment`, `inventory`, `catalog`, `notification`, `reporting` | `REQ-013`, `FR-028`, `FR-029` |
| **`payment`** | Payment initiation, server-side webhook signature verification (`REQ-009`), idempotent payment event deduplication (`REQ-010`), and refund recording/processing (`FR-025`..`027`). | `payments`, `payment_webhook_events`, `payment_refunds` | `PaymentApi.initiatePayment(...)`, `PaymentApi.processVerifiedWebhook(...)`, `PaymentApi.recordRefund(...)`, `PaymentVerifiedEvent`, `PaymentRefundedEvent` | `shared`, `audit`, `order` | `inventory`, `catalog`, `delivery`, `customer`, `promotion`, `notification`, `reporting` | `REQ-009`, `REQ-010`, `FR-025`, `FR-026`, `FR-027` |
| **`notification`** | Event-driven in-app branch alerts and outbound customer notifications (`FR-033`). | `notifications` | Event listeners (`@ApplicationModuleListener` / `@EventListener`) on `OrderPlacedEvent`, `OrderStatusChangedEvent`, `PaymentVerifiedEvent` | `shared` (plus event payloads from `order`, `payment`, `inventory`) | Never calls `order`, `payment`, or `inventory` mutating APIs | `FR-033` |
| **`reporting`** | Branch-scoped and chain-wide revenue, order fulfilment, and inventory movement summaries (`FR-034`, `FR-035`). | *(Read-only projections / queries via `OrderApi`, `InventoryApi`, `BranchApi`, `AuditApi`)* | REST query endpoints under `/api/v1/admin/reports/**` | `shared`, `branch`, `catalog`, `inventory`, `order`, `payment`, `audit` | Never mutates any table | `FR-034`, `FR-035` |

---

## 3. Proof of Acyclic Dependency Hierarchy (Topological Order)

Every module is assigned a topological layer (`L0` to `L5`). A module at layer `Ln` may only depend on modules at layers `< Ln`:

- **Layer 0 (Technical Foundation):** `shared`
- **Layer 1 (Core Audit):** `audit` (depends on `L0`)
- **Layer 2 (Master Organization & Customers):** `branch`, `customer` (depend on `L0..L1`)
- **Layer 3 (Identity, Catalog & Promotions):** `identity`, `catalog`, `promotion` (depend on `L0..L2`)
- **Layer 4 (Branch Inventory):** `inventory` (depends on `L0..L3`: `shared`, `audit`, `branch`, `catalog`)
- **Layer 5 (Ordering):** `order` (depends on `L0..L4`: `shared`, `audit`, `branch`, `customer`, `catalog`, `inventory`, `promotion`)
- **Layer 6 (Fulfilment & Payment):** `delivery`, `payment` (depend on `order` and `L0..L2`; neither depends on the other)
- **Layer 7 (Event Consumers & Read Reporting):** `notification`, `reporting` (depend on lower layers; no module depends on `L7`)

Because every dependency points strictly from a higher layer to a lower layer (`L7 → L6 → L5 → L4 → L3 → L2 → L1 → L0`), **cyclic dependencies are mathematically impossible** and automatically verifiable by Spring Modulith / ArchUnit (`NFR-011`).
