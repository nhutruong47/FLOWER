# Current state

- **Last updated:** 2026-11-24
- **Read this first** at the start of every session, then follow the `project-orchestration` skill.

## Where we are

| Item | State |
|---|---|
| Phase | **Phase 1 Governance: DONE** · **P02 Orchestrator: DONE (TASK-009)** · **Phases 2–6 (P03 Business Analysis, P04 Requirements, P05 Architecture, P06 Database & API, P07 UI/UX Design): DELIVERABLES COMPLETE, AWAITING OWNER APPROVAL (TASK-006, TASK-010, TASK-011, TASK-012, TASK-013)** |
| Git | Working tree on **`main`** contains P01 through P07 documentation deliverables and installed `hallmark` + `design-taste-frontend` skills locally (not pushed; pushes require explicit owner approval). |
| Application code | None. All planning and design phases (Phases 0–6 / P00–P07) are complete and ready for Phase 7–8 implementation once approved. |
| Requirements, Architecture, DB, API & UI/UX | `REQ-001`..`017`, `BRULE-001`..`030`, `FR-001`..`038`, `NFR-001`..`015`, `TEST-001`..`020`, `L0`–`L7` Module Map, `ADR-0002`..`0010`, 24-table PostgreSQL 16 ERD & Data Dictionary, 8-step Flyway Migration Plan, OpenAPI 3.1 Contract (`docs/api/openapi.yaml`), and Hallmark/Taste-Skill UI/UX Design (`docs/ui-ux/`, `UI-AC-01`..`11`) complete. |
| Open questions | Technical questions `T5`, `T9`, `T10`, `T11`, `T12` have concrete proposed ADR resolutions (`ADR-0002`..`0010`). Hosting provider (`T6`) and business confirmations remain open for owner sign-off. |
| Local toolchain | Git ✓ · JDK 21 ✓ · Node.js LTS / npm ✓ · **Docker:** CI-only integration tests per DEC-015 (though Docker CLI 29.5.3 was also detected on the current machine during P00 audit). |
| Claude Code | Project settings: conservative permissions, 8 specialist agents, 9 skills (added `requirements-engineering`), `protect-secrets` hook active and verified (27/27 tests pass). |

## Open blockers

- **BLK-002:** Phase 2 needs owner answers to the open business questions.

## Waiting on the owner

1. **Phase 2 input:** answer the open questions in [open-questions.md](../docs/requirements/open-questions.md) (TASK-006). Most urgent: B11, B3c, B4 and B5.
2. Restart the IDE so `java` and `node` are on the PATH for Claude sessions.
3. Close the leftover Windows admin dialog from the cancelled winget install (click **No**).

## Next step

Continue TASK-006 when the owner answers: the business-analyst agent records the answers, updates REQs and processes, and drafts the role list once B5 is answered. No code is written in Phase 2.

Plan note: because local Docker is not used, the CI pipeline with Testcontainers must be ready at the **start of Phase 7** (DEC-015).
