# Assumptions, Constraints, and Risk Register

- **Status:** DRAFT
- **Last updated:** 2026-11-24
- **Owner:** business-analyst · **Task:** TASK-006 (Phase 2 — P03 Business Analysis)
- **Sources:** Owner Brief (2026-11-23), Owner Phase 2 Answers (2026-11-23), Project Rules Brief (2026-11-24), `CLAUDE.md`, `DECISIONS.md` (`DEC-001` to `DEC-016`).
- **Labels:** `CONFIRMED` · `INFERRED` · `PROPOSED` · `NEEDS CONFIRMATION`

---

## 1. Confirmed Constraints

1. **[CONFIRMED] Single Business, 5–30 Branches (`REQ-001`, `DEC-001`):** The platform serves one business owner across 5–30 branches, implemented as a **Modular Monolith** rather than microservices.
2. **[CONFIRMED] No Central Warehouse (`REQ-016`, `B2`):** All inventory lives at branches. Any stock movement between locations is an inter-branch transfer (`REQ-015`).
3. **[CONFIRMED] Technology Stack Constraints (`DEC-001` to `DEC-007`):**
   - **Backend:** Java 21, Spring Boot 3.x (Spring MVC, Spring Security, Spring Data JPA), Maven Wrapper (`mvnw`).
   - **Database:** PostgreSQL with Flyway-only migrations (`ddl-auto` must be `validate` or `none`).
   - **Frontend:** React, TypeScript (`strict`), Vite, Tailwind CSS in an npm workspace with two separate apps (`frontend/storefront` and `frontend/admin`) plus `frontend/packages/shared`.
   - **Testing:** JUnit 5, Mockito, Testcontainers (real PostgreSQL, never H2), and Playwright.
4. **[CONFIRMED] Non-Negotiable Data & Security Invariants (`REQ-005` to `REQ-012`):**
   - Every order has a traceable processing branch (`REQ-005`) and immutable product/price snapshots (`REQ-006`).
   - Available branch inventory never becomes negative (`REQ-007`) and every change is audited (`REQ-008`).
   - Payment webhooks are verified server-side (`REQ-009`) and processed idempotently at most once (`REQ-010`).
   - Backend enforces branch-level authorization (`REQ-011`) and never trusts client-supplied `branchId` without server-side authorization checks (`REQ-012`).
5. **[CONFIRMED] CI Testcontainers Constraint (`DEC-015`):** Because local Docker was waived by the owner (`DEC-015`), the GitHub Actions CI pipeline running PostgreSQL Testcontainers must be in place at the start of Phase 7 before the first integration test is verified.
6. **[CONFIRMED] Governance & Phase Gate Constraint (`CLAUDE.md` §2, §12):** No application feature code or business module may be implemented until its corresponding requirements (`REQ-xxx`) are marked `APPROVED` by the project owner in Phase 3.

---

## 2. Explicit Working Assumptions (All Flagged for Owner Confirmation)

Per `CLAUDE.md` §2 (*"Never turn an assumption into a requirement"*), the items below are **not** treated as approved requirements. Each is explicitly linked to an open question in [../requirements/open-questions.md](../requirements/open-questions.md):

| ID | Working Assumption / Hypothesis | Classification | Linked Open Question |
|---|---|---|---|
| **ASM-01** | Products in the central catalog are sold as finished floral products/bouquets with a single unit of measure per SKU, unless variants/stem-recipes are requested. | **NEEDS CONFIRMATION** | `B18` |
| **ASM-02** | Walk-in branch sales either use a lightweight stock adjustment or a simple branch POS screen, sharing the branch's physical inventory with online orders. | **NEEDS CONFIRMATION** | `B1` |
| **ASM-03** | For in-store pickup orders, the customer chooses the pickup branch, and that branch is the order's processing branch. | **INFERRED / NEEDS CONFIRMATION** | `B3c` |
| **ASM-04** | Branches receive stock directly into their own inventory via a Goods Receipt record without a multi-step supplier Purchase Order procurement suite in MVP. | **PROPOSED / NEEDS CONFIRMATION** | `B19` |
| **ASM-05** | The primary market, language, and currency are Vietnam (`vi-VN`, `VND`, integer/zero-decimal currency stored in `numeric`), unless multi-language/multi-currency is requested. | **NEEDS CONFIRMATION** | `B7` |

---

## 3. Business & Technical Risk Register

| Risk ID | Risk Description | Impact | Severity | Mitigation / Resolution Path | Classification |
|---|---|---|---|---|---|
| **RISK-01** | **Storefront Price vs. Auto-Selected Branch Mismatch (`REQ-017` vs. `REQ-014`):** Customer browses Branch A's prices (`REQ-017`), but at checkout for a delivery order the system automatically selects Branch B (`REQ-014`), which has a different price (`REQ-004`). | Customer surprise at checkout, abandoned carts, or margin loss if the lower price is forced. | **HIGH** | Resolve **`B11`** before Phase 3 approval (e.g., ask customer for delivery area/branch upfront, or show updated price at checkout for confirmation before payment). | **CONFIRMED RISK** |
| **RISK-02** | **Concurrent Checkout Race Condition on Last Perishable Stock (`REQ-007`):** Two online customers (or an online order and a walk-in branch sale) claim the last bouquet at the same branch simultaneously. | Overselling flowers that cannot be fulfilled, or violating `REQ-007`. | **CRITICAL** | Enforce database `CHECK (available_stock >= 0)`, row-level pessimistic locking (`SELECT ... FOR UPDATE`) or optimistic locking on stock deduction, define reservation timing (**`B13`**), and verify with a mandatory concurrency integration test (`CLAUDE.md` §9). | **CONFIRMED RISK** |
| **RISK-03** | **Multi-Item Order Split vs. Single Processing Branch (`REQ-005`):** Customer orders Item X and Item Y, and no single branch has both items in stock. If the order is split across two branches, `REQ-005` ("Every order has a traceable processing branch") could be violated if modeled as a single flat order. | Data model rework in Phase 5 / Phase 7. | **HIGH** | Resolve **`B3d`** before Phase 5 database design (either reject/transfer to one branch, or split into parent checkout + per-branch sub-orders). | **CONFIRMED RISK** |
| **RISK-04** | **Unsynced Walk-In Store Inventory Depletion:** Physical walk-in customers buy flowers at a branch, but branch staff do not deduct the stock in FlowerChain in real time. | System auto-assigns an online delivery order (`REQ-014`) to a branch whose physical shelf is actually empty. | **HIGH** | Clarify walk-in sale scope (**`B1`**), provide fast branch stock adjustment / POS deduction (`WF-10`), and allow exception order reassignment (**`B14`**). | **INFERRED RISK** |
| **RISK-05** | **Perishable Spoilage & Uncollected Pickup / Failed Delivery:** Fresh flowers wilt quickly if a payment hangs in reserved state (`B13`) or a delivery/pickup fails (`B15`, `B17`). | Financial loss and locked inventory. | **MEDIUM** | Define strict payment reservation expiration timers (**`B13`**) and clear cancellation cutoffs / spoilage write-off flows (**`B17`**, **`B19`**). | **INFERRED RISK** |
| **RISK-06** | **Forged, Retried, or Out-of-Order Payment Webhooks (`REQ-009`, `REQ-010`):** Attackers send fake payment callbacks, or the gateway delivers duplicate/out-of-order webhooks. | Unpaid orders marked paid, or duplicate inventory deduction / double accounting. | **CRITICAL** | Enforce server-side signature verification (`REQ-009`), database `UNIQUE` constraint on provider event/transaction IDs (`REQ-010`), and mandatory security review. | **CONFIRMED RISK** |
| **RISK-07** | **Cross-Branch Privilege Escalation (`REQ-011`, `REQ-012`):** A branch staff member modifies `branchId` in an API request payload or query parameter to view or alter another branch's stock, prices, or orders. | Unauthorized data access or inventory tampering across branches. | **CRITICAL** | Resolve allowed `branchId`s exclusively from the authenticated principal on the server and test cross-branch denial on every module (`REQ-011`, `REQ-012`). | **CONFIRMED RISK** |
