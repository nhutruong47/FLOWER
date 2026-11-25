# Process: walk-in store sale (point of sale)

- **Status:** NEEDS_CONFIRMATION (scope skeleton)
- **Last updated:** 2026-11-24
- **Owner:** business-analyst · **Task:** TASK-009 (P02 orchestration demonstration)
- **Sources:**
  - Owner brief, 2026-11-23: baseline business rules `REQ-001` to `REQ-012` (`BRULE-001` to `BRULE-012`).
  - Owner answers, 2026-11-23: `B2` (each branch has its own stock; no central warehouse, `REQ-016`), `B9` (each branch has its own prices, `REQ-004`).
  - Phase 2 analyst note in `project-management/TASKS.md` (`TASK-006`): *"Walk-in shop sales affecting branch stock are a scope question to raise under B1."*
- **Labels:** CONFIRMED (stated by the owner; source given) · INFERRED (follows from a source but was not stated) · NEEDS CONFIRMATION (open question given) · NOT FOUND (no source at all).

This document captures the boundary between physical walk-in branch sales and the FlowerChain internal management system so that the owner can decide in `B1` whether walk-in POS sales are handled inside FlowerChain or via stock adjustments. **No business rule is assumed.**

## Scope question (`B1`)

| Option | How walk-in store sales interact with FlowerChain | Status |
|---|---|---|
| Option A | **In-system POS / walk-in order entry:** Branch staff record walk-in sales directly in the FlowerChain admin app, automatically deducting branch stock and recording an order snapshot. | NEEDS CONFIRMATION (`B1`) |
| Option B | **Manual / batch stock adjustment only:** Walk-in sales happen outside FlowerChain (or at a separate cash register), and branch staff record stock deductions in FlowerChain to keep online availability accurate. | NEEDS CONFIRMATION (`B1`) |
| Option C | **Out of scope for MVP:** Only online orders and manual inventory adjustments are in MVP scope. | NEEDS CONFIRMATION (`B1`) |

## Baseline guardrails that apply regardless of option (CONFIRMED)

If walk-in sales or walk-in stock deductions touch FlowerChain, the baseline business rules require:

| Guardrail | Application to a branch walk-in sale / deduction | Label & Source |
|---|---|---|
| Branch stock ownership | Any stock deduction applies strictly to the specific branch where the sale occurred; there is no central warehouse. | CONFIRMED (`REQ-002`, `REQ-016`, `B2`) |
| Non-negative inventory | Available stock at the branch must never become negative, even if an online order and a branch deduction happen at the same time. | CONFIRMED (`REQ-007` / `BRULE-007`) |
| Auditable inventory change | Every stock deduction must record who made it, when, why, the branch, the quantity change, and the source document. | CONFIRMED (`REQ-008` / `BRULE-008`) |
| Branch-level authorization | A branch staff member may only record sales or stock changes for a branch they are authorized to act on; client-supplied `branchId` is never trusted alone. | CONFIRMED (`REQ-011`, `REQ-012`) |
| Branch pricing & snapshots | If walk-in orders are recorded as orders in the system (Option A), they use that branch's price (`REQ-004`) and preserve product/price snapshots at sale time (`REQ-006`). | CONFIRMED if Option A is chosen (`REQ-004`, `REQ-006`) |

## Open questions linked to this process

- **[B1](../../requirements/open-questions.md):** Are walk-in branch sales in scope for the internal management system (Option A, B, or C above)?
- **[B5](../../requirements/open-questions.md):** Which branch staff roles are allowed to record walk-in sales or manual stock deductions?
- **[B4](../../requirements/open-questions.md):** If Option A is chosen, which in-store payment methods (cash, card terminal, QR/transfer) must be recorded?
