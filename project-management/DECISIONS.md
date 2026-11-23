# Decisions register

- **Last updated:** 2026-11-23
- **Statuses:** `PROPOSED` (awaiting owner approval) · `ACCEPTED` · `REJECTED` · `SUPERSEDED` (link the replacement)
- **Rules:** IDs are never reused. A decision changes only through a new entry that supersedes it. Major architecture decisions also get an ADR in [docs/adr/](../docs/adr/).

| ID | Decision | Status | Source | Date | Reference |
|---|---|---|---|---|---|
| DEC-001 | Modular monolith architecture | ACCEPTED | Owner brief | 2026-11-23 | [ADR-0001](../docs/adr/0001-technology-stack-and-repository-layout.md) |
| DEC-002 | Backend: Java 21, Spring Boot 3.x (MVC, Security, Data JPA) | ACCEPTED | Owner brief | 2026-11-23 | ADR-0001 |
| DEC-003 | Backend build tool: Maven, through the Maven wrapper | ACCEPTED | Owner choice | 2026-11-23 | ADR-0001 |
| DEC-004 | Database: PostgreSQL; schema managed only by Flyway | ACCEPTED | Owner brief | 2026-11-23 | ADR-0001 |
| DEC-005 | Frontend: React + TypeScript + Vite + Tailwind; two apps (storefront, admin) plus a shared package in an npm workspace | ACCEPTED | Owner brief + owner choice | 2026-11-23 | ADR-0001 |
| DEC-006 | Testing: JUnit 5, Mockito, Testcontainers, Playwright | ACCEPTED | Owner brief | 2026-11-23 | ADR-0001 |
| DEC-007 | Infrastructure: Docker and GitHub Actions | ACCEPTED | Owner brief | 2026-11-23 | ADR-0001 |
| DEC-008 | Monorepo layout: `backend/`, `frontend/`, `infra/`, `docs/`, `.github/workflows/` | ACCEPTED | Proposed in Phase 0, approved by owner | 2026-11-23 | ADR-0001 |
| DEC-009 | Local toolchain: Temurin JDK 21 and Node.js LTS via winget; local Docker deferred (T4) | SUPERSEDED by DEC-014 | Owner choice | 2026-11-23 | TASK-003 |
| DEC-010 | Coding, database and API conventions as written in `CLAUDE.md` §5–7 | PROPOSED | Orchestrator | 2026-11-23 | Confirm in Phases 4 and 5 |
| DEC-011 | Git workflow: feature branches, Conventional Commits with IDs, pull requests into `main`, no force-push to `main` | PROPOSED | Orchestrator | 2026-11-23 | Branch protection open (T8) |
| DEC-012 | ID scheme (REQ/DEC/TASK/BUG/TEST/BLK) and task status workflow; DONE only after acceptance criteria are verified | ACCEPTED | Owner brief, Phase 1 | 2026-11-23 | `CLAUDE.md` §13 |
| DEC-013 | Governance setup: 8 agents (security reviewer read-only), 8 skills, conservative permissions, a secret-file guard hook; no format or test hooks until there is code to run them on | ACCEPTED | Orchestrator; owner approved Phase 1 | 2026-11-23 | Phase 1 review |
| DEC-014 | Local toolchain installed as portable, checksum-verified builds in the user profile (no admin rights available): Temurin JDK 21.0.13.1+2 and Node.js v26.13.1 in `%LOCALAPPDATA%\Programs\`; `JAVA_HOME` and user PATH set. Local Docker still deferred (T4). | ACCEPTED | Owner ("finish it") after BLK-001 | 2026-11-23 | TASK-003, BLK-001 |
