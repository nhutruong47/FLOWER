---
name: backend-development
description: Repeatable workflow for implementing an approved FlowerChain backend task in the Spring Boot modular monolith - branch, tests first, implement inside the owning module with branch-level authorization, run the real build and tests, record evidence, and hand off for testing. Use for any change under backend/.
---

# Backend development

## Required input
- The task ID and acceptance criteria (`project-management/TASKS.md`). Its dependencies must be DONE and its REQs APPROVED.
- The module map, the API contract (`docs/api/`), and table specs for any new data (`docs/database/`)
- `CLAUDE.md` §4–9

## Workflow
1. **Check readiness.** Dependencies are DONE, the REQs are APPROVED, the design exists, and the toolchain works (`java -version` shows 21). If not, stop.
2. **Branch.** Create `feat/TASK-xxx-<short-name>` from an up-to-date `main` (or the branch the owner names).
3. **Write tests first.** Write the unit tests (JUnit 5, Mockito) and integration tests (Testcontainers PostgreSQL) that express the acceptance criteria, then run them and watch them fail.
4. **Implement inside the owning module.** Controller → service → repository. DTOs at the edges. Business rules in the service or domain layer.
5. **Apply the guardrails.**
   - Branch scope comes from the authenticated user, never from the client's `branchId` alone (REQ-011, REQ-012).
   - Stock changes are locked and audited (REQ-007, REQ-008).
   - Order lines copy their snapshots (REQ-006).
   - Webhooks are verified and idempotent (REQ-009, REQ-010).
6. **Schema changes** go through a new Flyway migration agreed with the database-engineer.
7. **Run the real commands.** Use the build and test commands listed in `CLAUDE.md` §3; never make up commands. Everything must pass.
8. **Update the contract.** Keep the OpenAPI spec in `docs/api/` in sync.
9. **Record and hand off.** Write the commands and result summaries into the task notes, set the task to IMPLEMENTED, and list any areas that need security review.

## Output format

Handoff note:

```markdown
TASK-xxx → IMPLEMENTED
Changed: <files>
Tests: <command> → <N passed, 0 failed> (<date>)
Acceptance criteria → tests: AC1 → <TestClass#method>, …
Security-sensitive areas touched: <none | list>
Open questions: <none | list>
```

## Quality checks
- Every acceptance criterion maps to a test that was run in this session.
- No cross-module access to entities or repositories. No `ddl-auto=update`. No merged migration edited.
- No secrets, no `System.out`, no logging of sensitive data. Money uses `BigDecimal`, time uses UTC `java.time`.
- No new dependency without a DEC entry.

## Stop conditions
- The acceptance criteria are untestable or unclear, or the design is missing or conflicts with the requirement.
- A schema change is needed that has no agreed spec.
- The toolchain or Docker is unavailable, so tests can't run. Report it as a blocker; never claim the tests pass.
- The change would break a baseline rule, or needs a production or shared resource.
