# Current state

- **Last updated:** 2026-11-23
- **Read this first** at the start of every session, then follow the `project-orchestration` skill.

## Where we are

| Item | State |
|---|---|
| Phase | **1, Project governance: DONE** (owner approved 2026-11-23). **Phase 2, Business analysis: NEXT**, waiting for owner answers to B1–B10. |
| Git | All Phase 1 work is on **`main`** at GitHub (fast-forward merge, TASK-008 DONE). Work continues on `main`, as the owner instructed. `chore/phase-1-foundation` is kept for reference. |
| Application code | None. No business features exist. None may be built before Phase 3 approval. |
| Requirements | REQ-001 to REQ-012: baseline rules from the owner. 11 are DRAFT; REQ-004 is NEEDS_CONFIRMATION. None APPROVED. |
| Open questions | Business B1–B10 (B2, B3 partly answered); technical T5–T12 |
| Local toolchain | Git ✓ · JDK 21.0.13.1 Temurin ✓ · Node.js v26.13.1 / npm 11.20.0 ✓ (portable, DEC-014) · **Docker: not used locally (DEC-015)**; integration tests run in GitHub Actions. **Note:** an IDE or Claude session opened before the PATH change (2026-11-23 ~14:05) can't see `java` or `node`. Restart the IDE before any build task. |
| Claude Code | 2.1.330. Project settings: conservative permissions, `protect-secrets` hook active and verified |

## Open blockers

None.

## Waiting on the owner

1. **Phase 2 input:** answer business questions B1–B10 in [open-questions.md](../docs/requirements/open-questions.md) (TASK-006).
2. Restart the IDE so `java` and `node` are on the PATH for Claude sessions.
3. Close the leftover Windows admin dialog from the cancelled winget install (click **No**).

## Next step

Phase 2, business analysis (TASK-006, business-analyst agent): go through B1–B10 with the owner and draft processes, roles and REQ entries. No code is written in Phase 2.

Plan note: because local Docker is not used, the CI pipeline with Testcontainers must be ready at the **start of Phase 7** (DEC-015).
