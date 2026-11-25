# Business Overview and Business Model

- **Status:** DRAFT
- **Last updated:** 2026-11-24
- **Owner:** business-analyst · **Task:** TASK-006 (Phase 2 — P03 Business Analysis)
- **Sources:**
  - Project Owner Brief (2026-11-23) & Project Rules Brief (2026-11-24)
  - Project Owner Phase 2 Answers (`B2`, `B3a`, `B3b`, `B9`, 2026-11-23)
  - P00 Workspace Inspection & P01 Project Governance (`CLAUDE.md`, `REQUIREMENTS.md`, `open-questions.md`)
- **Classification Legend:**
  - **CONFIRMED:** Explicitly stated or selected by the project owner.
  - **INFERRED:** Logically follows from confirmed owner statements or domain constraints.
  - **PROPOSED:** Recommended option for owner evaluation; not binding until approved.
  - **NEEDS CONFIRMATION:** Open business decision linked to `docs/requirements/open-questions.md`.

---

## 1. Business Overview & Value Proposition

- **[CONFIRMED]** **Single-Owner Multi-Branch Flower Chain:** FlowerChain is a full-stack management and e-commerce platform built for a single business owner operating multiple flower shop branches (`REQ-001` / `BRULE-001`).
- **[CONFIRMED]** **Target Scale:** Initial operational scale is **5 to 30 physical flower shop branches** (`CLAUDE.md` §1, Project Rules §1).
- **[CONFIRMED]** **Two Integrated Applications:**
  1. **Customer Storefront (`frontend/storefront`):** Public e-commerce website where customers browse floral products and place orders for **home delivery** or **in-store pickup** (`REQ-013`, `B3a`).
  2. **Internal Management System (`frontend/admin`):** Back-office application used by Head Office and branch staff to manage the catalog, branch pricing, branch inventory, inter-branch stock transfers, orders, and operations (`CLAUDE.md` §1, `DEC-005`).
- **[INFERRED]** **Core Value Proposition:**
  - **For Customers:** Convenient online ordering across the chain with choice of home delivery (automatically routed to the nearest branch with available stock) or in-store collection (`REQ-013`, `REQ-014`).
  - **For Head Office / Business Owner:** Centralized product catalog control (`REQ-003`), branch-level inventory visibility and transfer tracking (`REQ-002`, `REQ-015`), strict audit trails (`REQ-008`), and protection against negative stock (`REQ-007`) and duplicate payments (`REQ-010`).
  - **For Branches:** Clear operational data isolation (`REQ-011`, `REQ-012`), local branch pricing flexibility (`REQ-004`), and traceable order assignment (`REQ-005`).

---

## 2. Business Model & 17 Domain Areas Analysis

### 2.1 Business Model & Ownership Structure
- **[CONFIRMED]** One single business entity owns and operates all branches (not a multi-tenant SaaS marketplace or independent franchise network) (`REQ-001` / `BRULE-001`).
- **[CONFIRMED]** There is **no central warehouse**; all physical inventory resides directly at individual branches (`REQ-016` / `BRULE-016`, `B2`).

### 2.2 Stakeholders & Organizational Structure
- **[CONFIRMED]** Key stakeholder groups are Customers, Branch Staff, and Head Office / Business Owner (`CLAUDE.md` §1).
- **[NEEDS CONFIRMATION]** Exact internal staff roles (e.g., Super Admin / Owner, Head Office Manager, Branch Manager, Florist / Branch Staff, Delivery Rider) and their permission matrix are open under **`B5`**. Detailed stakeholder analysis is in [stakeholders-and-actors.md](stakeholders-and-actors.md).

### 2.3 Head Office vs. Branch Operations
- **[CONFIRMED]** Product catalog management is centralized by default (`REQ-003` / `BRULE-003`).
- **[CONFIRMED]** Operational data (inventory, order processing) is isolated per branch and protected by server-side branch-level authorization (`REQ-002`, `REQ-005`, `REQ-011`, `REQ-012`).
- **[NEEDS CONFIRMATION]** Division of authority between Head Office and Branch Managers for setting branch prices (**`B9b`**), approving stock transfers (**`B12`**), and reassigning orders between branches (**`B14`**).

### 2.4 Product Catalog, Categories, Variants, and Seasonal Products
- **[CONFIRMED]** The product catalog is managed centrally (`REQ-003`), while product prices vary by branch (`REQ-004`, `B9`).
- **[INFERRED]** Every sellable catalog item has a name and SKU so that order lines can snapshot product name, SKU, and unit price at order time (`REQ-006`, `CLAUDE.md` §6).
- **[NEEDS CONFIRMATION]** Whether a branch can hide central products or sell branch-only local items (**`B10`**), and whether products support variants (size: S/M/L, bouquet vs. vase, custom message card), recipes/bundles (stems-to-bouquet assembly), or seasonal availability windows (**`B18`**).

### 2.5 Inventory and Stock Management
- **[CONFIRMED]** Inventory belongs strictly to a specific branch (`REQ-002` / `BRULE-002`), with no central warehouse (`REQ-016`).
- **[CONFIRMED]** Available inventory must never become negative, including under concurrent orders (`REQ-007` / `BRULE-007`).
- **[CONFIRMED]** Every inventory change must be auditable (recording who, when, why, branch, quantity change, and source document) (`REQ-008` / `BRULE-008`, `CLAUDE.md` §6).
- **[NEEDS CONFIRMATION]** Whether inventory tracks finished products/bouquets, raw flower stems, or both (**`B18`**), and the exact timing of stock reservation vs. deduction during checkout (**`B13`**).

### 2.6 Suppliers and Procurement (Goods Receiving)
- **[INFERRED]** Because there is no central warehouse (`REQ-016`), branches must receive stock into their branch inventory (either directly from suppliers or via initial stock entry), and each receipt must write an inventory audit record (`REQ-008`).
- **[NEEDS CONFIRMATION]** Whether full supplier management and purchase orders (POs) are in scope, or if branches simply record **Goods Receiving Notes (stock-in)** against their branch inventory (**`B19`**).

### 2.7 Sales and Order Management
- **[CONFIRMED]** Customers place online orders via the storefront (`CLAUDE.md` §1).
- **[CONFIRMED]** Every order has a traceable processing branch (`REQ-005` / `BRULE-005`).
- **[CONFIRMED]** Orders preserve immutable snapshots of product and price data at order time (`REQ-006` / `BRULE-006`).
- **[NEEDS CONFIRMATION]**
  - Which branch's price is shown while browsing on the storefront and charged if auto-selection picks a different branch (**`B11`**).
  - Order lifecycle statuses, transitions, and cancellation rules (**`B17`**).
  - Whether physical walk-in store sales (POS) are recorded as orders in FlowerChain, handled via stock adjustments, or out of MVP scope (**`B1`**, documented in [processes/walk-in-sale-scope.md](processes/walk-in-sale-scope.md)).

### 2.8 Customer Management
- **[CONFIRMED]** Customers can browse products and place delivery or pickup orders on the storefront (`REQ-013`), and authenticated customers may see only their own orders and data (`CLAUDE.md` §8).
- **[NEEDS CONFIRMATION]** Whether customer accounts are mandatory or guest checkout is allowed, and whether customer profiles/address books or loyalty points are in scope (**`B6`**).

### 2.9 Payment and Refunds
- **[CONFIRMED]** Payment webhooks must be verified on the server (`REQ-009` / `BRULE-009`); a browser redirect is never proof of payment (`CLAUDE.md` §8).
- **[CONFIRMED]** The same payment must never be processed twice (`REQ-010` / `BRULE-010`).
- **[CONFIRMED]** Refunds must be recorded in the audit trail (`CLAUDE.md` §8).
- **[NEEDS CONFIRMATION]** Which payment methods (e.g., Cash on Delivery / Pay at Store, bank transfer / VietQR, online payment gateway such as VNPay/MoMo/Stripe) and refund workflows (automated gateway refund vs. manual refund logging) are in scope (**`B4`**, **`B20`**).

### 2.10 Delivery and Fulfillment
- **[CONFIRMED]** Customers choose between **home delivery** and **in-store pickup** (`REQ-013`, `B3a`).
- **[CONFIRMED]** For delivery orders, the system automatically selects the processing branch: the branch nearest the delivery address that has sufficient stock (`REQ-014`, `B3b`).
- **[NEEDS CONFIRMATION]**
  - Who chooses the store for pickup orders and whether the pickup store is always the processing branch (**`B3c`**).
  - What happens when no single branch has full stock for a delivery order (**`B3d`**).
  - How "nearest" is calculated, maximum delivery radius/zones, and tie-breaking (**`B3e`**).
  - Whether an order can be reassigned to another branch after initial selection (**`B14`**).
  - Who performs delivery (branch riders vs. third-party courier), delivery fee calculation, and requested delivery/pickup date & time slots (**`B15`**).

### 2.11 Promotions and Vouchers
- **[NEEDS CONFIRMATION]** Whether discount codes, vouchers, percentage/fixed promotions, or loyalty discounts are in MVP scope (**`B6`**).
- **[INFERRED]** If promotions/vouchers are in scope, the applied discount and final line/order price must be stored in the order snapshot (`REQ-006`).

### 2.12 Employee and Branch Management
- **[CONFIRMED]** The business manages 5–30 branches (`REQ-001`), and staff accounts are assigned to authorized branches enforced by the backend (`REQ-011`, `REQ-012`).
- **[INFERRED]** Branch records require location/address data (to support nearest-branch selection for delivery, `REQ-014`, and pickup location selection, `REQ-013`) and active/inactive operational status.
- **[NEEDS CONFIRMATION]** Exact staff role hierarchy and whether a staff member can be assigned to multiple branches (e.g., regional manager) or a single branch (**`B5`**).

### 2.13 Inter-Branch Stock Transfers
- **[CONFIRMED]** Branches can transfer stock to each other, and every transfer is recorded (`REQ-015`, `B2`).
- **[CONFIRMED]** Stock transfers must never cause the sending branch's available stock to go below zero (`REQ-007`) and must write inventory audit entries for both the sending and receiving branches (`REQ-008`).
- **[NEEDS CONFIRMATION]** Who requests, approves, and confirms receipt of a transfer, whether "in-transit" stock is tracked separately (**`B12`**), and how shortfalls, damaged items in transit, or transfer cancellations are handled (**`B16`**).

### 2.14 Spoilage, Damaged Goods, and Stock Adjustments
- **[INFERRED]** Fresh flowers are perishable; branches inevitably experience spoilage (wilted/expired flowers), damage, or physical stocktake discrepancies.
- **[CONFIRMED]** Any manual stock adjustment or spoilage write-off that changes branch inventory must preserve non-negative available stock (`REQ-007`), record a full audit entry (`REQ-008`: who, when, why/reason code, branch, quantity delta, source document), and enforce branch-level authorization (`REQ-011`, `REQ-012`).
- **[NEEDS CONFIRMATION]** Standardized reason codes (e.g., `SPOILAGE`, `DAMAGED`, `STOCKTAKE_ADJUSTMENT`) and whether write-offs above a threshold require manager or head-office approval (**`B19`**).

### 2.15 Revenue and Operational Reporting
- **[INFERRED]** Because orders record their processing branch (`REQ-005`) and preserve price snapshots (`REQ-006`), and inventory changes are audited per branch (`REQ-008`), the system has the data foundation for per-branch and chain-wide revenue, order status, and inventory movement reports.
- **[NEEDS CONFIRMATION]** Which specific dashboards, reports, or CSV/Excel exports are required for Head Office vs. Branch Managers in MVP (**`B21`**).

### 2.16 Notifications and Customer Support
- **[INFERRED]** Customers and branch staff need visibility into order confirmation and status changes; customer support/staff need the ability to look up orders and handle cancellations, returns, or complaints.
- **[NEEDS CONFIRMATION]** Which notification channels (email, SMS, Zalo, in-app admin alerts) and customer return/replacement policies are in scope (**`B20`**, **`B22`**).

### 2.17 Audit and Operational Controls
- **[CONFIRMED]** Mandatory audit trails apply to:
  1. **Inventory changes:** who, when, why, branch, quantity change, and source document (`REQ-008`, `CLAUDE.md` §6).
  2. **Security & financial events:** logins, role changes, price changes, order status changes, and refunds (`CLAUDE.md` §8).
- **[CONFIRMED]** Server-side branch scoping (`REQ-011`, `REQ-012`), server-side webhook verification (`REQ-009`), and idempotent payment processing (`REQ-010`) act as non-negotiable operational controls.
