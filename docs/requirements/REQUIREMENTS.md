# Requirements register

- **Last updated:** 2026-11-23
- **Statuses:** `DRAFT` (captured, not yet approved) · `NEEDS_CONFIRMATION` (unclear or conditional, needs an owner decision) · `APPROVED` (signed off in Phase 3, may be built) · `REJECTED` · `SUPERSEDED` (link the replacement)
- **Rules:**
  - Only `APPROVED` requirements may be built as features.
  - Every requirement states its source. Never add a requirement based on an assumption; add a question to [open-questions.md](open-questions.md) instead.
  - Acceptance criteria are written in Phase 3 and verified by TEST cases in [TEST_CASES.md](../testing/TEST_CASES.md).
- **About REQ-001 to REQ-012:** these are guardrails from the project owner (Phase 1 brief, 2026-11-23). Every design must respect them, even before Phase 3 formally approves them.

| ID | Requirement | Type | Source | Status | Notes / related questions |
|---|---|---|---|---|---|
| REQ-001 | One business can own multiple branches. | Business rule | Owner brief 2026-11-23 | DRAFT | Initial target 5–30 branches |
| REQ-002 | Inventory belongs to a specific branch. | Business rule | Owner brief 2026-11-23 | DRAFT | Transfers between branches, central warehouse: B2 |
| REQ-003 | The product catalog is managed centrally, unless approved requirements say otherwise. | Business rule | Owner brief 2026-11-23 | DRAFT (default) | Branch-only products: B10 |
| REQ-004 | Product prices may vary by branch. | Business rule | Owner brief 2026-11-23 | NEEDS_CONFIRMATION | B9: "subject to business confirmation" |
| REQ-005 | Every order has a traceable processing branch. | Business rule | Owner brief 2026-11-23 | DRAFT | How the branch is chosen: B3 |
| REQ-006 | Orders keep snapshots of product and price data at order time. | Business rule | Owner brief 2026-11-23 | DRAFT | |
| REQ-007 | Available inventory must never become negative. | Business rule | Owner brief 2026-11-23 | DRAFT | Must hold under concurrent orders |
| REQ-008 | Every inventory change is auditable. | Business rule | Owner brief 2026-11-23 | DRAFT | |
| REQ-009 | Payment webhooks are verified on the server. | Security | Owner brief 2026-11-23 | DRAFT | Provider: B4 |
| REQ-010 | The same payment is never processed twice. | Security | Owner brief 2026-11-23 | DRAFT | |
| REQ-011 | The backend enforces branch-level authorization. | Security | Owner brief 2026-11-23 | DRAFT | Roles: B5 |
| REQ-012 | A `branchId` sent by the frontend is never trusted without an authorization check. | Security | Owner brief 2026-11-23 | DRAFT | |
