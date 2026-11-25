# Requirements Validation and Consistency Report

- **Status:** COMPLETED (Phase 3 — P04 Requirements Validation)
- **Last updated:** 2026-11-24
- **Owner:** business-analyst (`requirements-engineering` skill) · **Task:** TASK-010 (Phase 3 — P04 Requirements Engineering)
- **Scope Validated:**
  - `docs/requirements/REQUIREMENTS.md` (`REQ-001` to `REQ-017`)
  - `docs/requirements/functional-requirements.md` (`FR-001` to `FR-038`)
  - `docs/requirements/non-functional-requirements.md` (`NFR-001` to `NFR-015`)
  - `docs/requirements/roles-and-permissions.md`
  - `docs/requirements/traceability-matrices.md` (`TEST-001` to `TEST-020`)
  - `docs/business-analysis/business-rules-catalog.md` (`BRULE-001` to `BRULE-030`)

---

## 1. Duplicate Analysis

| Check | Finding | Resolution |
|---|---|---|
| `REQ-001`..`017` vs. `BRULE-001`..`017` vs. `FR-001`..`038` | `REQ-001`..`012` were established in P01 as the owner's baseline rules, while P03 introduced `BRULE-001`..`030` and P04 introduced `FR-001`..`038` and `NFR-001`..`015`. | **Resolved via explicit 1:1 hierarchy (no duplicate or conflicting IDs):**<br>• `REQ-001`..`017` remain the high-level **Owner Requirements Register** in `REQUIREMENTS.md`.<br>• `BRULE-001`..`030` in `business-rules-catalog.md` define the **Business Invariants** (`BRULE-001`..`017` map 1:1 to `REQ-001`..`017`).<br>• `FR-001`..`038` and `NFR-001`..`015` specify the **implementable system specifications** traced back to `REQ-xxx` and `BRULE-xxx` in `traceability-matrices.md`. |
| Inventory deduction rules across Order (`FR-017`), Transfer (`FR-018`), and Spoilage (`FR-019`) | All three workflows decrement branch stock and write audit logs. | Consolidated the shared non-negative stock invariant into **`FR-015`** (`BRULE-007`) and the shared atomic audit ledger invariant into **`FR-016`** (`BRULE-008`), so `FR-017`, `FR-018`, `FR-019`, and `FR-020` depend on `FR-015` and `FR-016` without duplicating constraint definitions. |

---

## 2. Omission Analysis

During P04 analysis against the P03 business model, the following potential omissions were identified and resolved:

1. **How stock enters a branch without a central warehouse (`REQ-016`):**
   - *Omission risk:* Since `B2` confirmed there is **no central warehouse** (`REQ-016`), branches would have zero initial stock unless a stock-in mechanism exists.
   - *Resolution:* Added **`FR-020` (Branch Goods Receiving / Stock-In)** and linked it to `REQ-002`, `REQ-008`, `REQ-016`, and open question `B19`.
2. **Perishable floral spoilage and physical count discrepancies:**
   - *Omission risk:* Fresh flowers wilt daily; without an audited write-off capability, online stock (`REQ-014`) would overstate actual sellable bouquets at branches.
   - *Resolution:* Added **`FR-019` (Spoilage, Damaged Goods Write-Off, and Stocktake Adjustments)** and `BRULE-021`.
3. **Branch coordinates / delivery zone configuration for automatic branch selection (`REQ-014`):**
   - *Omission risk:* `REQ-014` requires the system to pick the branch nearest the delivery address, which is impossible unless branches store geolocation/zone metadata.
   - *Resolution:* Added **`FR-008` (Branch Geolocation, Delivery Zone, and Operating Hours Configuration)** and `BRULE-018`.
4. **All 6 mandatory audit categories from `CLAUDE.md` §8:**
   - *Omission risk:* `REQ-008` covers inventory auditing, while `CLAUDE.md` §8 also mandates audit trails for logins, role changes, price changes, order status changes, and refunds.
   - *Resolution:* Explicitly specified audit capture in `FR-001` (logins), `FR-005` (role/branch assignments), `FR-013` (branch price changes), `FR-016` (inventory changes), `FR-022`/`FR-023`/`FR-024` (order status/branch changes), `FR-027` (refunds), and unified audit querying in **`FR-036`** and **`NFR-010`**.

---

## 3. Contradiction Analysis

| ID | Contradiction Identified | Impacted Requirements | Proposed Resolution (Awaiting Owner Sign-Off) |
|---|---|---|---|
| **CON-01** | **Customer-Chosen Branch Price (`REQ-017` / `B9`) vs. System Auto-Selected Delivery Branch (`REQ-014` / `B3b`):**<br>In `B9`, the owner selected *"each branch has its own prices, and the storefront shows the price of the branch the customer chose"* (`REQ-017`). In `B3b`, the owner selected *"the system picks the branch nearest the delivery address that still has enough stock"* (`REQ-014`, explicitly not choosing *"the customer picks the branch"*). | `REQ-004`, `REQ-006`, `REQ-014`, `REQ-017`, `FR-014`, `FR-021` | Documented in **`FR-014`** and **`B11`**:<br>1. While browsing, the customer selects a browsing branch/area (or sees a default branch) so the storefront can display per-branch prices (`REQ-017`).<br>2. For **pickup orders**, the customer chooses the pickup branch (`B3c`) and pays that branch's price.<br>3. For **delivery orders**, the system auto-selects the nearest branch with stock (`REQ-014`). If the auto-selected branch has a different price than the browsing branch, checkout displays the updated branch price and requires customer confirmation before order placement and snapshot creation (`REQ-006`). |
| **CON-02** | **In-Store Pickup Orders (`REQ-013` / `B3a`) vs. Nearest-to-Delivery-Address Rule (`REQ-014` / `B3b`):**<br>A pickup order has no delivery address, so the `B3b` nearest-to-delivery-address rule cannot select the branch for pickup orders. | `REQ-005`, `REQ-013`, `REQ-014`, `FR-021`, `FR-029` | Documented in **`FR-021`** and **`B3c`**:<br>Scope `REQ-014` strictly to **home delivery** orders, and specify (`BRULE-022`) that for **in-store pickup** orders the customer selects an active branch with available stock as the pickup and processing branch. |
| **CON-03** | **Multi-Branch Order Splitting (`B3d` Option a) vs. Single Traceable Processing Branch (`REQ-005`):**<br>`REQ-005` states *"Every order has a traceable processing branch"* (`branch_id NOT NULL`). If `B3d` split a single order across two branches, a single `branch_id` column on `orders` would be violated. | `REQ-005`, `REQ-014`, `FR-021` | Documented in **`FR-021`** and **`B3d`**:<br>Propose for MVP that each order is fulfilled by a single branch (`1 order = 1 processing_branch_id`); if no single branch holds full stock for all items in the cart, checkout rejects the unavailable combination and shows available branch stock. |

---

## 4. Ambiguous Wording Eliminated

Every requirement in `functional-requirements.md` and `non-functional-requirements.md` was checked to replace vague phrasing with testable criteria:

- *"Fast response time"* → Replaced in **`NFR-001`** with `p95 <= 300 ms` (reads) / `<= 500 ms` (writes) and mandatory pagination (`max page size = 100`).
- *"Proper branch security"* → Replaced in **`FR-006`** and **`NFR-006`** with explicit server-resolved `authorizedBranchIds` filtering and `403`/`404` responses on mismatch.
- *"Accurate money calculations"* → Replaced in **`FR-013`** and **`NFR-009`** with Java `BigDecimal` and PostgreSQL `numeric` (zero `float`/`double`).
- *"Safe stock updates"* → Replaced in **`FR-015`** and **`NFR-009`** with row-level/optimistic locking, `CHECK (available_stock >= 0)`, and a 10-thread concurrency test (`TEST-008`).

---

## 5. Unapproved Assumptions Audit

- **Zero unapproved assumptions were silently turned into confirmed requirements.**
- Every capability that depends on an unanswered owner question (`B1`, `B3c–B3e`, `B4–B8`, `B9b`, `B10`, `B11–B22`, `T5–T12`) is explicitly labeled **`PROPOSED`** or **`NEEDS CONFIRMATION`** with its blocking question ID (`AC pending: Bx`).
- Specifically, low-justification or optional features — **Product Variants/Seasonal Windows (`FR-011` / `B18`)**, **Branch Catalog Visibility Overrides (`FR-012` / `B10`)**, **Promotional Vouchers (`FR-032` / `B6`)**, and **Walk-In Store POS (`FR-038` / `B1`)** — are isolated as conditional `Could Have` requirements so they will not bloat the Phase 4–8 build unless the owner explicitly approves them.
