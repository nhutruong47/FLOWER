# Current state

- **Last updated:** 2026-11-23
- **Read this first** at the start of every session, then follow the `project-orchestration` skill.

## Where we are

| Item | State |
|---|---|
| Phase | **1, Project governance: DONE** (owner approved 2026-11-23). **Phase 2, Business analysis: NEXT**, waiting for owner answers. |
| Git | Phase 1 work is committed on branch `chore/phase-1-foundation` and pushed to `origin`. `main` = `d776e5e` (unchanged; merging goes through a pull request, DEC-011 / T8). |
| Application code | None. No business features exist. None may be built before Phase 3 approval. |
| Requirements | REQ-001 to REQ-012: baseline rules from the owner. 11 are DRAFT; REQ-004 is NEEDS_CONFIRMATION |
| Open questions | Business B1–B10 (B2, B3 partly answered); technical T4–T11 |
| Local toolchain | Git ✓ · JDK 21.0.13.1 Temurin ✓ · Node.js v26.13.1 / npm 11.20.0 ✓ (portable, DEC-014) · Docker ✗ (deferred, T4) |
| Claude Code | 2.1.330. Project settings: conservative permissions, `protect-secrets` hook active and verified |

## Open blockers

None. BLK-001 was resolved on 2026-11-23.

## Waiting on the owner

1. **Phase 2 input:** answer business questions B1–B10 in [open-questions.md](../docs/requirements/open-questions.md) (TASK-006).
2. Decide whether to merge `chore/phase-1-foundation` into `main` (pull request or direct merge; branch protection is T8).
3. Decide on local Docker (T4, TASK-007).
4. Close the leftover Windows admin dialog from the cancelled winget install (click **No**).

## Next step

Phase 2, business analysis (TASK-006, business-analyst agent): go through B1–B10 with the owner and draft processes, roles and REQ entries. No code is written in Phase 2.
