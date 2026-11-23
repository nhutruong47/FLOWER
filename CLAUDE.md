# CLAUDE.md: FlowerChain

Rules for Claude Code (and humans) working in this repository. Read this file first, then [project-management/CURRENT_STATE.md](project-management/CURRENT_STATE.md).

## 1. Project overview

FlowerChain is a management and e-commerce platform for one business that runs multiple flower shop branches (initial target: 5–30 branches). It has two parts:

- a **customer storefront** (the online shop), and
- an **internal management system** for head office and branch staff.

The current phase is in [CURRENT_STATE.md](project-management/CURRENT_STATE.md) and the plan in [ROADMAP.md](project-management/ROADMAP.md).

## 2. Business scope

The owner's brief puts the end-to-end customer website and internal management system in scope. Everything else is **not defined yet**: modules, workflows, roles, payment providers, delivery, promotions. Those come from Phase 2 (business analysis) and Phase 3 (requirements approval). Unanswered questions are in [open-questions.md](docs/requirements/open-questions.md).

### Baseline business rules (owner brief, 2026-11-23)

These rules come from the project owner. Every design and every line of code must respect them. They get formal approval in Phase 3. The full register is [REQUIREMENTS.md](docs/requirements/REQUIREMENTS.md).

| ID | Rule | Status |
|---|---|---|
| REQ-001 | One business can own multiple branches. | DRAFT |
| REQ-002 | Inventory belongs to a specific branch. | DRAFT |
| REQ-003 | The product catalog is managed centrally, unless approved requirements say otherwise. | DRAFT (default) |
| REQ-004 | Product prices may vary by branch. | **NEEDS_CONFIRMATION** (B9): do not build pricing until confirmed |
| REQ-005 | Every order has a traceable processing branch. | DRAFT |
| REQ-006 | Orders keep snapshots of product and price data at order time. | DRAFT |
| REQ-007 | Available inventory must never become negative. | DRAFT |
| REQ-008 | Every inventory change is auditable. | DRAFT |
| REQ-009 | Payment webhooks are verified on the server. | DRAFT |
| REQ-010 | The same payment is never processed twice. | DRAFT |
| REQ-011 | The backend enforces branch-level authorization. | DRAFT |
| REQ-012 | A `branchId` sent by the frontend is never trusted without an authorization check. | DRAFT |

### Handling unconfirmed information

- Never turn an assumption into a requirement. Label findings **CONFIRMED**, **INFERRED**, **NEEDS CONFIRMATION** or **NOT FOUND**.
- If a task needs an answer that isn't documented, stop. Add the question to `open-questions.md` and a blocker to [BLOCKERS.md](project-management/BLOCKERS.md), then ask the human.

## 3. Technology stack

| Area | Choice | Decision |
|---|---|---|
| Architecture | Modular monolith | DEC-001 |
| Backend | Java 21, Spring Boot 3.x (Spring MVC, Spring Security, Spring Data JPA) | DEC-002 |
| Build | Maven via wrapper (`mvnw`) | DEC-003 |
| Database | PostgreSQL, Flyway | DEC-004 |
| Frontend | React, TypeScript, Vite, Tailwind CSS; two apps (storefront, admin) plus a shared package in an npm workspace | DEC-005 |
| Testing | JUnit 5, Mockito, Testcontainers, Playwright | DEC-006 |
| Infrastructure | Docker, GitHub Actions | DEC-007 |

Adding any other framework or library requires a new DEC entry and human approval.

**Build and test commands:** none yet. List them here once `backend/` and `frontend/` exist.

## 4. Architecture principles

- One Spring Boot application in `backend/`, split into business modules. The module list comes from Phase 4. Do not create modules before the requirements behind them are approved.
- Each module owns its package (proposed: `com.flowerchain.<module>`), its database tables and its business rules.
- Modules call each other only through a module's public API (`<module>.api` interfaces and DTOs) or through domain events. Never through another module's entities, repositories or internal classes. No cycles between modules.
- Business rules live in the domain/service layer, never in controllers or the frontend.
- Branch scoping is enforced in the backend for every module (see §8).
- The storefront and admin apps never import from each other. Shared code goes in `frontend/packages/shared`.
- Prefer the simplest solution that meets the requirement.

## 5. Coding conventions (PROPOSED, confirm in Phase 4, DEC-010)

**Java**
- Constructor injection only. Transactions at the service layer. No business logic in controllers.
- Entities never leave their module. APIs accept and return DTOs (Java records) with Bean Validation.
- Money uses `BigDecimal`, never `double` or `float`. Time uses `java.time`, stored and sent in UTC.
- Log with SLF4J. Never log passwords, tokens, secrets or full payment payloads.

**TypeScript / React**
- TypeScript `strict` mode. Avoid `any`; if it is unavoidable, add a comment explaining why.
- Function components and hooks, styled with Tailwind. Call the backend only through the shared API client.
- Never render HTML from user or server data with `dangerouslySetInnerHTML`.

**General**
- Formatting basics come from `.editorconfig`. Line endings are LF.
- Code, identifiers and commit messages are in English. The languages shown to users are not decided yet (B7).

## 6. Database conventions (PROPOSED, confirm in Phase 5, DEC-010)

- Change the schema **only** through Flyway migrations in `backend/src/main/resources/db/migration`. Name them `V<YYYYMMDDHHmm>__<short_description>.sql`. Never edit a migration after it is merged; add a new one.
- Hibernate `ddl-auto` must be `validate` or `none`, never `update` or `create`.
- Table and column names are `snake_case`; table names are plural. Every table has an `id` primary key (type decided in Phase 5) and `created_at`/`updated_at` columns of type `timestamptz`.
- Money uses `numeric` with explicit precision, never floating point.
- Enforce rules in the database too, not only in code:
  - Every table that belongs to a branch has `branch_id NOT NULL` with a foreign key (REQ-002, REQ-005).
  - A `CHECK` constraint keeps available stock at or above zero (REQ-007).
  - `UNIQUE` constraints on payment provider event and transaction IDs (REQ-010).
- Record every inventory change with who, when, why, branch, quantity change and source document (REQ-008).
- Order lines copy product name, SKU and unit price at order time. Historical orders never read live catalog prices (REQ-006).
- Stock updates use row locks or optimistic locking, and a concurrency test proves that stock cannot go negative.
- Never run migrations against a shared or production database from a developer machine.

## 7. API conventions (PROPOSED, confirm in Phase 4, DEC-010)

- REST with JSON under `/api/v1`, with separate route groups for the storefront and the admin app. Resource names are plural nouns; JSON fields are camelCase.
- Errors use RFC 9457 Problem Details (`application/problem+json`). Never return stack traces.
- Status codes: 400 for invalid input (with field details), 401 when not logged in, 403 when not allowed. Return 404 instead of 403 when even confirming that a record exists would leak data.
- List endpoints are paginated, with a maximum page size. Timestamps are ISO-8601 UTC.
- Operations that can be retried (placing an order, payments) accept an `Idempotency-Key` header.
- The OpenAPI contract in `docs/api/` must match the code.

## 8. Security requirements

- Spring Security denies by default. Every endpoint states who may call it.
- **Branch-level authorization (REQ-011, REQ-012):** the branches a user may act on come from the authenticated user on the server. Check every `branchId` in a path, query or body against that list, and filter every query for branch-owned data by it. Customers see only their own orders and data.
- **Payment webhooks (REQ-009, REQ-010):** verify authenticity on the server using the provider's documented method before doing anything else. A browser redirect is never proof of payment. Process each provider event exactly once (unique constraint plus idempotent handler) and handle retries and out-of-order delivery.
- Hash passwords with Spring's `DelegatingPasswordEncoder` (bcrypt or stronger). Never store card data.
- Use parameterized queries only. Validate input on the server. Allow only known origins for CORS. Enable CSRF protection if cookie sessions are used.
- Keep an audit trail for logins, role changes, inventory changes, price changes, order status changes and refunds.
- Secrets come only from environment variables or a secret store. Never commit them. Agents must not read `.env` files (blocked by `.claude/settings.json` and a hook). Commit only `.env.example` with placeholder values.
- The security reviewer must review every change touching login, authorization, branch scoping, payments or inventory.

## 9. Testing requirements

- Unit tests: JUnit 5 and Mockito for domain and service logic.
- Integration tests: Testcontainers with PostgreSQL, covering repositories, migrations and security. Never H2 or other in-memory stand-ins. Docker is not installed locally yet, so these tests run in CI for now (open question T4).
- End-to-end: Playwright for critical user journeys. The frontend unit-test tool is chosen in Phase 4 (T11).
- Required tests for the baseline rules:
  - concurrent orders cannot make stock negative (REQ-007)
  - the inventory audit trail is recorded (REQ-008)
  - orders keep their snapshots (REQ-006)
  - forged webhooks are rejected and duplicate webhooks are ignored (REQ-009, REQ-010)
  - cross-branch access is denied (REQ-011, REQ-012)
- Every test case has a TEST-xxx ID linked to a REQ in [TEST_CASES.md](docs/testing/TEST_CASES.md).
- Never say tests pass without running them. Record the command and the result in the task's notes.

## 10. Git workflow

- `main` holds only reviewed work. Name branches `feat/TASK-xxx-short-name`, `fix/BUG-xxx-short-name`, `docs/…` or `chore/…`.
- Commit messages follow Conventional Commits and include the ID, e.g. `feat(inventory): block negative stock (TASK-042)`.
- Merge through a reviewed pull request. Branch protection is not decided yet (T8).
- Never force-push `main`, rewrite published history, or commit secrets or build output.
- Claude commits and pushes only when a human asks.

## 11. Documentation requirements

| What | Where |
|---|---|
| Requirements (REQ) and open questions | `docs/requirements/` |
| Business analysis | `docs/business-analysis/` |
| Decisions (DEC); ADRs for major architecture decisions | `project-management/DECISIONS.md`, `docs/adr/` |
| Architecture, database, API, UI/UX, testing (TEST), deployment | `docs/architecture/`, `docs/database/`, `docs/api/`, `docs/ui-ux/`, `docs/testing/`, `docs/deployment/` |
| Tasks, progress, blockers, bugs, current state, roadmap | `project-management/` |

- At the end of each work session, update the task status in `TASKS.md` and add an entry to `PROGRESS.md`.
- Update `CURRENT_STATE.md` whenever the phase, a blocker or the next step changes.
- Every document shows its status (DRAFT, APPROVED, …) and its last-updated date.

## 12. Working with existing code and files

- Inspect before you change anything. Check whether a file exists before creating it.
- Never delete, rename or move files without explicit approval.
- Keep existing configuration. Explain every change to `README.md`, `CLAUDE.md`, package manifests and build files.
- Keep diffs small and focused, follow existing patterns, and don't reformat unrelated code.
- Add no dependencies without approval. Build no business features before the related REQ is approved (Phase 3).
- Never connect to production systems or deploy without explicit human approval.

## 13. IDs, statuses and team

- **IDs:** REQ-001, DEC-001, TASK-001, BUG-001, TEST-001, and BLK-001 for blockers. IDs are never reused.
- **Task statuses:** `PENDING → IN_PROGRESS → IMPLEMENTED → TESTING → FIXING → CODE_REVIEW → DONE`. FIXING applies only when testing or review finds a defect; afterwards the task returns to TESTING. If testing finds no defect, the task goes straight from TESTING to CODE_REVIEW. **A task becomes DONE only after every acceptance criterion has been verified, with the evidence recorded.**
- **Agents** (`.claude/agents/`): business-analyst, solution-architect, backend-developer, frontend-developer, database-engineer, qa-engineer, security-reviewer (read-only) and devops-engineer.
- **Skills** (`.claude/skills/`): `project-orchestration` coordinates the agents and enforces task dependencies. The others are business-analysis, architecture-design, backend-development, frontend-development, database-design, testing and deployment.
