# Current state

- **Last updated:** 2026-11-25
- **Read this first** at the start of every session, then follow the `project-orchestration` skill.

## Where we are

| Item | State |
|---|---|
| Phase | Phases 1–2 governance and orchestration are DONE. The planning deliverables P03–P07 (TASK-006, TASK-010 to TASK-013) are complete but **not yet approved by the owner**. **Backend implementation (P08) is in progress** on the owner's instruction (DEC-028), on DRAFT requirements. |
| Git | Work branch `fix/TASK-014-backend-build` (not pushed yet), holding TASK-014, TASK-015, the B15 answer and these records. Parallel task branches live in worktrees outside the repository: `feat/TASK-017-payment-webhooks` and `fix/TASK-018-backend-defects` in `C:\Users\truongthainhu\Downloads\fc-worktrees\`. `main` is at `5589216`. |
| Application code | A Spring Boot **4.2.0** backend (DEC-026) with the modules `shared`, `audit`, `branch`, `identity`, `customer`, `catalog`, `inventory` and `order`, plus 8 Flyway migrations. It was imported from another tool's session in commit `5589216`. It compiles (TASK-014) and starts against local PostgreSQL (TASK-015). The `payment` and `reporting` modules and the baseline tests are being built (TASK-017, TASK-018). There is no frontend yet. |
| Requirements | REQ-001 to REQ-018 (REQ-018 = third-party couriers and delivery fee, owner answer B15, 2026-11-25), plus BRULE, FR, NFR and TEST registers. All DRAFT. |
| Local toolchain | JDK 21 ✓ · Node.js / npm ✓ · Maven wrapper 3.9.16 ✓ · **PostgreSQL 16.16 (portable, localhost only) ✓** (DEC-027) · no local Docker (DEC-015). Build commands are in `CLAUDE.md` §3. |
| Claude Code | Conservative permissions, 8 specialist agents, skills, `protect-secrets` hook (27/27 self-test). |

## Open blockers

- **BLK-002:** Phase 2 needs owner answers to the open business questions. The most urgent for the code now:
  - B4: payment provider;
  - B15a–B15d: delivery fee and courier booking;
  - B11: which branch price the storefront shows;
  - B3c: who chooses the pickup store;
  - B3e: how "nearest" is measured;
  - B5: final roles.

## Known gaps (not yet scheduled)

- Customer login tokens are held in memory with no expiry, and are lost on restart. Staff sessions are stored in the database and expire after 8 hours. DEC-020 (JWT plus a refresh session) is not implemented yet.
- Stock reserved by unpaid online orders is never released after `reservation_expires_at`, because there is no expiry job (depends on B13).
- List endpoints page in memory (the page size is capped at 100).
- Delivery and courier hand-over (FR-028, REQ-018), promotions (B6) and notifications (FR-033) are not built.
- The delivery fee is a fixed placeholder of 35,000 VND that nobody has approved (B15b).

## Next step

1. Merge TASK-017 and TASK-018 when their agents finish: build, run the unit tests, then run the app against local PostgreSQL.
2. Security review of TASK-015, TASK-017 and TASK-018.
3. Push the work branch (asks for approval). GitHub Actions then runs the integration tests.
4. Owner: answer B15a–B15d and B4, and approve the planning deliverables (Phase 3 gate).
