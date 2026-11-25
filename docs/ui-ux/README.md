# UI/UX Design

- **Owner:** frontend-developer (`hallmark`, `design-taste-frontend`, and `frontend-development` skills), with business-analyst input · **Phase:** 6 (P07 UI/UX Design) · **Status:** PROPOSED (complete P07 UI/UX design package awaiting owner approval before P08).
- **Last updated:** 2026-11-24

This folder holds the comprehensive P07 UI/UX Design deliverables for FlowerChain, crafted using the **`hallmark`** (`Garden` botanical editorial theme dealt via `scripts/theme.mjs`) and **`design-taste-frontend`** (`taste-skill 2.5.1` florist place/shop & anti-slop rules) skills:

1. **[design-system-and-components.md](design-system-and-components.md):**
   - Hallmark v2.0.0 Picks block & Taste-Skill v2.5.1 deal (`Garden` theme: warm oat paper `oklch(95.8% 0.024 90)`, deep botanical green ink `oklch(23% 0.050 152)`, chlorophyll leaf-green accent `oklch(46% 0.135 142)`, `Vollkorn 600` roman headings over `Hanken Grotesk` body & `tabular-nums` financial/stock figures, `1px` hairlines, flat paper surfaces with zero drop shadows).
   - Complete `:root` CSS design tokens (`tokens.css`) and anti-slop floor rules (no literal colours, no `transition: all`, no tracked-capital eyebrows, no captions under floral photos, no purple gradients, no cards-in-cards).
   - Shared Component Inventory (`CMP-01` to `CMP-15`, including `BranchPriceSelectorBar`, `CheckoutPriceReconciliationNotice`, `OrderSnapshotReceiptTable`, `AdminBranchScopeSwitcher`, `StockTransferDualBranchCard`, and `ProblemAlertBanner`).
2. **[information-architecture-and-flows.md](information-architecture-and-flows.md):**
   - Dual-application Information Architecture (`frontend/storefront` vs. `frontend/admin` + `frontend/packages/shared`).
   - Complete Mermaid Sitemaps for both applications.
   - Role-based dashboard adaptation matrix across all 6 internal roles (`SYSTEM_OWNER`, `HEAD_OFFICE_ADMIN`, `BRANCH_MANAGER`, `SALES_STAFF`, `INVENTORY_STAFF`, `DELIVERY_STAFF`) and `CUSTOMER`.
   - End-to-end Mermaid User Journeys (Customer Browsing → Branch Price Reconciliation → Nearest-Branch Auto-Allocation → Payment Webhook Polling; Branch Goods Receipt → Spoilage → Dual-Side Inter-Branch Stock Transfer).
   - Standard UX states (`loading` skeletons, honest `empty` states, RFC 9457 `application/problem+json` `error` states, inline field validation, and WCAG AAA accessibility).
3. **[storefront-screens-and-wireframes.md](storefront-screens-and-wireframes.md):**
   - Page specifications and wireframes for all 15 Customer Website capabilities: (1) Homepage Stage Hero, (2) Product Listing, (3) Product Detail & Cross-Branch Stock Drawer, (4) Product Search, (5) Shopping Cart Drawer, (6) Checkout, (7) Branch Selection (`REQ-004`, `REQ-017`), (8) Delivery vs. In-Store Pickup Selection & Price Reconciliation (`REQ-013`, `REQ-014`, `B11`), (9) Payment & Server-Verified Webhook Status Polling (`REQ-009`), (10) Order Tracking & Immutable Snapshots (`REQ-005`, `REQ-006`), (11) Customer Account & Addresses, (12) Order History (`BRULE-027`), (13) Wishlist, (14) Promotions (`B6`), and (15) Customer Support & Return Requests (`FR-030`).
4. **[admin-screens-and-wireframes.md](admin-screens-and-wireframes.md):**
   - Page specifications and wireframes for all 15 Internal Management Dashboard modules: (1) Role-Adaptive Executive & Branch Dashboard, (2) Branch Management (`5–30` branches, coordinates & delivery radius), (3) Employee, Role & Branch Assignment Management (`REQ-011`), (4) Central Product Catalog (`REQ-003`), (5) Per-Branch Price Matrix & Audit (`REQ-004`), (6) Branch Inventory & Immutable Movement Ledger (`REQ-002`, `REQ-007`, `REQ-008`), (7) Purchasing / Branch Goods Receiving (`REQ-016`, `FR-020`), (8) Supplier Reference Tracking on Goods Receipts, (9) Spoilage, Damaged Goods & Stock Adjustments (`FR-019`), (10) Dual-Side Inter-Branch Stock Transfers (`REQ-015`, `FR-018`), (11) Branch-Scoped Order Management, Snapshot View & Exception Branch Reassignment (`REQ-005`, `REQ-006`, `FR-022`..`024`), (12) Payment, Webhook Ledger & Refund Management (`REQ-009`, `REQ-010`, `FR-025`..`027`), (13) Home Delivery Dispatch & Store Pickup Hand-Over (`FR-028`, `FR-029`), (14) Customer Directory, Promotions, Revenue/Inventory Reports (`FR-034`, `FR-035`) & 6-Category Audit Log Explorer (`FR-036`), and (15) Chain-Wide System Settings (`FR-037`).
5. **[responsive-and-ui-acceptance-criteria.md](responsive-and-ui-acceptance-criteria.md):**
   - Responsive breakpoint specifications across `1920px`, `1440x900`, `1280x800`, `1024x768`, `820x1180`, `768x1024`, `390x844`, and `320px`.
   - Testable UI Acceptance Criteria (`UI-AC-01` to `UI-AC-11`) mapped to `REQ-xxx`, `FR-xxx`, `NFR-xxx`, and `TEST-xxx`.
