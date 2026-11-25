# Traceability and Verification Matrices

- **Status:** DRAFT (awaiting owner approval at Phase 3 gate before P05)
- **Last updated:** 2026-11-24
- **Owner:** business-analyst & qa-engineer (`requirements-engineering` & `testing` skills) · **Task:** TASK-010 (Phase 3 — P04 Requirements Engineering)
- **Sources:** `REQUIREMENTS.md` (`REQ-001`..`017`), `functional-requirements.md` (`FR-001`..`038`), `non-functional-requirements.md` (`NFR-001`..`015`), `roles-and-permissions.md`, P03 `use-case-catalog.md`, P03 `business-rules-catalog.md`, `docs/testing/TEST_CASES.md`.

---

## 1. Master Requirement Traceability Matrix (`REQ` ↔ `BRULE` ↔ `FR` / `NFR` ↔ `UC` ↔ `WF` ↔ `TEST`)

| Baseline / Domain REQ | Business Rule (`BRULE`) | Functional Requirements (`FR`) | Non-Functional Requirements (`NFR`) | Use Cases (`UC`) | Workflows (`WF`) | Initial Test Cases (`TEST`) | Status |
|---|---|---|---|---|---|---|---|
| **REQ-001** (One business owns 5–30 branches) | `BRULE-001`, `BRULE-018` | `FR-007`, `FR-008`, `FR-009`, `FR-037` | `NFR-005`, `NFR-011` | `UC-HO-01`, `UC-CUST-01` | `WF-01`, `WF-02` | `TEST-001` | DRAFT |
| **REQ-002** (Inventory belongs to a specific branch) | `BRULE-002` | `FR-015`, `FR-019`, `FR-020`, `FR-035` | `NFR-005`, `NFR-009` | `UC-BR-05`, `UC-BR-06`, `UC-BR-07` | `WF-04`, `WF-09`, `WF-10` | `TEST-002`, `TEST-015` | DRAFT |
| **REQ-003** (Central product catalog by default) | `BRULE-003` | `FR-010`, `FR-011`, `FR-012` | `NFR-001` | `UC-HO-02`, `UC-CUST-01` | `WF-01` | `TEST-003` | DRAFT |
| **REQ-004** (Product prices vary by branch) | `BRULE-004`, `BRULE-019`, `BRULE-020` | `FR-013`, `FR-014` | `NFR-009`, `NFR-010` | `UC-BR-09`, `UC-CUST-01` | `WF-01` | `TEST-004`, `TEST-005` | DRAFT |
| **REQ-005** (Every order has a traceable processing branch) | `BRULE-005`, `BRULE-022`, `BRULE-023` | `FR-021`, `FR-022`, `FR-023`, `FR-034` | `NFR-009`, `NFR-010` | `UC-CUST-03`, `UC-CUST-04`, `UC-BR-01`, `UC-BR-02`, `UC-HO-05`, `UC-HO-07` | `WF-03`, `WF-06`, `WF-07` | `TEST-006`, `TEST-016` | DRAFT |
| **REQ-006** (Orders keep snapshots of product & price data) | `BRULE-006` | `FR-021`, `FR-031`, `FR-032`, `FR-034`, `FR-038` | `NFR-009` | `UC-CUST-02`, `UC-CUST-03`, `UC-CUST-04`, `UC-CUST-06`, `UC-HO-07` | `WF-03` | `TEST-007` *(Mandatory baseline)* | DRAFT |
| **REQ-007** (Available inventory must never become negative) | `BRULE-007`, `BRULE-021` | `FR-015`, `FR-017`, `FR-018`, `FR-019`, `FR-021`, `FR-038` | `NFR-009`, `NFR-012` | `UC-CUST-03`, `UC-CUST-04`, `UC-BR-05`, `UC-BR-07`, `UC-BR-08` | `WF-04`, `WF-10`, `WF-11` | `TEST-008`, `TEST-009` *(Mandatory baseline)* | DRAFT |
| **REQ-008** (Every inventory change is auditable) | `BRULE-008`, `BRULE-021` | `FR-016`, `FR-017`, `FR-018`, `FR-019`, `FR-020`, `FR-024`, `FR-030`, `FR-035`, `FR-036`, `FR-038` | `NFR-010`, `NFR-012` | `UC-BR-05`, `UC-BR-06`, `UC-BR-07`, `UC-BR-08`, `UC-HO-07` | `WF-04`, `WF-08`..`WF-12` | `TEST-010` *(Mandatory baseline)* | DRAFT |
| **REQ-009** (Payment webhooks verified on server) | `BRULE-009`, `BRULE-024` | `FR-025` | `NFR-007`, `NFR-012` | `UC-CUST-05` | `WF-05` | `TEST-011` *(Mandatory baseline)* | DRAFT |
| **REQ-010** (Same payment never processed twice) | `BRULE-010`, `BRULE-025`, `BRULE-026` | `FR-026`, `FR-027` | `NFR-004`, `NFR-009`, `NFR-012` | `UC-CUST-05`, `UC-HO-06` | `WF-05`, `WF-08`, `WF-12` | `TEST-012` *(Mandatory baseline)* | DRAFT |
| **REQ-011** (Backend enforces branch-level authorization) | `BRULE-011`, `BRULE-027`, `BRULE-028`, `BRULE-029`, `BRULE-030` | `FR-001`..`FR-006`, `FR-013`, `FR-018`..`FR-020`, `FR-022`, `FR-024`, `FR-028`, `FR-029`, `FR-031`, `FR-033`..`FR-036` | `NFR-006`, `NFR-007`, `NFR-012` | `UC-BR-01`..`10`, `UC-HO-03`, `UC-CUST-06` | `WF-06`..`WF-11` | `TEST-013` *(Mandatory baseline)* | DRAFT |
| **REQ-012** (Frontend `branchId` never trusted without auth check) | `BRULE-012` | `FR-006`, `FR-013`, `FR-018`..`FR-022`, `FR-034`..`FR-036` | `NFR-006`, `NFR-012` | `UC-BR-01`..`10`, `UC-CUST-03` | `WF-02`, `WF-03`, `WF-06`, `WF-09`..`WF-11` | `TEST-014` *(Mandatory baseline)* | DRAFT |
| **REQ-013** (Customer chooses home delivery or in-store pickup) | `BRULE-013`, `BRULE-022` | `FR-009`, `FR-021`, `FR-022`, `FR-028`, `FR-029` | `NFR-013` | `UC-CUST-02`, `UC-CUST-03`, `UC-CUST-04`, `UC-BR-03`, `UC-BR-04` | `WF-01`, `WF-02`, `WF-07` | `TEST-015` | DRAFT |
| **REQ-014** (System selects nearest branch with stock for delivery) | `BRULE-014`, `BRULE-018` | `FR-008`, `FR-021`, `FR-023` | `NFR-001`, `NFR-005` | `UC-CUST-03`, `UC-HO-01`, `UC-HO-05` | `WF-02`, `WF-03` | `TEST-016`, `TEST-017` | DRAFT |
| **REQ-015** (Inter-branch stock transfers are allowed and recorded) | `BRULE-015` | `FR-018` | `NFR-009`, `NFR-010` | `UC-BR-08`, `UC-HO-04` | `WF-11` | `TEST-018` | DRAFT |
| **REQ-016** (No central warehouse; all stock held by branches) | `BRULE-016` | `FR-015`, `FR-020` | `NFR-009` | `UC-BR-05`, `UC-BR-06` | `WF-04`, `WF-09` | `TEST-002` | DRAFT |
| **REQ-017** (Storefront shows branch prices) | `BRULE-017` | `FR-014` | `NFR-001`, `NFR-013` | `UC-CUST-01` | `WF-01`, `WF-02` | `TEST-019` | NEEDS_CONFIRMATION (`B11`) |

---

## 2. Use Case to Functional Requirement Matrix (`UC` ↔ `FR`)

| Use Case ID | Use Case Title | Implementing Functional Requirements (`FR`) | Governing Business Rules (`BRULE`) |
|---|---|---|---|
| **UC-CUST-01** | Browse Product Catalog & Branch Prices | `FR-009`, `FR-010`, `FR-011`, `FR-012`, `FR-014` | `BRULE-003`, `BRULE-004`, `BRULE-017`, `BRULE-020` |
| **UC-CUST-02** | Manage Shopping Cart | `FR-014`, `FR-021` | `BRULE-006`, `BRULE-013` |
| **UC-CUST-03** | Place Online Home Delivery Order | `FR-008`, `FR-014`, `FR-015`, `FR-016`, `FR-017`, `FR-021` | `BRULE-005`, `BRULE-006`, `BRULE-007`, `BRULE-008`, `BRULE-012`, `BRULE-013`, `BRULE-014`, `BRULE-026` |
| **UC-CUST-04** | Place Online In-Store Pickup Order | `FR-009`, `FR-015`, `FR-016`, `FR-017`, `FR-021` | `BRULE-005`, `BRULE-006`, `BRULE-007`, `BRULE-008`, `BRULE-013`, `BRULE-022`, `BRULE-026` |
| **UC-CUST-05** | Pay for Online Order | `FR-025`, `FR-026` | `BRULE-009`, `BRULE-010`, `BRULE-024`, `BRULE-026` |
| **UC-CUST-06** | View Own Order Status & History | `FR-002`, `FR-031`, `FR-033` | `BRULE-005`, `BRULE-006`, `BRULE-027` |
| **UC-CUST-07** | Request Order Cancellation or Return | `FR-024`, `FR-027`, `FR-030` | `BRULE-008`, `BRULE-023`, `BRULE-025`, `BRULE-027` |
| **UC-CUST-08** | Apply Voucher or Promotion Code | `FR-032` | `BRULE-006` |
| **UC-BR-01** | View & Filter Assigned Branch Orders | `FR-006`, `FR-022` | `BRULE-005`, `BRULE-011`, `BRULE-012` |
| **UC-BR-02** | Prepare Order & Update Fulfilment Status | `FR-006`, `FR-022`, `FR-033` | `BRULE-005`, `BRULE-011`, `BRULE-012`, `BRULE-023` |
| **UC-BR-03** | Complete In-Store Pickup Hand-Over | `FR-006`, `FR-022`, `FR-029` | `BRULE-005`, `BRULE-011`, `BRULE-013`, `BRULE-023` |
| **UC-BR-04** | Dispatch & Complete Home Delivery | `FR-006`, `FR-022`, `FR-028` | `BRULE-005`, `BRULE-011`, `BRULE-013`, `BRULE-023` |
| **UC-BR-05** | View Branch Inventory & Stock Audit Trail | `FR-006`, `FR-015`, `FR-016`, `FR-035` | `BRULE-002`, `BRULE-007`, `BRULE-008`, `BRULE-011`, `BRULE-012`, `BRULE-016` |
| **UC-BR-06** | Record Goods Receipt (Branch Stock-In) | `FR-006`, `FR-015`, `FR-016`, `FR-020` | `BRULE-002`, `BRULE-008`, `BRULE-011`, `BRULE-012`, `BRULE-016` |
| **UC-BR-07** | Record Spoilage, Damage, or Stock Adjustment | `FR-006`, `FR-015`, `FR-016`, `FR-019` | `BRULE-002`, `BRULE-007`, `BRULE-008`, `BRULE-011`, `BRULE-012`, `BRULE-021` |
| **UC-BR-08** | Request, Dispatch, or Receive Stock Transfer | `FR-006`, `FR-015`, `FR-016`, `FR-018` | `BRULE-007`, `BRULE-008`, `BRULE-011`, `BRULE-012`, `BRULE-015` |
| **UC-BR-09** | Manage Branch Product Prices | `FR-006`, `FR-012`, `FR-013` | `BRULE-004`, `BRULE-011`, `BRULE-012`, `BRULE-019`, `BRULE-020` |
| **UC-BR-10** | Record Walk-In Store Sale (POS) *(Conditional `B1`)* | `FR-006`, `FR-015`, `FR-016`, `FR-038` | `BRULE-002`, `BRULE-004`, `BRULE-005`, `BRULE-006`, `BRULE-007`, `BRULE-008`, `BRULE-011`, `BRULE-012` |
| **UC-HO-01** | Manage Branches & System Settings | `FR-007`, `FR-008`, `FR-009`, `FR-037` | `BRULE-001`, `BRULE-014`, `BRULE-018` |
| **UC-HO-02** | Manage Central Product Catalog & Categories | `FR-010`, `FR-011`, `FR-012` | `BRULE-003`, `BRULE-020` |
| **UC-HO-03** | Manage Staff Accounts, Roles & Branch Assignments | `FR-001`, `FR-003`, `FR-004`, `FR-005`, `FR-006` | `BRULE-011`, `BRULE-012`, `BRULE-028`, `BRULE-029`, `BRULE-030` |
| **UC-HO-04** | Oversee / Approve Inter-Branch Stock Transfers | `FR-006`, `FR-018` | `BRULE-007`, `BRULE-008`, `BRULE-011`, `BRULE-015` |
| **UC-HO-05** | Reassign Order Processing Branch | `FR-023` | `BRULE-005`, `BRULE-006`, `BRULE-007`, `BRULE-008`, `BRULE-023` |
| **UC-HO-06** | Process Order Refund & Post-Delivery Returns | `FR-027`, `FR-030` | `BRULE-008`, `BRULE-025`, `BRULE-026` |
| **UC-HO-07** | View Chain-Wide Revenue, Inventory & Audit Reports | `FR-034`, `FR-035`, `FR-036` | `BRULE-005`, `BRULE-006`, `BRULE-008`, `BRULE-011`, `BRULE-012`, `BRULE-019`, `BRULE-023`, `BRULE-025`, `BRULE-030` |
| **UC-HO-08** | Manage Promotions and Vouchers *(Conditional `B6`)* | `FR-032` | `BRULE-006` |

---

## 3. Requirement Dependency Matrix (`FR` Build Order)

No functional requirement may be implemented before its prerequisite `FR`s are implemented and verified.

| Layer / Wave | Functional Requirements | Depends On (`FR`) | Rationale |
|---|---|---|---|
| **Wave 1: Identity, Security & Organization Foundation** | `FR-001`, `FR-003`, `FR-007` | None | Core authentication, password hashing, and branch entity creation. |
| **Wave 2: Roles, Branch Scoping & Branch Config** | `FR-002`, `FR-004`, `FR-005`, `FR-006`, `FR-008`, `FR-009`, `FR-037` | `FR-001`, `FR-003`, `FR-007` | Staff accounts, role/branch assignments, server-side branch authorization filter/aspect (`FR-006`), and branch geolocation/zones. |
| **Wave 3: Central Catalog & Per-Branch Pricing** | `FR-010`, `FR-011`, `FR-012`, `FR-013` | `FR-006`, `FR-007` | Central products/SKUs (`FR-010`) and branch-isolated prices + price audit trail (`FR-013`). |
| **Wave 4: Branch Inventory, Audit Ledger & Stock Operations** | `FR-015`, `FR-016`, `FR-018`, `FR-019`, `FR-020` | `FR-006`, `FR-007`, `FR-010` | Branch stock table with `CHECK (available_stock >= 0)` (`FR-015`), atomic audit ledger (`FR-016`), goods receipts (`FR-020`), spoilage/adjustments (`FR-019`), and inter-branch transfers (`FR-018`). |
| **Wave 5: Storefront Pricing, Order Allocation & Reservation** | `FR-014`, `FR-017`, `FR-021`, `FR-031`, `FR-032` | `FR-008`, `FR-013`, `FR-015`, `FR-016` | Storefront price display (`FR-014`), order reservation (`FR-017`), automatic nearest-branch allocation + order line snapshots (`FR-021`). |
| **Wave 6: Payment Verification & Idempotency** | `FR-025`, `FR-026` | `FR-021` | Server-side webhook signature verification (`FR-025`) and idempotent payment processing (`FR-026`). |
| **Wave 7: Order Fulfilment, Delivery, Pickup, Cancellation & Refunds** | `FR-022`, `FR-023`, `FR-024`, `FR-027`, `FR-028`, `FR-029`, `FR-030`, `FR-033`, `FR-038` | `FR-006`, `FR-017`, `FR-021`, `FR-026` | Branch preparation queue (`FR-022`), delivery (`FR-028`), pickup (`FR-029`), cancellation + stock restore (`FR-024`), refunds (`FR-027`), and notifications (`FR-033`). |
| **Wave 8: Reporting & Unified Audit Querying** | `FR-034`, `FR-035`, `FR-036` | `FR-006`, `FR-013`, `FR-016`, `FR-021`, `FR-022`, `FR-027` | Branch-scoped and chain-wide revenue/inventory reports and audit log inquiry. |

---

## 4. Initial Test Case Mapping (`TEST-001` to `TEST-020`)

These initial test cases expand `docs/testing/TEST_CASES.md` and cover all mandatory baseline rules (`CLAUDE.md` §9) plus core functional and non-functional requirements:

| Test ID | Verifies (`REQ` / `FR` / `NFR`) | Test Scenario | Level | Status |
|---|---|---|---|---|
| **TEST-001** | `REQ-001`, `FR-007`, `FR-009` | Create multiple branches; verify active branches appear in storefront directory and deactivated branches are excluded from new orders. | `INTEGRATION` | `PLANNED` |
| **TEST-002** | `REQ-002`, `REQ-016`, `FR-015`, `NFR-009` | Verify every inventory row requires a non-null `branch_id` foreign key and no central warehouse stock exists. | `INTEGRATION` | `PLANNED` |
| **TEST-003** | `REQ-003`, `FR-010`, `FR-006` | Head Office creates central product SKU; branch-scoped user attempting to create/edit central product is denied with `403`. | `INTEGRATION` | `PLANNED` |
| **TEST-004** | `REQ-004`, `FR-013` | Configure different prices for Product P at Branch A (`500000`) and Branch B (`550000`); verify updating A's price does not alter B's price. | `UNIT` + `INTEGRATION` | `PLANNED` |
| **TEST-005** | `REQ-004`, `BRULE-019`, `FR-013`, `FR-036` | Verify changing a branch price writes an immutable price-change audit entry with actor, UTC timestamp, branch, product, old price, and new price. | `INTEGRATION` | `PLANNED` |
| **TEST-006** | `REQ-005`, `FR-021`, `NFR-009` | Verify every persisted order has a non-null `processing_branch_id` foreign key. | `INTEGRATION` | `PLANNED` |
| **TEST-007** *(Baseline)* | `REQ-006`, `FR-021`, `FR-010`, `FR-013` | **Order snapshot immutability:** Place an order for Product P at Branch A; subsequently change P's catalog name/SKU and Branch A's price; verify the historical order still returns the original name, SKU, and price. | `INTEGRATION` | `PLANNED` |
| **TEST-008** *(Baseline)* | `REQ-007`, `FR-015`, `FR-017`, `FR-021` | **Concurrent checkout non-negative stock:** Branch A has 1 unit of Product P; fire 10 concurrent order/deduction requests for 1 unit at Branch A; verify exactly 1 succeeds, 9 fail cleanly, and `available_stock == 0` (never `< 0`). | `INTEGRATION` (Testcontainers) | `PLANNED` |
| **TEST-009** *(Baseline)* | `REQ-007`, `FR-015`, `NFR-009` | **Database CHECK constraint:** Attempt a direct SQL update setting `available_stock = -1`; verify PostgreSQL rejects it with a check constraint violation. | `INTEGRATION` (Testcontainers) | `PLANNED` |
| **TEST-010** *(Baseline)* | `REQ-008`, `FR-016`..`FR-020`, `FR-024`, `NFR-010` | **Inventory audit trail completeness:** Perform a goods receipt (`+10`), order reservation (`-2`), cancellation release (`+2`), spoilage write-off (`-1`), and transfer (`-3` at A, `+3` at B); verify every change writes an immutable audit row with actor, UTC timestamp, reason, branch, delta, and source document ID. | `INTEGRATION` (Testcontainers) | `PLANNED` |
| **TEST-011** *(Baseline)* | `REQ-009`, `FR-025`, `NFR-007` | **Forged webhook rejection & redirect non-trust:** Send a payment webhook with an invalid/missing signature and call the browser redirect URL; verify the webhook is rejected (`400`/`401`) and the order is never marked paid. | `UNIT` + `INTEGRATION` | `PLANNED` |
| **TEST-012** *(Baseline)* | `REQ-010`, `FR-026`, `NFR-004`, `NFR-009` | **Idempotent duplicate webhook handling:** Deliver the exact same verified payment webhook event twice (sequentially and concurrently); verify payment is recorded once, order transitions once, and duplicate SQL insert is blocked by `UNIQUE` constraint. | `INTEGRATION` (Testcontainers) | `PLANNED` |
| **TEST-013** *(Baseline)* | `REQ-011`, `FR-005`, `FR-006`, `NFR-006` | **Cross-branch read/write denial:** Authenticate as a user assigned only to Branch A; verify list endpoints return only Branch A records and direct access to Branch B orders, stock, prices, or transfers is denied (`403`/`404`). | `INTEGRATION` | `PLANNED` |
| **TEST-014** *(Baseline)* | `REQ-012`, `FR-006`, `FR-021`, `NFR-006` | **Untrusted client `branchId` tampering:** Authenticate as Branch A staff and send a payload/query with `"branchId": "<BranchB>"`; verify backend rejects it (`403`). On delivery checkout, send `"branchId": "<BranchC>"` and verify backend overrides it with the auto-selected branch. | `INTEGRATION` | `PLANNED` |
| **TEST-015** | `REQ-013`, `FR-021`, `FR-028`, `FR-029` | Place one `DELIVERY` order (with delivery address) and one `PICKUP` order; verify fulfilment methods are stored and each follows its respective hand-over flow. | `INTEGRATION` + `E2E` (Playwright) | `PLANNED` |
| **TEST-016** | `REQ-014`, `FR-008`, `FR-021` | **Nearest branch with stock selection:** Branches A (2 km away, sufficient stock) and B (5 km away, sufficient stock) → verify A is selected. When A has insufficient stock for 1 item and B has full stock → verify B is selected. | `UNIT` + `INTEGRATION` | `PLANNED` |
| **TEST-017** | `REQ-014`, `FR-021` | **No branch has sufficient stock:** Place a delivery order where no active branch holds sufficient stock; verify order is rejected cleanly with Problem Details and zero stock is deducted. | `UNIT` + `INTEGRATION` | `PLANNED` |
| **TEST-018** | `REQ-015`, `FR-018` | **Inter-branch stock transfer:** Transfer 4 units from Branch A (stock 10) to Branch B (stock 2); verify A ends at 6, B ends at 6, paired `-4`/`+4` audit entries exist, and a transfer exceeding A's stock is rejected. | `UNIT` + `INTEGRATION` | `PLANNED` |
| **TEST-019** | `REQ-017`, `FR-014` | **Storefront branch price display & checkout reconciliation:** Verify storefront shows chosen branch's price and flags any price difference if auto-selected delivery branch has a different price (`B11`). | `E2E` (Playwright) | `PLANNED` |
| **TEST-020** | `NFR-008`, `NFR-011`, `FR-002` | Verify customer A cannot view customer B's order (`404`), API errors return RFC 9457 `application/problem+json` without stack traces, and ArchUnit/Modulith boundary checks pass with zero cyclic dependencies. | `UNIT` + `INTEGRATION` | `PLANNED` |
