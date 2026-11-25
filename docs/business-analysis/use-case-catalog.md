# Use Case Catalog

- **Status:** DRAFT
- **Last updated:** 2026-11-24
- **Owner:** business-analyst · **Task:** TASK-006 (Phase 2 — P03 Business Analysis)
- **Sources:** Owner Brief (2026-11-23), Owner Phase 2 Answers (`B2`, `B3a`, `B3b`, `B9`, 2026-11-23), `REQUIREMENTS.md` (`REQ-001` to `REQ-017`).
- **Labels:** `CONFIRMED` · `INFERRED` · `PROPOSED` · `NEEDS CONFIRMATION`

---

## 1. Customer Storefront Use Cases (`frontend/storefront`)

| Use Case ID | Use Case Name | Primary Actor | Linked Workflow | Linked REQs / BRULEs | Classification & Open Questions |
|---|---|---|---|---|---|
| **UC-CUST-01** | Browse Product Catalog & Branch Prices | Customer | WF-01 | `REQ-003`, `REQ-004`, `REQ-017` | **CONFIRMED** (per-branch prices `B9`); how browsing branch is chosen is **NEEDS CONFIRMATION (`B11`)** |
| **UC-CUST-02** | Manage Shopping Cart | Customer | WF-01 | `REQ-006`, `REQ-013` | **INFERRED** (required for online checkout) |
| **UC-CUST-03** | Place Online Home Delivery Order | Customer | WF-01..05 | `REQ-005`..`008`, `REQ-013`, `REQ-014` | **CONFIRMED** (`B3a`, `B3b`); distance/shortfall/slots **NEEDS CONFIRMATION (`B3d`, `B3e`, `B11`, `B13`, `B15`)** |
| **UC-CUST-04** | Place Online In-Store Pickup Order | Customer | WF-01..05 | `REQ-005`..`008`, `REQ-013` | **CONFIRMED** (`B3a`); who selects pickup store **NEEDS CONFIRMATION (`B3c`, `B15`)** |
| **UC-CUST-05** | Pay for Online Order | Customer, Payment Provider | WF-05 | `REQ-009`, `REQ-010` | **CONFIRMED** (server verification & idempotency); payment providers **NEEDS CONFIRMATION (`B4`)** |
| **UC-CUST-06** | View Own Order Status & History | Customer | WF-06, WF-07 | `REQ-005`, `REQ-006`, `CLAUDE.md` §8 | **CONFIRMED** (customers see only their own orders); account/guest lookup **NEEDS CONFIRMATION (`B6`, `B17`)** |
| **UC-CUST-07** | Request Order Cancellation or Return | Customer | WF-08, WF-12 | `REQ-008`, `CLAUDE.md` §8 | **NEEDS CONFIRMATION (`B17`, `B20`)** |
| **UC-CUST-08** | Apply Voucher or Promotion Code | Customer | WF-01 | `REQ-006` | **NEEDS CONFIRMATION (`B6`)** |

---

## 2. Branch Operations Use Cases (`frontend/admin` — Branch Scope)

All branch use cases enforce server-side branch-level authorization (`REQ-011`, `REQ-012`): a branch actor can only read or mutate data for their server-authorized `branchId`s.

| Use Case ID | Use Case Name | Primary Actor | Linked Workflow | Linked REQs / BRULEs | Classification & Open Questions |
|---|---|---|---|---|---|
| **UC-BR-01** | View & Filter Assigned Branch Orders | Branch Staff, Branch Manager | WF-06 | `REQ-005`, `REQ-011`, `REQ-012` | **CONFIRMED** (`REQ-005`, `REQ-011`); exact role permissions **NEEDS CONFIRMATION (`B5`)** |
| **UC-BR-02** | Prepare Order & Update Fulfilment Status | Branch Staff, Branch Manager | WF-06, WF-07 | `REQ-005`, `REQ-011`, `REQ-013` | **CONFIRMED** (branch processes order); exact status machine **NEEDS CONFIRMATION (`B17`)** |
| **UC-BR-03** | Complete In-Store Pickup Hand-Over | Branch Staff | WF-07 | `REQ-005`, `REQ-011`, `REQ-013` | **CONFIRMED** (`B3a`); verification steps **NEEDS CONFIRMATION (`B15`, `B17`)** |
| **UC-BR-04** | Dispatch & Complete Home Delivery | Branch Staff / Delivery Rider | WF-07 | `REQ-005`, `REQ-011`, `REQ-013` | **CONFIRMED** (`B3a`); internal rider vs. courier **NEEDS CONFIRMATION (`B15`, `B17`)** |
| **UC-BR-05** | View Branch Inventory & Stock Audit Trail | Branch Staff, Branch Manager | WF-04, WF-09..11 | `REQ-002`, `REQ-007`, `REQ-008`, `REQ-011` | **CONFIRMED** (`REQ-002`, `REQ-008`, `REQ-011`) |
| **UC-BR-06** | Record Goods Receipt (Branch Stock-In) | Branch Manager / Staff | WF-09 | `REQ-002`, `REQ-008`, `REQ-011`, `REQ-016` | **INFERRED** (no central warehouse `REQ-016`); PO vs. direct stock-in **NEEDS CONFIRMATION (`B19`)** |
| **UC-BR-07** | Record Spoilage, Damage, or Stock Adjustment | Branch Manager / Staff | WF-10 | `REQ-002`, `REQ-007`, `REQ-008`, `REQ-011` | **INFERRED** (perishable flowers + `REQ-008`); approval threshold **NEEDS CONFIRMATION (`B19`)** |
| **UC-BR-08** | Request, Dispatch, or Receive Stock Transfer | Branch Manager / Staff | WF-11 | `REQ-007`, `REQ-008`, `REQ-011`, `REQ-015` | **CONFIRMED** (`B2`, `REQ-015`); workflow states & exceptions **NEEDS CONFIRMATION (`B12`, `B16`)** |
| **UC-BR-09** | Manage Branch Product Prices | Branch Manager or Head Office | WF-01 | `REQ-004`, `REQ-011`, `CLAUDE.md` §8 | **CONFIRMED** (per-branch prices `B9`); who sets them **NEEDS CONFIRMATION (`B9b`)** |
| **UC-BR-10** | Record Walk-In Store Sale (POS) | Branch Staff | [walk-in-sale-scope.md](processes/walk-in-sale-scope.md) | `REQ-002`, `REQ-004`, `REQ-006`..`008`, `REQ-011` | **NEEDS CONFIRMATION (`B1`)** |

---

## 3. Head Office & Chain Administration Use Cases (`frontend/admin` — Central Scope)

| Use Case ID | Use Case Name | Primary Actor | Linked Workflow | Linked REQs / BRULEs | Classification & Open Questions |
|---|---|---|---|---|---|
| **UC-HO-01** | Manage Branches (Create, Configure Location/Zones, Activate/Deactivate) | Business Owner / Head Office | WF-02, WF-03 | `REQ-001`, `REQ-014` | **CONFIRMED** (`REQ-001`, `REQ-014`); delivery zone configuration **NEEDS CONFIRMATION (`B3e`)** |
| **UC-HO-02** | Manage Central Product Catalog & Categories | Head Office / Catalog Manager | WF-01 | `REQ-003` | **CONFIRMED** (`REQ-003`); branch overrides/variants **NEEDS CONFIRMATION (`B10`, `B18`)** |
| **UC-HO-03** | Manage Staff Accounts, Roles & Branch Assignments | Business Owner / Admin | All Admin WFs | `REQ-011`, `REQ-012`, `CLAUDE.md` §8 | **CONFIRMED** (`REQ-011`, `CLAUDE.md` §8); role matrix **NEEDS CONFIRMATION (`B5`)** |
| **UC-HO-04** | Oversee / Approve Inter-Branch Stock Transfers | Head Office | WF-11 | `REQ-015`, `REQ-008` | **NEEDS CONFIRMATION (`B12`, `B16`)** |
| **UC-HO-05** | Reassign Order Processing Branch (Exception Handling) | Head Office / Authorized Manager | WF-03, WF-06 | `REQ-005`, `CLAUDE.md` §8 | **NEEDS CONFIRMATION (`B14`)** |
| **UC-HO-06** | Process Order Refund & Post-Delivery Returns | Head Office / Branch Manager | WF-08, WF-12 | `CLAUDE.md` §8 | **CONFIRMED** (refund audit trail); refund rules **NEEDS CONFIRMATION (`B4`, `B20`)** |
| **UC-HO-07** | View Chain-Wide Revenue, Inventory & Security Audit Reports | Business Owner / Head Office | All WFs | `REQ-005`, `REQ-006`, `REQ-008`, `CLAUDE.md` §8 | **CONFIRMED** (audit trails); specific dashboards/exports **NEEDS CONFIRMATION (`B21`)** |
| **UC-HO-08** | Manage Promotions and Vouchers | Head Office | WF-01 | `REQ-006` | **NEEDS CONFIRMATION (`B6`)** |
