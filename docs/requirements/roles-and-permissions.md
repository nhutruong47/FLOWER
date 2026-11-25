# Users, Roles, Permissions, and Branch Scope Specification

- **Status:** DRAFT (PROPOSED role matrix awaiting owner sign-off under `B5`; branch-isolation invariants `REQ-011` and `REQ-012` are CONFIRMED)
- **Last updated:** 2026-11-24
- **Owner:** business-analyst (`requirements-engineering` skill) · **Task:** TASK-010 (Phase 3 — P04 Requirements Engineering)
- **Sources:** `REQ-011`, `REQ-012`, `CLAUDE.md` §8, P03 `docs/business-analysis/stakeholders-and-actors.md`, `open-questions.md` (`B5`, `B9b`, `B12`, `B14`).

---

## 1. Role Definitions and Data Scope Boundaries

Per `REQ-011` (`BRULE-011`) and `REQ-012` (`BRULE-012`), the backend resolves the authenticated user's role and authorized `branchId` list on the server. A `branchId` supplied by the frontend is **never** trusted without checking it against the user's server-resolved scope.

| Role Code | Role Name | Organizational Level | Data Scope Boundary | Classification & Source |
|---|---|---|---|---|
| `SYSTEM_OWNER` | System Owner (Business Owner) | Chain-wide (Head Office) | **Global (`ALL_BRANCHES`):** Full administrative and operational access across all 5–30 branches, system settings, staff roles, and audit trails. | **CONFIRMED** (`REQ-001`, `CLAUDE.md` §1); exact permissions **PROPOSED (`B5`)** |
| `HEAD_OFFICE_ADMIN` | Head Office Administrator | Chain-wide (Head Office) | **Global (`ALL_BRANCHES`):** Manages central product catalog (`REQ-003`), branches, staff assignments, chain-wide reporting, transfer approvals (`B12`), order reassignments (`B14`), and branch prices (`B9b`). | **PROPOSED (`B5`)** based on `CLAUDE.md` §1 & `REQ-003` |
| `BRANCH_MANAGER` | Branch Manager | Branch-level | **Assigned Branch(es) (`ASSIGNED_BRANCHES_ONLY`):** Full operational control over their assigned `branchId`(s): branch inventory, goods receipts, spoilage/adjustments, inter-branch transfer requests/receipts, branch order fulfilment, branch reports, and branch prices (if approved under `B9b`). | **PROPOSED (`B5`, `B9b`, `B12`)**; branch isolation **CONFIRMED (`REQ-011`, `REQ-012`)** |
| `SALES_STAFF` | Branch Sales / Florist Staff | Branch-level | **Assigned Branch (`ASSIGNED_BRANCH_ONLY`):** Views and prepares online orders assigned to their branch (`REQ-005`), completes in-store pickup hand-overs (`REQ-013`), and records walk-in sales if `B1` Option A is approved. Cannot modify catalog, branch prices, or other branches' data. | **PROPOSED (`B5`)**; branch isolation **CONFIRMED (`REQ-011`, `REQ-012`)** |
| `INVENTORY_STAFF` | Branch Inventory Staff | Branch-level | **Assigned Branch (`ASSIGNED_BRANCH_ONLY`):** Views branch stock, records goods receipts (`FR-020`), records spoilage/damage write-offs (`FR-019`), and packs/dispatches or receives inter-branch transfers (`FR-018`) for their assigned branch. | **PROPOSED (`B5`, `B12`, `B19`)**; branch isolation **CONFIRMED (`REQ-011`, `REQ-012`)** |
| `DELIVERY_STAFF` | Branch Delivery Staff | Branch-level | **Assigned Branch / Assigned Deliveries (`ASSIGNED_BRANCH_ONLY`):** Views delivery orders (`REQ-013`) ready for dispatch at their assigned branch, transitions delivery status (`OUT_FOR_DELIVERY` → `COMPLETED`), and records delivery hand-over notes. | **PROPOSED (`B5`, `B15`)**; branch isolation **CONFIRMED (`REQ-011`, `REQ-012`)** |
| `CUSTOMER` | Storefront Customer | External Customer | **Self Only (`OWN_CUSTOMER_ID_ONLY`):** Views public catalog and branch prices, places delivery or pickup orders, and views/cancels only their own orders (`CLAUDE.md` §8, `BRULE-027`). | **CONFIRMED** (`CLAUDE.md` §1, §8, `BRULE-027`) |

*(Note for small branches: A single staff user account may hold multiple branch roles — e.g., both `SALES_STAFF` and `INVENTORY_STAFF` at Branch A — or `BRANCH_MANAGER` can encompass all branch-level duties.)*

---

## 2. Role-Permission and Branch Scope Matrix

Legend:
- **`GLOBAL`**: Permitted across all branches in the chain.
- **`BRANCH`**: Permitted **only** when the target resource's `branch_id` is in the authenticated user's server-resolved `authorizedBranchIds` (`REQ-011`, `REQ-012`).
- **`SELF`**: Permitted **only** when the target resource's `customer_id` matches the authenticated customer (`BRULE-027`).
- **`PUBLIC`**: Permitted on public active storefront data.
- **`DENY`**: Denied by default (`401`/`403`, or `404` when confirming existence would leak data).
- **`? (Bx)`**: Proposed default shown; final decision pending open question `Bx`.

| Functional Requirement / Capability | `SYSTEM_OWNER` | `HEAD_OFFICE_ADMIN` | `BRANCH_MANAGER` | `SALES_STAFF` | `INVENTORY_STAFF` | `DELIVERY_STAFF` | `CUSTOMER` | Open Question |
|---|---|---|---|---|---|---|---|---|
| **FR-004 / FR-005** Manage staff accounts, roles & branch assignments | `GLOBAL` | `GLOBAL` | `BRANCH` (Read-only) | `DENY` | `DENY` | `DENY` | `DENY` | `B5` |
| **FR-007 / FR-008** Create/edit branches, geolocation & delivery zones | `GLOBAL` | `GLOBAL` | `BRANCH` (Read-only) | `BRANCH` (Read-only) | `BRANCH` (Read-only) | `BRANCH` (Read-only) | `DENY` | `B5`, `B3e` |
| **FR-009** View active public branch directory | `GLOBAL` | `GLOBAL` | `GLOBAL` | `GLOBAL` | `GLOBAL` | `GLOBAL` | `PUBLIC` | – |
| **FR-010 / FR-011** Create/edit central product catalog, categories & SKUs | `GLOBAL` | `GLOBAL` | `GLOBAL` (Read-only) | `GLOBAL` (Read-only) | `GLOBAL` (Read-only) | `DENY` | `PUBLIC` (Active only) | `B5`, `B18` |
| **FR-012** Toggle branch-specific product visibility | `GLOBAL` ? | `GLOBAL` ? | `BRANCH` ? | `DENY` | `DENY` | `DENY` | `DENY` | `B10` |
| **FR-013** Configure branch product prices | `GLOBAL` | `GLOBAL` ? | `BRANCH` ? | `DENY` | `DENY` | `DENY` | `DENY` | `B9b` |
| **FR-014** View storefront branch prices & checkout quote | `GLOBAL` | `GLOBAL` | `GLOBAL` | `GLOBAL` | `GLOBAL` | `GLOBAL` | `PUBLIC` | `B11` |
| **FR-015 / FR-016** View branch inventory balances & inventory audit trail | `GLOBAL` | `GLOBAL` | `BRANCH` | `BRANCH` (Balances) | `BRANCH` | `DENY` | `DENY` | `B5` |
| **FR-018** Request inter-branch stock transfer | `GLOBAL` | `GLOBAL` | `BRANCH` | `DENY` | `BRANCH` ? | `DENY` | `DENY` | `B12` |
| **FR-018** Approve inter-branch stock transfer | `GLOBAL` | `GLOBAL` ? | `BRANCH` (Sender) ? | `DENY` | `DENY` | `DENY` | `DENY` | `B12` |
| **FR-018** Dispatch (`-qty` at sender) / Receive (`+qty` at receiver) transfer | `GLOBAL` | `GLOBAL` | `BRANCH` (Own side) | `DENY` | `BRANCH` (Own side) | `DENY` | `DENY` | `B12`, `B16` |
| **FR-019** Record spoilage, damaged goods, or stocktake adjustment | `GLOBAL` | `GLOBAL` | `BRANCH` | `DENY` | `BRANCH` | `DENY` | `DENY` | `B19` |
| **FR-020** Record branch Goods Receipt (stock-in) | `GLOBAL` | `GLOBAL` | `BRANCH` | `DENY` | `BRANCH` | `DENY` | `DENY` | `B19` |
| **FR-021** Place online delivery or pickup order | `DENY` | `DENY` | `DENY` | `DENY` | `DENY` | `DENY` | `SELF` / Guest (`B6`) | `B3c–e`, `B6` |
| **FR-022** View branch order queue & transition preparation statuses | `GLOBAL` | `GLOBAL` | `BRANCH` | `BRANCH` | `DENY` | `BRANCH` (Delivery only) | `SELF` (Read-only) | `B5`, `B17` |
| **FR-023** Reassign order processing branch (exception handling) | `GLOBAL` | `GLOBAL` | `BRANCH` (Request?) | `DENY` | `DENY` | `DENY` | `DENY` | `B14` |
| **FR-024** Cancel order prior to cutoff | `GLOBAL` | `GLOBAL` | `BRANCH` | `DENY` | `DENY` | `DENY` | `SELF` (Before `PREPARING`) | `B17` |
| **FR-027 / FR-030** Approve/record payment refund or post-delivery return | `GLOBAL` | `GLOBAL` | `BRANCH` ? | `DENY` | `DENY` | `DENY` | `SELF` (Request return) | `B4`, `B20` |
| **FR-028** Dispatch & complete home delivery (`OUT_FOR_DELIVERY` → `COMPLETED`) | `GLOBAL` | `GLOBAL` | `BRANCH` | `BRANCH` ? | `DENY` | `BRANCH` | `DENY` | `B5`, `B15` |
| **FR-029** Complete in-store pickup hand-over (`READY_FOR_PICKUP` → `COMPLETED`) | `GLOBAL` | `GLOBAL` | `BRANCH` | `BRANCH` | `DENY` | `DENY` | `DENY` | `B5`, `B15` |
| **FR-031** Manage own customer profile & saved delivery addresses | `DENY` | `DENY` | `DENY` | `DENY` | `DENY` | `DENY` | `SELF` | `B6` |
| **FR-032** Create/manage promotional vouchers (if in scope) | `GLOBAL` | `GLOBAL` | `DENY` | `DENY` | `DENY` | `DENY` | `PUBLIC` (Apply at checkout) | `B6` |
| **FR-034 / FR-035** View revenue, order & inventory reports | `GLOBAL` | `GLOBAL` | `BRANCH` | `DENY` | `BRANCH` (Inventory only) | `DENY` | `DENY` | `B21` |
| **FR-036** Query security, financial & operational audit logs | `GLOBAL` | `GLOBAL` | `BRANCH` (Operational) | `DENY` | `DENY` | `DENY` | `DENY` | `B5` |
| **FR-037** Configure chain-wide system settings | `GLOBAL` | `GLOBAL` | `DENY` | `DENY` | `DENY` | `DENY` | `DENY` | `B5` |
| **FR-038** Record walk-in store sale (POS) (if `B1` Option A chosen) | `GLOBAL` | `GLOBAL` | `BRANCH` | `BRANCH` | `DENY` | `DENY` | `DENY` | `B1` |

---

## 3. Mandatory Authorization Enforcement Rules

1. **Server Context Authority (`REQ-011`, `REQ-012`):**
   - For any actor with `BRANCH` scope, the backend loads `Set<BranchId> authorizedBranches` from the authenticated security principal.
   - Any path variable (`/branches/{branchId}/...`), query parameter (`?branchId=...`), or request body field (`branchId`, `sourceBranchId`, `targetBranchId`) is validated against `authorizedBranches`.
   - For entity lookups by ID (e.g., `GET /api/v1/admin/orders/{orderId}` or `POST /api/v1/admin/transfers/{transferId}/receive`), the backend loads the entity's owning `branchId` (or uses a branch-scoped repository query `findByIdAndBranchIdIn(id, authorizedBranches)`) and rejects cross-branch access before returning or modifying the entity.
2. **Inter-Branch Stock Transfer Dual-Side Scoping (`FR-018`, `REQ-011`, `REQ-015`):**
   - A branch-scoped actor at Sending Branch A may only dispatch (`-qty`) a transfer where `sourceBranchId == A`.
   - A branch-scoped actor at Receiving Branch B may only confirm receipt (`+qty`) of a transfer where `targetBranchId == B`.
   - Neither branch may forge a transfer deduction or receipt on behalf of the other branch.
3. **Customer Isolation (`BRULE-027`):**
   - Storefront endpoints (`/api/v1/storefront/orders/{orderId}`) query by `(id, customerId)` and return `404 Not Found` on mismatch so order existence is never leaked across customers (`CLAUDE.md` §7).
