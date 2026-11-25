# Stakeholder Analysis and Organizational Structure

- **Status:** DRAFT
- **Last updated:** 2026-11-24
- **Owner:** business-analyst · **Task:** TASK-006 (Phase 2 — P03 Business Analysis)
- **Sources:** Owner Brief (2026-11-23), Owner Phase 2 Answers (`B2`, `B3a`, `B3b`, `B9`, 2026-11-23), `CLAUDE.md` §1, §2, §8.
- **Labels:** `CONFIRMED` · `INFERRED` · `PROPOSED` · `NEEDS CONFIRMATION`

---

## 1. Organizational Structure

```text
                        ┌───────────────────────────────┐
                        │  Business Owner / Head Office │  [CONFIRMED: 1 business owner, REQ-001]
                        │  (Central Catalog & Oversight)│  [CONFIRMED: central catalog, REQ-003]
                        └───────────────┬───────────────┘
                                        │
            ┌───────────────────────────┼───────────────────────────┐
            ▼                           ▼                           ▼
┌───────────────────────┐   ┌───────────────────────┐   ┌───────────────────────┐
│       Branch 1        │◄─►│       Branch 2        │◄─►│    Branch N (5–30)    │
│ • Own Inventory       │   │ • Own Inventory       │   │ • Own Inventory       │
│ • Own Branch Prices   │   │ • Own Branch Prices   │   │ • Own Branch Prices   │
│ • Order Fulfilment    │   │ • Order Fulfilment    │   │ • Order Fulfilment    │
└───────────────────────┘   └───────────────────────┘   └───────────────────────┘
  [CONFIRMED: REQ-002, REQ-004, REQ-005, REQ-015, REQ-016 — no central warehouse]
```

---

## 2. Stakeholder & Actor Catalog

| Stakeholder / Actor | Category | Goals & Responsibilities | System Touchpoint | Classification & Source |
|---|---|---|---|---|
| **Business Owner / Super Admin** | Internal — Head Office | Owns the chain across all 5–30 branches; oversees chain-wide revenue, branch setup, staff role assignments, and business rules. | Admin App (all branches) | **CONFIRMED** (`REQ-001`, `CLAUDE.md` §1); exact permissions **NEEDS CONFIRMATION (`B5`)** |
| **Head Office Staff / Catalog Manager** | Internal — Head Office | Manages the centralized product catalog (`REQ-003`), categories, and potentially coordinates chain pricing (`B9b`), inter-branch transfers (`B12`), or exception order reassignments (`B14`). | Admin App (central / multi-branch scope) | **INFERRED** from `REQ-003` & `CLAUDE.md` §1; role split **NEEDS CONFIRMATION (`B5`)** |
| **Branch Manager** | Internal — Branch | Oversees one specific branch (or assigned set of branches): manages branch stock, stock adjustments/spoilage, inter-branch transfers, branch orders, and potentially branch-specific prices (`B9b`). | Admin App (scoped strictly to assigned `branchId`s, `REQ-011`, `REQ-012`) | **PROPOSED / NEEDS CONFIRMATION (`B5`, `B9b`, `B12`)** |
| **Branch Staff / Florist** | Internal — Branch | Prepares floral arrangements for orders assigned to their branch (`REQ-005`), hands over pickup orders (`REQ-013`), receives goods, and packs inter-branch transfers (`REQ-015`). | Admin App (scoped strictly to assigned `branchId`, `REQ-011`, `REQ-012`) | **INFERRED** from `CLAUDE.md` §1 & `REQ-005`; exact permissions **NEEDS CONFIRMATION (`B5`)** |
| **Delivery Rider / Courier** | Internal or External | Delivers prepared orders from the processing branch to the customer's delivery address (`REQ-013`). Could be in-house branch delivery staff or a third-party courier service. | Admin App / Mobile view or manual status update by branch staff | **NEEDS CONFIRMATION (`B5`, `B15`)** |
| **Customer** | External — Buyer | Browses products and branch prices (`REQ-004`), places delivery or pickup orders (`REQ-013`), pays, tracks order status, and receives flowers. Sees only their own orders (`CLAUDE.md` §8). | Customer Storefront (`frontend/storefront`) | **CONFIRMED** (`CLAUDE.md` §1, §8, `B3a`, `B9`); guest vs. registered account **NEEDS CONFIRMATION (`B6`)** |
| **Payment Provider** | External System | Processes online customer payments and sends signed server-to-server payment webhooks (`REQ-009`, `REQ-010`). | Backend Webhook API (`/api/v1/...`) | **CONFIRMED** (`REQ-009`, `REQ-010`); specific provider(s) **NEEDS CONFIRMATION (`B4`)** |
| **Flower / Material Supplier** | External Partner | Supplies fresh cut flowers, vases, and wrapping materials directly to branches (since there is no central warehouse, `REQ-016`). | Out of system or recorded via branch Goods Receiving | **INFERRED** (`REQ-016`); system tracking scope **NEEDS CONFIRMATION (`B19`)** |

---

## 3. Proposed Role & Branch-Scope Matrix (For Owner Decision under `B5`)

All entries below are **PROPOSED** to help the owner answer **`B5`**, except the security invariants which are **CONFIRMED** (`REQ-011`, `REQ-012`, `CLAUDE.md` §8):

| Capability | Customer (`CONFIRMED` isolation) | Branch Staff (`PROPOSED`) | Branch Manager (`PROPOSED`) | Head Office / Owner (`PROPOSED`) | Governing Rule / Open Question |
|---|---|---|---|---|---|
| Browse storefront & place order | Own orders only | N/A | N/A | N/A | `REQ-013`, `CLAUDE.md` §8 |
| Manage branches & staff accounts | No | No | View own branch | Full CRUD across chain | `REQ-001`, `B5` |
| Manage central product catalog | Read-only (storefront) | Read-only | Read-only | Full CRUD | `REQ-003`, `B5`, `B10` |
| Set / update branch product prices | Read-only (storefront) | No | Own branch (`B9b`?) | All branches (`B9b`?) | `REQ-004`, `B9b` |
| View & update branch inventory (receiving, spoilage, adjustment) | No | Own branch (view / record?) | Own branch (full / approve) | All branches | `REQ-002`, `REQ-007`, `REQ-008`, `REQ-011`, `B5`, `B19` |
| Request / approve / receive inter-branch stock transfer | No | Pack / confirm receipt at own branch? | Request / approve for own branch? | View / approve across chain? | `REQ-015`, `B12`, `B16` |
| View & process online orders | Own customer orders | Assigned processing branch only | Assigned processing branch only | All branches | `REQ-005`, `REQ-011`, `REQ-012`, `B17` |
| Reassign order to another branch | No | No | Request only? | Approve / reassign? | `REQ-005`, `B14` |
| Trigger / record refund | Request cancellation? | No | Own branch? | All branches | `CLAUDE.md` §8, `B4`, `B20` |
| View audit logs & revenue reports | No | No | Own branch summary | All branches & full audit trails | `REQ-008`, `CLAUDE.md` §8, `B21` |
