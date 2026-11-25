# Internal Management Dashboard — Page Specifications & Wireframes (`frontend/admin`)

- **Status:** PROPOSED (awaiting owner approval at Phase 6 gate before P08)
- **Last updated:** 2026-11-24
- **Owner:** frontend-developer (`hallmark`, `design-taste-frontend`, and `frontend-development` skills) · **Task:** TASK-013 (Phase 6 — P07 UI/UX Design)
- **Sources:** P04 `functional-requirements.md` (`FR-001`..`038`), `roles-and-permissions.md`, P06 `docs/api/openapi.yaml`, `design-system-and-components.md`.

---

## 1. Admin Workbench Shell & Anti-Template Design Philosophy

Per the P07 brief (*"Avoid generic admin template appearance"*) and the **Hallmark `Garden` / Workbench** rules:
- **No generic SaaS bloat:** Zero dark-slate sidebars with glowing neon gradients, zero circular gauge charts, zero decorative status dots, zero card-in-card shadow boxes, and zero all-caps tracked table headers.
- **Editorial Botanical Workbench Aesthetic:**
  - Warm oat structural frame (`--color-paper` `#F6F2E9`) paired with crisp elevated paper data sheets (`--color-surface-elevated` `#FCFBF7`) framed by `1px solid var(--color-rule)` hairlines.
  - Section headings in `Vollkorn 600`; operational data, SKUs, timestamps, `VND` amounts, and stock balances in `Hanken Grotesk` with **`lining-nums tabular-nums`**.
- **Top Bar with Server-Resolved Branch Scope (`CMP-10` — `REQ-011`, `REQ-012`):**
  - Displays the logged-in staff member's name, role pills (`HEAD_OFFICE_ADMIN`, `BRANCH_MANAGER`, etc.), and their **Server-Authorized Branch Scope**:
    - For `SYSTEM_OWNER` / `HEAD_OFFICE_ADMIN`: Branch filter selector (`Scope: All 12 Branches ▾` or filter to one branch).
    - For Branch-Scoped Roles (`BRANCH_MANAGER`, `SALES_STAFF`, `INVENTORY_STAFF`, `DELIVERY_STAFF`): Locked moss-tinted badge (`Authorized Scope: District 1 Branch`) that reminds staff all queries and writes are restricted to their assigned branch(es) by the backend.

---

## 2. Screen-by-Screen Specifications & Wireframes (15 Admin Modules)

### 2.1 Executive & Branch Operations Dashboard (`/admin/dashboard`)
- **Role-Adaptive Layout:**
  - **Head Office View (`SYSTEM_OWNER`, `HEAD_OFFICE_ADMIN`):**
    1. **4 Hairline KPI Cells (Full-width ruled strip):** Today's Chain Revenue (`VND`, from order snapshots `REQ-006`), Active Orders Across Branches (`DELIVERY` vs. `PICKUP`), Low-Stock / Zero-Stock Branch Alerts (`REQ-007`), and In-Transit Stock Transfers (`REQ-015`).
    2. **Branch Health & Fulfilment Matrix Table:** One row per active branch (`5–30`) showing `Pending/Preparing Orders`, `Completed Today`, `Low-Stock SKUs`, `Spoilage Today`, and `Gross Revenue`.
    3. **Recent Critical Audit Feed (`FR-036`):** Live last 10 events across `PRICE_CHANGE`, `ROLE_CHANGE`, `REFUND`, and `ORDER_BRANCH_REASSIGNED`.
  - **Branch View (`BRANCH_MANAGER`, `SALES_STAFF`, `INVENTORY_STAFF`, `DELIVERY_STAFF`):**
    1. Scoped strictly to `authorizedBranchIds` (`REQ-011`).
    2. Highlights urgent orders awaiting preparation (`CONFIRMED`), pickups awaiting collection (`READY_FOR_PICKUP`), deliveries ready for dispatch (`READY_FOR_DELIVERY`), incoming stock transfers (`IN_TRANSIT` to this branch), and low-stock bouquets at this branch.

#### Wireframe — Admin Workbench Shell & Dashboard (`/admin/dashboard`)
```text
+----------------------------------------------------------------------------------------------------+
| FlowerChain Workbench          Scope: [ All 12 Branches (Head Office) ▾ ]   Tran Minh (HO Admin)   |
+----------------------+-----------------------------------------------------------------------------+
| Overview             | Chain operations overview · Tuesday 24 Nov 2026                             |
| • Dashboard          | --------------------------------------------------------------------------- |
| • Reports            | Gross revenue (snapshots) | Active orders | Low-stock pairs | In-transit    |
|                      | 48,650,000 VND            | 34 orders     | 7 branch SKUs   | 4 transfers   |
| Sales & Fulfilment   | 22 delivery · 12 pickup   | across 9 shops| <= 3 units left | between shops |
| • Orders (14)        | --------------------------------------------------------------------------- |
| • Deliveries & Pickup| Branch performance & stock health                              (Export CSV) |
| • Payments & Refunds | --------------------------------------------------------------------------- |
|                      | Branch               Preparing  Ready  Completed  Low Stock  Revenue (VND)  |
| Catalog & Pricing    | --------------------------------------------------------------------------- |
| • Central Products   | District 1 Flagship          4      2         18          1     14,200,000  |
| • Branch Prices      | Binh Thanh Branch            3      1         11          2      9,450,000  |
| • Promotions         | District 3 Studio            2      3          9          0      7,800,000  |
|                      | Thao Dien Branch             5      0         14          4     11,100,000  |
| Inventory & Supply   | --------------------------------------------------------------------------- |
| • Branch Stock       | Recent chain audit events (REQ-008 / FR-036)            View all audit logs |
| • Goods Receipts     | --------------------------------------------------------------------------- |
| • Spoilage & Adjust  | 09:42  PRICE_CHANGE   District 1 · RRB-01: 450,000 -> 470,000   by Lan (BM) |
| • Stock Transfers    | 09:31  TRANSFER_OUT   Binh Thanh -> Thao Dien · 6x MLV-02 (-6)  by Huy (IS) |
|                      | 09:18  REFUND         ORD-20261124-0004 · 520,000 VND           by Minh(HO) |
| Organization         |                                                                             |
| • Branches           |                                                                             |
| • Staff & Roles      |                                                                             |
| • Customers          |                                                                             |
| • Audit Logs         |                                                                             |
| • System Settings    |                                                                             |
+----------------------+-----------------------------------------------------------------------------+
```

---

### 2.2 Branch Management (`/admin/branches` — `FR-007`, `FR-008`, `REQ-001`, `REQ-014`)
- **API Binding:** `GET /api/v1/admin/branches`, `POST /api/v1/admin/branches`, `PUT /api/v1/admin/branches/{branchId}`.
- **Key Features:**
  - Hairline table of all 5–30 branches showing `Code` (`BR-D1`), `Name`, `Street & District`, `Coordinates (lat, lng)`, `Max Delivery Radius (km)` (used by automatic nearest-branch order allocation `REQ-014`), `Operating Hours`, and `Status` (`ACTIVE` / `INACTIVE`).
  - **Create / Edit Branch Drawer (`SYSTEM_OWNER` & `HEAD_OFFICE_ADMIN` only):** Form with coordinate validation (`latitude` `-90..90`, `longitude` `-180..180`, `maxDeliveryRadiusKm > 0`). Deactivating a branch warns that it will immediately stop receiving new online delivery allocations or pickup orders.

---

### 2.3 Employee, Role & Branch Assignment Management (`/admin/staff` — `FR-004`, `FR-005`, `REQ-011`)
- **API Binding:** `GET /api/v1/admin/staff`, `POST /api/v1/admin/staff`, `PUT /api/v1/admin/staff/{staffId}/assignments`.
- **Key Features:**
  - Lists staff accounts with `Full Name`, `Email`, `Status` (`ACTIVE` / `DISABLED`), `Assigned Role(s)` (`SYSTEM_OWNER`, `HEAD_OFFICE_ADMIN`, `BRANCH_MANAGER`, `SALES_STAFF`, `INVENTORY_STAFF`, `DELIVERY_STAFF`), and **`Authorized Branches`** (`GLOBAL` or list of branch pills).
  - **Role & Branch Assignment Modal:**
    - Enforces client & server validation: if any branch-level role is selected, at least one branch checkbox must be checked (`FR-005` AC2).
    - Saving immediately revokes stale sessions if the account was disabled or a branch was removed (`ADR-0005`) and writes a `ROLE_CHANGE` audit entry (`BRULE-030`).

---

### 2.4 Central Product Catalog (`/admin/catalog/products` — `FR-010`, `FR-011`, `REQ-003`)
- **API Binding:** `GET /api/v1/admin/products`, `POST /api/v1/admin/products`.
- **Key Features:**
  - Managed centrally by `SYSTEM_OWNER` / `HEAD_OFFICE_ADMIN` (`REQ-003`). Branch roles see a read-only view (`403` if mutation is attempted).
  - Columns: `Thumbnail`, `SKU` (unique immutable identifier snapshotted on orders `REQ-006`), `Product Name`, `Category`, `Unit Label` (`BOUQUET`, `VASE`, `BASKET`), `Seasonal Window` (`availableFrom` – `availableUntil`, `FR-011`), and `Status` (`ACTIVE` / `ARCHIVED`).

---

### 2.5 Per-Branch Pricing Matrix (`/admin/catalog/branch-prices` — `FR-012`, `FR-013`, `REQ-004`, `BRULE-019`)
- **API Binding:** `PUT /api/v1/admin/branches/{branchId}/prices/{productId}`.
- **Key Features:**
  - Branch selector at top (scoped to `authorizedBranchIds`, `REQ-011`, `REQ-012`).
  - Displays every central catalog SKU alongside the selected branch's `Unit Price (VND)` (`BigDecimal` decimal input `>= 0`), `Sellable at this branch` toggle (`isSellable`, `FR-012`), `Last Updated By`, and `Updated Timestamp`.
  - Inline `Save price` action per row or batch with confirmation showing `Old Price → New Price`, committing an immutable `PRICE_CHANGE` audit record (`BRULE-019`) while reminding staff that historical orders keep their snapshotted prices (`REQ-006`).

---

### 2.6 Branch Inventory & Movement Audit Ledger (`/admin/inventory` — `FR-015`, `FR-016`, `REQ-002`, `REQ-007`, `REQ-008`, `REQ-016`)
- **API Binding:** `GET /api/v1/admin/branches/{branchId}/inventory` and `GET /api/v1/admin/branches/{branchId}/inventory/movements`.
- **Key Features:**
  - **Tab 1 — Live Branch Stock Balances (`REQ-002`, `REQ-007`):**
    - Columns: `SKU`, `Product Name`, `Available Stock` (highlighted if `<= 3`; guaranteed `>= 0`), `Reserved Stock` (held by in-progress orders `FR-017`), `Total Physical Stock` (`available + reserved`), and quick links: `Record Stock-In`, `Write Off Spoilage`, `Request Transfer`.
  - **Tab 2 — Immutable Inventory Movement Audit Ledger (`REQ-008`, `FR-016`):**
    - Append-only ledger filterable by `Product SKU`, `Reason Code` (`GOODS_RECEIPT`, `ORDER_RESERVED`, `ORDER_CONFIRMED`, `ORDER_RELEASED`, `TRANSFER_OUT`, `TRANSFER_IN`, `SPOILAGE`, `DAMAGED`, `STOCKTAKE_ADJUSTMENT`, `RETURN_RESTOCK`), and date range.
    - Columns: `UTC Timestamp`, `Product SKU`, `Reason Code`, `Delta` (`+25` / `-2`), `Available After`, `Reserved After`, `Source Document` (clickable link to Order, Transfer, Receipt, or Adjustment), and `Actor`.

---

### 2.7 Purchasing / Goods Receiving & Supplier Notes (`/admin/inventory/goods-receipts` — `FR-020`, `REQ-002`, `REQ-008`, `REQ-016`)
- **API Binding:** `POST /api/v1/admin/branches/{branchId}/goods-receipts`.
- **Rationale (`B19`, `REQ-016`):** Because there is no central warehouse (`REQ-016`), fresh flowers arrive directly at branches from suppliers. This screen covers **Purchasing / Branch Stock-In** and **Supplier Reference Tracking**.
- **Key Features:**
  - **Record Goods Receipt Form:**
    - `Target Branch` (locked/validated against `authorizedBranchIds`, `REQ-011`, `REQ-012`).
    - `Supplier Name *` (with autocomplete of previously used floral suppliers), `Supplier Delivery Note / Invoice Reference`, and `General Note`.
    - Dynamic line items table: `Product SKU *` + `Received Quantity *` (`integer >= 1`).
    - Submitting increments `available_stock` at the branch and writes `+qty` `GOODS_RECEIPT` movement rows (`REQ-008`).

---

### 2.8 Spoilage, Damaged Goods & Stock Adjustments (`/admin/inventory/adjustments` — `FR-019`, `REQ-007`, `REQ-008`)
- **API Binding:** `POST /api/v1/admin/branches/{branchId}/stock-adjustments`.
- **Key Features:**
  - Form with `Branch` (scoped), `Product SKU` (shows current `availableStock` live), `Adjustment Type` (`SPOILAGE` — negative delta only; `DAMAGED` — negative delta only; `STOCKTAKE_ADJUSTMENT` — positive or negative delta), `Quantity Delta`, and mandatory `Reason Note *` (`minLength: 3`).
  - **Live Non-Negative Guard (`REQ-007`):** Displays `Resulting available stock: [availableStock + delta]`. If `availableStock + delta < 0`, the form disables submission and explains that available inventory can never become negative (`REQ-007`); the backend and PostgreSQL `CHECK` constraint enforce the same rule.

---

### 2.9 Inter-Branch Stock Transfers (`/admin/inventory/transfers` — `FR-018`, `REQ-007`, `REQ-008`, `REQ-011`, `REQ-015`)
- **API Binding:** `GET/POST /api/v1/admin/stock-transfers`, `POST /api/v1/admin/stock-transfers/{transferId}/dispatch`, `POST /api/v1/admin/stock-transfers/{transferId}/receive`.
- **Key Features:**
  - **Dual-Branch Authorization UI (`CMP-13`, `REQ-011`, `REQ-012`):**
    - Shows transfers where the user's branch is either `Source (Sending Branch)` or `Target (Receiving Branch)` (or all transfers for Head Office).
    - **Action 1: Request Transfer** (`sourceBranchId <> targetBranchId`, product lines with `requestedQuantity >= 1`).
    - **Action 2: Dispatch from Sending Branch (`POST /dispatch`)** — Enabled **only** for staff authorized for `sourceBranchId` (or Head Office). Deducts `-dispatchedQuantity` from `sourceBranchId` (failing with `409 INVENTORY_INSUFFICIENT_STOCK` if `available_stock < dispatchedQuantity`, `REQ-007`), writes `TRANSFER_OUT` audit row (`REQ-008`), and sets status to `IN_TRANSIT`.
    - **Action 3: Confirm Receipt at Receiving Branch (`POST /receive`)** — Enabled **only** for staff authorized for `targetBranchId` (or Head Office). Inputs `receivedQuantity` (`0..dispatchedQuantity`) and optional `discrepancyNote` (for transit damage `B16`), adds `+receivedQuantity` to `targetBranchId`, writes `TRANSFER_IN` audit row (`REQ-008`), and sets status to `COMPLETED`.

#### Wireframe — Inter-Branch Stock Transfer Workbench (`/admin/inventory/transfers`)
```text
+--------------------------------------------------------------------------------------------------+
| Inter-Branch Stock Transfers (REQ-015)                           ( + Request new stock transfer )|
| Authorized branch scope: Binh Thanh Branch                                                       |
+--------------------------------------------------------------------------------------------------+
| Filter: [ All involving my branch ▾ ]   Status: [ All statuses ▾ ]                               |
| ------------------------------------------------------------------------------------------------ |
| Transfer #         From (Sender)       To (Receiver)       Status       Items        Action      |
| ------------------------------------------------------------------------------------------------ |
| TRF-20261124-012   Binh Thanh Branch   Thao Dien Branch    APPROVED     2 SKUs (10)  ( Dispatch )|
|                    [Your branch: may dispatch -qty]                                              |
| ------------------------------------------------------------------------------------------------ |
| TRF-20261124-009   District 1 Branch   Binh Thanh Branch   IN_TRANSIT   1 SKU (6)    ( Receive ) |
|                                        [Your branch: may confirm receipt +qty]                   |
+--------------------------------------------------------------------------------------------------+
|Selected Transfer: TRF-20261124-009 · District 1 -> Binh Thanh · Status: IN_TRANSIT               |
| ------------------------------------------------------------------------------------------------ |
| Product SKU          Requested   Dispatched (-qty at D1)   Received (+qty at BT)   Discrepancy   |
| RRB-01 Red Rose              6                         6   [ 6                 ]   [ Optional  ] |
|                                                                                                  |
| Note: Confirming receipt adds +6 to Binh Thanh available_stock and writes a TRANSFER_IN audit.   |
|                                                            ( Confirm receipt at Binh Thanh )     |
+--------------------------------------------------------------------------------------------------+
```

---

### 2.10 Order Management & Detail (`/admin/orders` & `/admin/orders/:orderId` — `FR-021`..`024`, `FR-030`, `REQ-005`, `REQ-006`, `REQ-011`)
- **API Binding:** `GET /api/v1/admin/orders`, `GET /api/v1/admin/orders/{orderId}`, `POST /api/v1/admin/orders/{orderId}/status`, `POST /api/v1/admin/orders/{orderId}/reassign-branch`.
- **Key Features:**
  - **Branch-Scoped Order Queue (`/admin/orders`):**
    - Automatically filtered on the server by `processing_branch_id IN (:authorizedBranchIds)` (`REQ-005`, `REQ-011`, `REQ-012`).
    - Quick status tabs: `All`, `Awaiting Payment`, `Confirmed (To Prepare)`, `Preparing`, `Ready for Pickup`, `Ready for Delivery`, `Out for Delivery`, `Completed`, `Cancelled`.
  - **Order Detail Screen (`/admin/orders/:orderId`):**
    - **Immutable Snapshot Panel (`CMP-08`, `REQ-006`):** Displays `skuSnapshot`, `productNameSnapshot`, `unitPriceSnapshot`, `quantity`, and `lineTotalSnapshot` with a label: *"Prices and product names are historical snapshots captured at order placement (`REQ-006`)."*
    - **Status Transition Bar (`FR-022`):** Offers only valid next status button(s) for the order's `fulfilmentType` (`DELIVERY` vs. `PICKUP`), recording an `order_status_history` audit entry on every click.
    - **Exception Branch Reassignment Modal (`FR-023` — Head Office only):** Allows reassigning a `CONFIRMED` order from Branch A to Branch B with a mandatory reason, atomically releasing stock at Branch A (`+qty` audited) and reserving stock at Branch B (`-qty` audited) while preserving the customer's snapshotted price (`REQ-006`).

---

### 2.11 Delivery & In-Store Pickup Fulfilment Queue (`/admin/deliveries` — `FR-028`, `FR-029`, `REQ-013`)
- **Key Features:**
  - **View 1 — Home Delivery Dispatch (`DELIVERY` orders at branch):** Lists orders in `READY_FOR_DELIVERY` and `OUT_FOR_DELIVERY`. Allows assigning a `DELIVERY_STAFF` rider or courier reference, clicking `Dispatch (Out for Delivery)`, and clicking `Mark Delivered (Completed)` with recipient hand-over notes.
  - **View 2 — In-Store Pickup Counter (`PICKUP` orders at branch):** Lists orders in `READY_FOR_PICKUP`. When the customer arrives at the shop, staff verify the order number / recipient phone and click `Confirm Store Pickup Hand-Over (Completed)`.

---

### 2.12 Payment, Webhook Ledger & Refund Management (`/admin/payments` — `FR-025`..`027`, `REQ-009`, `REQ-010`, `BRULE-025`)
- **API Binding:** `POST /api/v1/admin/payments/{paymentId}/refunds` (with `Idempotency-Key` header).
- **Key Features:**
  - Lists payment records (`orderNumber`, `provider`, `paymentMethod`, `providerTransactionId` [`UNIQUE`, `REQ-010`], `amount`, `status`, `capturedAt`) and verified webhook delivery logs (`providerEventId` [`UNIQUE`, `REQ-010`], `signatureVerified = true` [`REQ-009`], `processingOutcome`).
  - **Record / Approve Refund Modal (`FR-027`):** Inputs `refundAmount` (`BigDecimal <= remaining refundable amount`), mandatory `reason`, and `externalRefundRef`. Sends `Idempotency-Key` and writes an immutable `REFUND` audit log entry (`BRULE-025`).

---

### 2.13 Customer Management, Promotions, Reports, Audit Logs & System Settings
1. **Customer Management (`/admin/customers` — `FR-031`):**
   - Read-only customer directory for support staff to look up a customer's contact details and order history when assisting with delivery or return requests (`FR-030`).
2. **Promotions Management (`/admin/promotions` — Conditional `B6` / `FR-032`):**
   - Head Office voucher table (`Code`, `Discount Type`, `Value`, `Min Order Total`, `Valid From/Until`, `Usage Count / Limit`, `Status`).
3. **Revenue & Inventory Movement Reports (`/admin/reports` — `FR-034`, `FR-035`, `REQ-005`, `REQ-006`, `REQ-008`, `REQ-011`):**
   - Date-range and branch selector (scoped to `authorizedBranchIds` for Branch Managers; all branches for Head Office).
   - **Revenue Report Table (`GET /api/v1/admin/reports/revenue`):** Per-branch breakdown of `Completed Orders`, `Delivery Orders`, `Pickup Orders`, and `Gross Revenue (VND)` computed strictly from `order_lines` snapshots (`REQ-006`).
   - **Inventory & Spoilage Summary (`FR-035`):** Aggregates `GOODS_RECEIPT (+qty)`, `ORDER_CONFIRMED (-qty)`, `TRANSFER_IN / OUT`, and `SPOILAGE / DAMAGED (-qty)` from `inventory_movements` (`REQ-008`).
4. **Unified Audit Log Explorer (`/admin/audit-logs` — `FR-036`, `CLAUDE.md` §8):**
   - Queries `GET /api/v1/admin/audit-logs` across all 6 mandatory categories (`LOGIN`, `ROLE_CHANGE`, `PRICE_CHANGE`, `ORDER_STATUS`, `REFUND`, `SETTING`) + deep link to `inventory_movements` (`REQ-008`). Clicking any row opens the `AuditDiffDrawer` (`CMP-14`) showing `oldValues` vs. `newValues` JSON and actor metadata.
5. **System Settings (`/admin/settings` — `FR-037`, `SYSTEM_OWNER` / `HEAD_OFFICE_ADMIN` only):**
   - Chain-wide operational parameters: default payment reservation expiration window (minutes, `B13`), default delivery fee per km / base fee (`B15`), default currency (`VND`, `B7`), and storefront support contact information.
