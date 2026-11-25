# Business Rules Catalog (`BRULE-001` to `BRULE-030`)

- **Status:** DRAFT
- **Last updated:** 2026-11-24
- **Owner:** business-analyst · **Task:** TASK-006 (Phase 2 — P03 Business Analysis)
- **Sources:** Owner Brief (2026-11-23), Owner Phase 2 Answers (`B2`, `B3a`, `B3b`, `B9`, 2026-11-23), Project Rules Brief (2026-11-24), `CLAUDE.md`, `REQUIREMENTS.md`.
- **Labels:**
  - **CONFIRMED:** Stated or chosen by the project owner; mandatory for all architecture, database, backend, and frontend work.
  - **INFERRED:** Necessary consequence of confirmed rules.
  - **PROPOSED / NEEDS CONFIRMATION:** Candidate rule awaiting an owner decision in `docs/requirements/open-questions.md`.

Note: `BRULE-001` through `BRULE-012` map 1:1 to the owner's 12 baseline guardrails `REQ-001` through `REQ-012`. `BRULE-013` through `BRULE-017` map 1:1 to `REQ-013` through `REQ-017`. `BRULE-018` onwards cover additional audit, security, and candidate workflow rules.

---

## 1. Global & Multi-Branch Organization Rules

| Rule ID | Linked REQ | Rule Statement | Classification | Source / Open Question |
|---|---|---|---|---|
| **BRULE-001** | `REQ-001` | One business entity owns and operates multiple flower shop branches (initial target: 5–30 branches). | **CONFIRMED** | Owner brief, 2026-11-23 |
| **BRULE-016** | `REQ-016` | There is no central warehouse; 100% of physical inventory is held directly by branches. | **CONFIRMED** | Owner answer `B2`, 2026-11-23 |
| **BRULE-018** | `REQ-001`, `REQ-014` | Every active branch must maintain a defined physical location / delivery coverage definition so the system can determine proximity for delivery orders and display pickup locations. | **INFERRED** | Follows from `B3a`, `B3b` (`REQ-013`, `REQ-014`); distance formula open (`B3e`) |

---

## 2. Catalog & Branch Pricing Rules

| Rule ID | Linked REQ | Rule Statement | Classification | Source / Open Question |
|---|---|---|---|---|
| **BRULE-003** | `REQ-003` | The product catalog (products, SKUs, categories) is managed centrally by default, unless approved requirements allow branch-level overrides. | **CONFIRMED** | Owner brief, 2026-11-23; branch overrides open (`B10`) |
| **BRULE-004** | `REQ-004` | Product prices vary by branch; each branch has its own price for a product. | **CONFIRMED** | Owner brief & owner answer `B9`, 2026-11-23 |
| **BRULE-017** | `REQ-017` | The storefront displays product prices for a branch while the customer shops. | **NEEDS CONFIRMATION** | Owner answer `B9` conflicts with automatic branch selection `B3b`; resolve via **`B11`** |
| **BRULE-019** | `REQ-004` | Every change to a branch's product price must be recorded in the price-change audit trail (who, when, branch, product, old price, new price). | **CONFIRMED** | `CLAUDE.md` §8 ("Keep an audit trail for ... price changes"); who may set prices open (**`B9b`**) |
| **BRULE-020** | `REQ-004` | A product without a valid configured price at a branch cannot be sold from that branch unless a fallback pricing rule is approved. | **NEEDS CONFIRMATION** | Open under **`B9b`** & **`B10`** |

---

## 3. Inventory, Stock Transfer & Spoilage Rules

| Rule ID | Linked REQ | Rule Statement | Classification | Source / Open Question |
|---|---|---|---|---|
| **BRULE-002** | `REQ-002` | Every inventory record belongs strictly to one specific branch (`branch_id NOT NULL` foreign key). | **CONFIRMED** | Owner brief, 2026-11-23; `CLAUDE.md` §6 |
| **BRULE-007** | `REQ-007` | Available inventory at any branch must never become negative (`available_stock >= 0`), enforced both in service-layer locking and by a database `CHECK` constraint, including under concurrent requests. | **CONFIRMED** | Owner brief, 2026-11-23; `CLAUDE.md` §6, §9 |
| **BRULE-008** | `REQ-008` | Every inventory change (order reservation/deduction, cancellation restock, goods receipt, spoilage/damage write-off, manual adjustment, inter-branch transfer) must create an immutable audit record capturing who, when (UTC), why (reason/type), branch, quantity delta, and source document. | **CONFIRMED** | Owner brief, 2026-11-23; `CLAUDE.md` §6 |
| **BRULE-015** | `REQ-015` | Branches may transfer stock to each other, and every inter-branch transfer must be recorded and linked to paired inventory audit records (`-qty` at sending branch, `+qty` at receiving branch). | **CONFIRMED** | Owner answer `B2`, 2026-11-23; approval/in-transit/discrepancy details open (**`B12`**, **`B16`**) |
| **BRULE-021** | `REQ-007`, `REQ-008` | Spoilage, damaged goods, and physical stocktake adjustments at a branch must deduct from that branch's inventory via an audited adjustment and must fail if the deduction would reduce available stock below zero. | **INFERRED** | Follows directly from `REQ-002`, `REQ-007`, `REQ-008`; approval thresholds open (**`B19`**) |

---

## 4. Order, Allocation, Delivery & Pickup Rules

| Rule ID | Linked REQ | Rule Statement | Classification | Source / Open Question |
|---|---|---|---|---|
| **BRULE-005** | `REQ-005` | Every order must have a non-null, traceable processing branch (`branch_id NOT NULL`). | **CONFIRMED** | Owner brief, 2026-11-23; `CLAUDE.md` §6 |
| **BRULE-006** | `REQ-006` | Order lines must copy and persist immutable snapshots of product name, SKU, and unit price at order time; historical orders must never read live catalog prices. | **CONFIRMED** | Owner brief, 2026-11-23; `CLAUDE.md` §6 |
| **BRULE-013** | `REQ-013` | For every online order, the customer must choose either **home delivery** or **in-store pickup**. | **CONFIRMED** | Owner answer `B3a`, 2026-11-23 |
| **BRULE-014** | `REQ-014` | For a home delivery order, the system automatically selects the processing branch: the branch nearest the delivery address that currently has enough available stock for the order. | **CONFIRMED** | Owner answer `B3b`, 2026-11-23; distance metric (**`B3e`**) and multi-branch stock shortfall (**`B3d`**) open |
| **BRULE-022** | `REQ-005`, `REQ-013` | For an in-store pickup order, the pickup store and its processing branch must be deterministically recorded at checkout. | **NEEDS CONFIRMATION** | Whether the customer selects the pickup store and whether it is always the processing branch is open under **`B3c`** |
| **BRULE-023** | `REQ-005` | Every order status change (and any authorized branch reassignment) must be recorded in an order audit trail with actor, UTC timestamp, previous state, new state, and reason. | **CONFIRMED** | `CLAUDE.md` §8 ("Keep an audit trail for ... order status changes"); reassignment rules open (**`B14`**, **`B17`**) |

---

## 5. Payment & Refund Rules

| Rule ID | Linked REQ | Rule Statement | Classification | Source / Open Question |
|---|---|---|---|---|
| **BRULE-009** | `REQ-009` | All payment provider webhooks must be cryptographically verified on the server using the provider's official verification mechanism before any order or inventory side effect occurs. Browser redirects are never treated as proof of payment. | **CONFIRMED** | Owner brief, 2026-11-23; `CLAUDE.md` §8 |
| **BRULE-010** | `REQ-010` | The same payment or webhook event must never be processed twice, enforced by a database `UNIQUE` constraint on provider event/transaction IDs and an idempotent handler. | **CONFIRMED** | Owner brief, 2026-11-23; `CLAUDE.md` §6, §8 |
| **BRULE-024** | `REQ-009` | FlowerChain must never store raw payment card numbers (PAN), CVV, or sensitive payment credentials. | **CONFIRMED** | `CLAUDE.md` §8 ("Never store card data") |
| **BRULE-025** | – | Every payment refund must be authorized and recorded in the refund audit trail with actor, UTC timestamp, order reference, amount, and reason. | **CONFIRMED** | `CLAUDE.md` §8 ("Keep an audit trail for ... refunds"); refund workflow open (**`B4`**, **`B20`**) |
| **BRULE-026** | `REQ-010` | Client endpoints that create orders or initiate payments must support idempotent retries via an `Idempotency-Key` header. | **PROPOSED** | `CLAUDE.md` §7 (`DEC-010`, confirm in Phase 4) |

---

## 6. Security, Authorization & Audit Rules

| Rule ID | Linked REQ | Rule Statement | Classification | Source / Open Question |
|---|---|---|---|---|
| **BRULE-011** | `REQ-011` | The backend must enforce branch-level authorization on every branch-owned read and write operation. | **CONFIRMED** | Owner brief, 2026-11-23; `CLAUDE.md` §8 |
| **BRULE-012** | `REQ-012` | A `branchId` sent by any client (path, query, header, or body) is never trusted without checking it against the authenticated user's server-resolved authorized branches. | **CONFIRMED** | Owner brief, 2026-11-23; `CLAUDE.md` §8 |
| **BRULE-027** | `REQ-011` | Customers may view and act only on their own orders and profile data; cross-customer access must be denied (`403` or `404` to avoid data leakage). | **CONFIRMED** | `CLAUDE.md` §7, §8 |
| **BRULE-028** | – | All API endpoints deny access by default unless explicitly permitted by Spring Security configuration. | **CONFIRMED** | `CLAUDE.md` §8 |
| **BRULE-029** | – | User passwords must be hashed using `DelegatingPasswordEncoder` (`bcrypt` or stronger), and secrets/tokens/passwords/full payment payloads must never be logged. | **CONFIRMED** | `CLAUDE.md` §5, §8 |
| **BRULE-030** | – | Logins and staff role/branch-assignment changes must be recorded in the security audit trail. | **CONFIRMED** | `CLAUDE.md` §8 ("Keep an audit trail for logins, role changes...") |
