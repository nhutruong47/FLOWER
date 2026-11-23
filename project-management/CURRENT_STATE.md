# Current state

- **Last updated:** 2026-11-23
- **Read this first** at the start of every session, then follow the `project-orchestration` skill.

## Where we are

| Item | State |
|---|---|
| Phase | **2, Business analysis: IN PROGRESS** (TASK-006 first pass done 2026-11-23). Waiting for owner answers (BLK-002). |
| Git | All Phase 1 work is on **`main`** at GitHub (fast-forward merge, TASK-008 DONE). Work continues on `main`, as the owner instructed. `chore/phase-1-foundation` is kept for reference. |
| Application code | None. No business features exist. None may be built before Phase 3 approval. |
| Requirements | REQ-001 to REQ-017. 16 are DRAFT; REQ-017 (storefront shows branch prices) is NEEDS_CONFIRMATION. None APPROVED. |
| Open questions | Answered: B2, B3, B9 (partly). Open business questions: B1, B4–B8, B10, and follow-ups B3c–B3e, B9b, B11–B17. Technical: T5–T12 |
| Local toolchain | Git ✓ · JDK 21.0.13.1 Temurin ✓ · Node.js v26.13.1 / npm 11.20.0 ✓ (portable, DEC-014) · **Docker: not used locally (DEC-015)**; integration tests run in GitHub Actions. **Note:** an IDE or Claude session opened before the PATH change (2026-11-23 ~14:05) can't see `java` or `node`. Restart the IDE before any build task. |
| Claude Code | 2.1.330. Project settings: conservative permissions, `protect-secrets` hook active and verified |

## Open blockers

- **BLK-002:** Phase 2 needs owner answers to the open business questions.

## Waiting on the owner

1. **Phase 2 input:** answer the open questions in [open-questions.md](../docs/requirements/open-questions.md) (TASK-006). Most urgent: B11, B3c, B4 and B5.
2. Restart the IDE so `java` and `node` are on the PATH for Claude sessions.
3. Close the leftover Windows admin dialog from the cancelled winget install (click **No**).

## Next step

Continue TASK-006 when the owner answers: the business-analyst agent records the answers, updates REQs and processes, and drafts the role list once B5 is answered. No code is written in Phase 2.

Plan note: because local Docker is not used, the CI pipeline with Testcontainers must be ready at the **start of Phase 7** (DEC-015).
