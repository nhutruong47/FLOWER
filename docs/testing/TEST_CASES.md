# Test cases register

- **Last updated:** 2026-11-24
- **Statuses:** `PLANNED` · `AUTOMATED` (test exists in the code) · `PASSING` · `FAILING` · `RETIRED`
- **Levels:** `UNIT` (JUnit 5, Mockito) · `INTEGRATION` (Testcontainers PostgreSQL) · `E2E` (Playwright) · `MANUAL` (only with a written reason)
- **Rules:** every test case links to at least one `REQ` and `FR`/`NFR`. Status `PASSING` needs the command, date and result recorded in the Evidence column. Full traceability is in [../requirements/traceability-matrices.md](../requirements/traceability-matrices.md).

| ID | Verifies (`REQ` / `FR` / `NFR`) | Scenario | Level | Location in code | Status | Evidence |
|---|---|---|---|---|---|---|
| **TEST-001** | `REQ-001`, `FR-007`, `FR-009` | Create multiple branches; verify active branches appear in storefront directory and deactivated branches are excluded from new orders. | `INTEGRATION` | _(TBD in Phase 7)_ | `PLANNED` | – |
| **TEST-002** | `REQ-002`, `REQ-016`, `FR-015`, `NFR-009` | Verify every inventory row requires a non-null `branch_id` foreign key and no central warehouse stock exists. | `INTEGRATION` | _(TBD in Phase 7)_ | `PLANNED` | – |
| **TEST-003** | `REQ-003`, `FR-010`, `FR-006` | Head Office creates central product SKU; branch-scoped user attempting to create/edit central product is denied with `403`. | `INTEGRATION` | _(TBD in Phase 7)_ | `PLANNED` | – |
| **TEST-004** | `REQ-004`, `FR-013` | Configure different prices for Product P at Branch A (`500000`) and Branch B (`550000`); verify updating A's price does not alter B's price. | `UNIT` + `INTEGRATION` | _(TBD in Phase 7)_ | `PLANNED` | – |
| **TEST-005** | `REQ-004`, `BRULE-019`, `FR-013`, `FR-036` | Verify changing a branch price writes an immutable price-change audit entry with actor, UTC timestamp, branch, product, old price, and new price. | `INTEGRATION` | _(TBD in Phase 7)_ | `PLANNED` | – |
| **TEST-006** | `REQ-005`, `FR-021`, `NFR-009` | Verify every persisted order has a non-null `processing_branch_id` foreign key. | `INTEGRATION` | _(TBD in Phase 7)_ | `PLANNED` | – |
| **TEST-007** | `REQ-006`, `FR-021`, `FR-010`, `FR-013` | **Baseline — Order snapshot immutability:** Place an order for Product P at Branch A; subsequently change P's catalog name/SKU and Branch A's price; verify the historical order still returns the original name, SKU, and price. | `INTEGRATION` | _(TBD in Phase 7)_ | `PLANNED` | – |
| **TEST-008** | `REQ-007`, `FR-015`, `FR-017`, `FR-021` | **Baseline — Concurrent checkout non-negative stock:** Branch A has 1 unit of Product P; fire 10 concurrent order/deduction requests for 1 unit at Branch A; verify exactly 1 succeeds, 9 fail cleanly, and `available_stock == 0` (never `< 0`). | `INTEGRATION` | _(TBD in Phase 7)_ | `PLANNED` | – |
| **TEST-009** | `REQ-007`, `FR-015`, `NFR-009` | **Baseline — Database CHECK constraint:** Attempt a direct SQL update setting `available_stock = -1`; verify PostgreSQL rejects it with a check constraint violation. | `INTEGRATION` | _(TBD in Phase 7)_ | `PLANNED` | – |
| **TEST-010** | `REQ-008`, `FR-016`..`020`, `FR-024`, `NFR-010` | **Baseline — Inventory audit trail completeness:** Perform a goods receipt (`+10`), order reservation (`-2`), cancellation release (`+2`), spoilage write-off (`-1`), and transfer (`-3` at A, `+3` at B); verify every change writes an immutable audit row with actor, UTC timestamp, reason, branch, delta, and source document ID. | `INTEGRATION` | _(TBD in Phase 7)_ | `PLANNED` | – |
| **TEST-011** | `REQ-009`, `FR-025`, `NFR-007` | **Baseline — Forged webhook rejection & redirect non-trust:** Send a payment webhook with an invalid/missing signature and call the browser redirect URL; verify the webhook is rejected (`400`/`401`) and the order is never marked paid. | `UNIT` + `INTEGRATION` | _(TBD in Phase 7)_ | `PLANNED` | – |
| **TEST-012** | `REQ-010`, `FR-026`, `NFR-004`, `NFR-009` | **Baseline — Idempotent duplicate webhook handling:** Deliver the exact same verified payment webhook event twice (sequentially and concurrently); verify payment is recorded once, order transitions once, and duplicate SQL insert is blocked by `UNIQUE` constraint. | `INTEGRATION` | _(TBD in Phase 7)_ | `PLANNED` | – |
| **TEST-013** | `REQ-011`, `FR-005`, `FR-006`, `NFR-006` | **Baseline — Cross-branch read/write denial:** Authenticate as a user assigned only to Branch A; verify list endpoints return only Branch A records and direct access to Branch B orders, stock, prices, or transfers is denied (`403`/`404`). | `INTEGRATION` | _(TBD in Phase 7)_ | `PLANNED` | – |
| **TEST-014** | `REQ-012`, `FR-006`, `FR-021`, `NFR-006` | **Baseline — Untrusted client `branchId` tampering:** Authenticate as Branch A staff and send a payload/query with `"branchId": "<BranchB>"`; verify backend rejects it (`403`). On delivery checkout, send `"branchId": "<BranchC>"` and verify backend overrides it with the auto-selected branch. | `INTEGRATION` | _(TBD in Phase 7)_ | `PLANNED` | – |
| **TEST-015** | `REQ-013`, `FR-021`, `FR-028`, `FR-029` | Place one `DELIVERY` order (with delivery address) and one `PICKUP` order; verify fulfilment methods are stored and each follows its respective hand-over flow. | `INTEGRATION` + `E2E` | _(TBD in Phases 7–10)_ | `PLANNED` | – |
| **TEST-016** | `REQ-014`, `FR-008`, `FR-021` | **Nearest branch with stock selection:** Branches A (2 km away, sufficient stock) and B (5 km away, sufficient stock) → verify A is selected. When A lacks stock for 1 item and B has full stock → verify B is selected. | `UNIT` + `INTEGRATION` | _(TBD in Phase 7)_ | `PLANNED` | – |
| **TEST-017** | `REQ-014`, `FR-021` | **No branch has sufficient stock:** Place a delivery order where no active branch holds sufficient stock; verify order is rejected cleanly with Problem Details and zero stock is deducted. | `UNIT` + `INTEGRATION` | _(TBD in Phase 7)_ | `PLANNED` | – |
| **TEST-018** | `REQ-015`, `FR-018` | **Inter-branch stock transfer:** Transfer 4 units from Branch A (stock 10) to Branch B (stock 2); verify A ends at 6, B ends at 6, paired `-4`/`+4` audit entries exist, and a transfer exceeding A's stock is rejected. | `UNIT` + `INTEGRATION` | _(TBD in Phase 7)_ | `PLANNED` | – |
| **TEST-019** | `REQ-017`, `FR-014` | **Storefront branch price display & checkout reconciliation:** Verify storefront shows chosen branch's price and flags any price difference if auto-selected delivery branch has a different price (`B11`). | `E2E` | _(TBD in Phases 8–10)_ | `PLANNED` | – |
| **TEST-020** | `NFR-008`, `NFR-011`, `FR-002` | Verify customer A cannot view customer B's order (`404`), API errors return RFC 9457 `application/problem+json` without stack traces, and ArchUnit/Modulith boundary checks pass with zero cyclic dependencies. | `UNIT` + `INTEGRATION` | _(TBD in Phase 7)_ | `PLANNED` | – |

## Required coverage for the baseline rules

Each of these is covered by at least one `TEST-xxx` case above:

| Requirement | Scenario that must be tested | Mapped Test IDs |
|---|---|---|
| `REQ-007` | Concurrent orders for the last units of stock: exactly one succeeds and stock never goes below zero | `TEST-008`, `TEST-009` |
| `REQ-008` | Every stock change writes an audit record (who, when, why, branch, quantity change, source) | `TEST-010` |
| `REQ-006` | After a catalog price change, existing orders still show the original price and product data | `TEST-007` |
| `REQ-009` | A webhook with an invalid or missing signature is rejected and has no side effects | `TEST-011` |
| `REQ-010` | The same webhook event delivered twice is processed once | `TEST-012` |
| `REQ-011`, `REQ-012` | A user from branch A cannot read or change branch B's data, even with a forged `branchId` | `TEST-013`, `TEST-014` |
