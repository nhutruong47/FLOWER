# Roadmap

- **Status:** PROPOSED. The phases are agreed with the owner. Scope, order and exit criteria can change at each phase gate.
- **Last updated:** 2026-11-23

Each phase starts only after the previous phase's exit criteria are met **and** the owner approves (a phase gate). Overlaps are listed explicitly in the table.

| Phase | Goal | Key deliverables | Lead agent(s) | Exit criteria | Status |
|---|---|---|---|---|---|
| 0. Workspace inspection | Understand what already exists | Inspection report | Orchestrator | Owner has reviewed the report | DONE |
| 1. Project governance | Set up how the team works | `CLAUDE.md`, agents, skills, settings and hooks, `project-management/`, `docs/` structure | Orchestrator | Owner approves the scaffold; settings and hooks verified | DONE |
| 2. Business analysis | Capture how the business actually works | Process descriptions, actors and roles, glossary; answers to B1–B10; draft REQ entries | business-analyst | Every open business question answered or explicitly deferred | NEXT (needs owner answers) |
| 3. Requirements approval | Agree what will be built | Approved REQ list with acceptance criteria and priorities; MVP scope | business-analyst, owner | Owner signs off each in-scope REQ (status APPROVED) | PLANNED |
| 4. Solution architecture | Decide how it will be built | Module map, context and container diagrams, API contract approach, confirmed conventions (DEC-010), ADRs | solution-architect, security-reviewer | ADRs accepted; every approved REQ assigned to a module | PLANNED |
| 5. Database design | Design the data model | ERD, table specs, migration plan, constraints for REQ-002/005–008/010 | database-engineer | Model reviewed against the approved REQs; plan approved | PLANNED |
| 6. UI/UX design | Design the screens | User flows, wireframes for storefront and admin, design tokens | frontend-developer, business-analyst | Owner approves the flows and wireframes (may overlap Phase 5) | PLANNED |
| 7. Backend development | Build the modules | Spring Boot modules, migrations, unit and integration tests, OpenAPI spec | backend-developer, database-engineer | Module tasks DONE, with acceptance criteria verified | PLANNED |
| 8. Frontend development | Build both apps | Storefront and admin apps, shared package, component tests | frontend-developer | Screen tasks DONE (may overlap Phase 7 once the API contract is fixed) | PLANNED |
| 9. Integration | Connect the end-to-end flows | Frontend wired to the backend; payment provider sandbox integration | backend-developer, frontend-developer | Critical journeys work end to end in a test environment | PLANNED |
| 10. Testing and security | Prove quality and safety | Playwright E2E suite, security review, fixes | qa-engineer, security-reviewer | No open critical or high bugs; security findings resolved or accepted | PLANNED |
| 11. Deployment preparation | Make it deployable | Dockerfiles, CI/CD pipelines, environment configuration, runbooks | devops-engineer | Staging deployment reproducible from CI | PLANNED |
| 12. Production readiness | Go-live readiness | Backup and restore, monitoring, go-live checklist | devops-engineer, owner | Owner go-live approval (deployment itself needs explicit approval) | PLANNED |

## Unconfirmed items that affect the roadmap

- **Business questions B1–B10** ([open-questions.md](../docs/requirements/open-questions.md)): they decide the module list and scope from Phase 2 onwards.
- **REQ-004 per-branch pricing** is NEEDS_CONFIRMATION.
- **No local Docker (DEC-015):** integration tests run only in CI, so a CI pipeline that runs Testcontainers (otherwise Phase 11 work) must be in place **at the start of Phase 7**. How to run PostgreSQL locally by hand is still open (T12).
- **T6 hosting target:** unknown. It shapes Phases 11 and 12.
- **DEC-010 conventions** are PROPOSED until confirmed in Phases 4 and 5.
