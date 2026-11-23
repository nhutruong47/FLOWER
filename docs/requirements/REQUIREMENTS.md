# Requirements register

- **Status:** DRAFT. No requirement is approved yet.
- **Last updated:** 2026-11-23
- **Statuses:** `DRAFT` (captured, not yet approved) · `NEEDS_CONFIRMATION` (unclear or conditional, needs an owner decision) · `APPROVED` (signed off in Phase 3, may be built) · `REJECTED` · `SUPERSEDED` (link the replacement)
- **Rules:**
  - Only `APPROVED` requirements may be built as features.
  - Every requirement states its source. Never add a requirement based on an assumption; add a question to [open-questions.md](open-questions.md) instead.
  - Acceptance criteria are drafted in Phase 2 (see [Draft acceptance criteria](#draft-acceptance-criteria)), finalised in Phase 3, and verified by TEST cases in [TEST_CASES.md](../testing/TEST_CASES.md).
- **About REQ-001 to REQ-012:** these are guardrails from the project owner (Phase 1 brief, 2026-11-23). Every design must respect them, even before Phase 3 formally approves them.

| ID | Requirement | Type | Source | Status | Notes / related questions |
|---|---|---|---|---|---|
| REQ-001 | One business can own multiple branches. | Business rule | Owner brief 2026-11-23 | DRAFT | Initial target 5–30 branches |
| REQ-002 | Inventory belongs to a specific branch. | Business rule | Owner brief 2026-11-23 | DRAFT | B2 answered: transfers between branches are allowed (REQ-015); there is no central warehouse (REQ-016) |
| REQ-003 | The product catalog is managed centrally, unless approved requirements say otherwise. | Business rule | Owner brief 2026-11-23 | DRAFT (default) | Branch-only products: B10 |
| REQ-004 | Product prices may vary by branch. | Business rule | Owner brief 2026-11-23; owner answer, 2026-11-23 (B9) | DRAFT | Confirmed by owner (B9), 2026-11-23: each branch has its own prices. Who sets prices is open (B9b). Storefront display: REQ-017, B11 |
| REQ-005 | Every order has a traceable processing branch. | Business rule | Owner brief 2026-11-23 | DRAFT | Delivery orders: the system selects the branch (REQ-014). Pickup orders: B3c. Split orders: B3d. Moving an order to another branch: B14 |
| REQ-006 | Orders keep snapshots of product and price data at order time. | Business rule | Owner brief 2026-11-23 | DRAFT | |
| REQ-007 | Available inventory must never become negative. | Business rule | Owner brief 2026-11-23 | DRAFT | Must hold under concurrent orders |
| REQ-008 | Every inventory change is auditable. | Business rule | Owner brief 2026-11-23 | DRAFT | |
| REQ-009 | Payment webhooks are verified on the server. | Security | Owner brief 2026-11-23 | DRAFT | Provider: B4 |
| REQ-010 | The same payment is never processed twice. | Security | Owner brief 2026-11-23 | DRAFT | |
| REQ-011 | The backend enforces branch-level authorization. | Security | Owner brief 2026-11-23 | DRAFT | Roles: B5 |
| REQ-012 | A `branchId` sent by the frontend is never trusted without an authorization check. | Security | Owner brief 2026-11-23 | DRAFT | |
| REQ-013 | For an online order, the customer chooses home delivery or in-store pickup. | Feature | Owner answer, 2026-11-23 (B3a) | DRAFT | Pickup store and its processing branch: B3c. Delivery and pickup details: B15. Order statuses: B17. Process: [online order fulfilment](../business-analysis/processes/online-order-fulfilment.md) |
| REQ-014 | For a delivery order, the system selects the processing branch: the branch nearest the delivery address that still has enough stock for the order. | Business rule | Owner answer, 2026-11-23 (B3b) | DRAFT | Applies REQ-005 to delivery orders. Open: how "nearest" is measured (B3e); no single branch has enough stock (B3d); when stock is checked and reserved (B13); moving an order to another branch (B14). Pickup orders: B3c |
| REQ-015 | Branches can transfer stock to each other, and every transfer is recorded. | Business rule | Owner answer, 2026-11-23 (B2) | DRAFT | Must respect REQ-007 and REQ-008. Open: who requests and approves, stock in transit (B12); shortfalls, damage, cancellation (B16). Process: [stock transfer](../business-analysis/processes/stock-transfer.md) |
| REQ-016 | There is no central warehouse: all stock is held by branches. | Business rule | Owner answer, 2026-11-23 (B2): "an additional central warehouse" was offered and not chosen | DRAFT | CONFIRMED by the owner's choice between alternatives. Refines REQ-002 |
| REQ-017 | The storefront shows product prices of the branch the customer chose. | Feature | Owner answer, 2026-11-23 (B9) | NEEDS_CONFIRMATION | Conflicts with B3b and REQ-014: the system, not the customer, picks the branch. Resolve through B11. Depends on REQ-004 |

## Draft acceptance criteria

Drafted in Phase 2 (TASK-006) from the owner's answers of 2026-11-23. They are not approved. "AC pending: Bx" means the criterion cannot be written until question Bx in [open-questions.md](open-questions.md) is answered. Acceptance criteria for REQ-001 to REQ-003 and REQ-005 to REQ-012 are written in Phase 3.

#### REQ-004 acceptance criteria
- AC1: Given branch A's price for product P is X and branch B's price for P is a different amount Y, when the price of P is looked up for each branch, then A's price is X and B's price is Y.
- AC2: Given the situation in AC1, when A's price for P is changed, then B's price for P is unchanged.
- AC pending: B9b (who may set or change a branch's price; what happens when a branch has no price for a product).

#### REQ-013 acceptance criteria
- AC1: Given a customer is checking out an online order, when they choose how to receive it, then both home delivery and in-store pickup are offered.
- AC2: Given an online order has been placed, when the order is viewed, then it shows exactly one fulfilment method: delivery or pickup.
- AC3: Given a customer chooses home delivery, when they place the order, then the order stores the delivery address they entered.
- AC pending: B3c (which store a pickup order goes to, and who chooses it).
- AC pending: B15 (delivery fee, who delivers, delivery or pickup date and time).

#### REQ-014 acceptance criteria
- AC1: Given a delivery order, branches A and B both have enough stock for every item in it, and A is nearer to the delivery address than B, when the order is placed, then A is recorded as the processing branch.
- AC2: Given a delivery order, the nearest branch A does not have enough stock for the order, and the next nearest branch B does, when the order is placed, then B is recorded as the processing branch.
- AC3: Given a delivery order request that names a branch, when the order is placed, then the processing branch is the one selected by the rule in AC1 and AC2, not the branch named in the request.
- AC pending: B3e (how "nearer" is measured, any delivery-area limit, and ties).
- AC pending: B3d (no single branch, or no branch at all, has enough stock).
- AC pending: B13 (when stock is checked and reserved).
- AC pending: B14 (moving an order to another branch after selection).

#### REQ-015 acceptance criteria
- AC1: Given branch A holds 10 units of product P and branch B holds 2, when a transfer of 4 units of P from A to B is completed, then A holds 6 units of P and B holds 6.
- AC2: Given the completed transfer in AC1, when the inventory audit trail is checked, then it shows a change of −4 at A and +4 at B, each linked to that transfer and showing who made it and when (REQ-008).
- AC3: Given branch A holds 3 units of product P, when a transfer of 5 units of P out of A is attempted, then A's available stock of P never goes below zero (REQ-007).
- AC pending: B16 (whether the transfer in AC3 is refused or reduced; shortfalls, damage, cancellation).
- AC pending: B12 (who requests, approves and receives a transfer; stock in transit).

#### REQ-016 acceptance criteria
- AC1: Given the stock of any product, when it is listed across the business, then every quantity belongs to a branch, and no quantity belongs to a central warehouse.

#### REQ-017 acceptance criteria
- AC1: Given branches A and B have different prices for product P, and A is the branch the customer chose, when the customer views P on the storefront, then the storefront shows A's price.
- AC pending: B11 (how the customer's branch is set while browsing, given that the system picks the processing branch; which price the customer pays if the processing branch differs).
