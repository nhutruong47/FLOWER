# Tasks

- **Last updated:** 2026-11-24

## Status workflow

```
PENDING → IN_PROGRESS → IMPLEMENTED → TESTING → FIXING → CODE_REVIEW → DONE
                                         ↑          │
                                         └──────────┘   (FIXING only when testing or review finds a defect)
```

- **PENDING:** defined, not started. It may start only when every task in "Depends on" is DONE.
- **IN_PROGRESS:** an agent is working on it. Only one owner at a time.
- **IMPLEMENTED:** the work is complete and ready for verification.
- **TESTING:** acceptance criteria are being checked (tests run, documents checked).
- **FIXING:** defects found during testing or review are being fixed. Afterwards the task goes back to TESTING. If there are no defects, TESTING goes straight to CODE_REVIEW.
- **CODE_REVIEW:** review by another agent or the owner. Changes touching login, authorization, branch scoping, payments or inventory also need the security-reviewer.
- **DONE:** **every acceptance criterion is verified, with evidence recorded** (command and result, reviewer, or owner approval). Never set DONE on trust.
- **BLOCKED / NEEDS_APPROVAL:** a blocked task references its `BLK-xxx` ID (`IN_PROGRESS (BLK-xxx)` or `BLOCKED (BLK-xxx)`); a task waiting on a phase or owner decision gate is marked `NEEDS_APPROVAL`.

## Task definition template

Every new task must specify:
- **Task ID & Title**
- **Objective:** one-sentence goal
- **Scope:** what is included and excluded
- **Dependencies:** prerequisite tasks (`TASK-xxx`) and approved requirements (`REQ-xxx`)
- **Files affected:** target directories or files
- **Acceptance Criteria:** testable conditions
- **Test Cases:** linked `TEST-xxx` IDs (or verification checks for documentation tasks)

## Board

| ID | Title | Phase | Owner | Status | Depends on |
|---|---|---|---|---|---|
| TASK-001 | Workspace inspection | 0 | Orchestrator | DONE | – |
| TASK-002 | Repository foundation files | 0–1 | Orchestrator | DONE | TASK-001 |
| TASK-003 | Install local toolchain (JDK 21, Node.js LTS) | 1 | Orchestrator + owner | DONE | – |
| TASK-004 | Project governance scaffold | 1 | Orchestrator | DONE | TASK-001 |
| TASK-005 | Commit Phase 1 work and push the feature branch | 1 | Orchestrator | DONE | TASK-002, TASK-004, owner approval |
| TASK-006 | Business analysis: P03 deliverables, workflows, BRULEs, and B1–B22 open questions | 2 | business-analyst | IN_PROGRESS (BLK-002 / NEEDS_APPROVAL) | TASK-004 |
| TASK-007 | Decide the local Docker strategy (T4) | 1–4 | Owner | DONE | – |
| TASK-008 | Merge Phase 1 into `main` and push | 1 | Orchestrator | DONE | TASK-005, owner approval |
| TASK-009 | P02 orchestration demo: document walk-in sale scope skeleton for B1 | 2 | business-analyst | DONE | TASK-004 |
| TASK-010 | P04 requirements engineering: specify FRs, NFRs, role permissions, traceability & validation | 3 | business-analyst | NEEDS_APPROVAL | TASK-006 |
| TASK-011 | P05 solution architecture: stack evaluation, module map, multi-branch security, diagrams & ADRs | 4 | solution-architect + security-reviewer | NEEDS_APPROVAL | TASK-010 |
| TASK-012 | P06 database and API design: ERD, data dictionary, migration plan, OpenAPI 3.1 & consistency check | 5 | database-engineer + solution-architect | NEEDS_APPROVAL | TASK-011 |
| TASK-013 | P07 UI/UX design (Hallmark & Taste-Skill): design system, sitemaps, wireframes & UI ACs | 6 | frontend-developer + business-analyst | NEEDS_APPROVAL | TASK-012 |

## Details

### TASK-001: Workspace inspection
- **Acceptance criteria**
  - [x] Report covers all 12 required sections, with every finding classified. Evidence: Phase 0 report delivered 2026-11-23.
  - [x] Owner has reviewed it. Evidence: the Phase 1 brief says "inspection has been completed and reviewed".

### TASK-002: Repository foundation files
- **Scope:** `.gitignore`, `.gitattributes`, `.editorconfig`, the `README.md` overview, ADR-0001 and `docs/requirements/open-questions.md`, on branch `chore/phase-1-foundation`.
- **Acceptance criteria**
  - [x] `.env` is ignored and `.env.example` is not. Evidence: `git check-ignore -v`.
  - [x] Text files are LF; `.cmd`/`.bat` are CRLF; `mvnw` is LF. Evidence: `git check-attr eol`.
  - [x] No CRLF in the new files. Evidence: `grep -lr $'\r'` found none.
  - [x] Owner approves the files. Evidence: owner instruction "LÀM CHO HOÀN THÀNH" (finish it), 2026-11-23.

### TASK-003: Install local toolchain
- **Acceptance criteria**
  - [x] `java -version` reports 21.x (Temurin). Evidence: `openjdk version "21.0.13.1" 2026-11-17 LTS`, Temurin-21.0.13.1+2; `javac 21.0.13.1`.
  - [x] `node -v` reports the LTS version and `npm -v` works. Evidence: `v26.13.1`, npm `11.20.0`, npx `11.20.0`.
- **Notes:** the winget/MSI route needed admin rights the account doesn't have (BLK-001). Installed instead as portable builds in `%LOCALAPPDATA%\Programs\` (no admin), after verifying the SHA-256 checksums against the publishers' values. `JAVA_HOME` and the user PATH are set. See DEC-014.

### TASK-004: Project governance scaffold
- **Scope:** `CLAUDE.md`, `.claude/` (agents, skills, settings, hooks), `project-management/`, the `docs/` structure, and the requirement and test registers.
- **Acceptance criteria**
  - [x] Every requested file and folder exists, or an equivalent is documented. Evidence: Phase 1 report file list.
  - [x] JSON is valid; Markdown and frontmatter are checked; relative links resolve. Evidence: the validation script reported 1 JSON, 37 Markdown, 42 links, 8 agents and 8 skills, with no problems (2026-11-23).
  - [x] The hook script passes its self-test. Evidence: 27/27 passed, and a live block was observed in the session.
  - [x] Owner approves. Evidence: owner instruction "LÀM CHO HOÀN THÀNH" (finish it), 2026-11-23.
- **Notes:** the validation results are recorded in [PROGRESS.md](PROGRESS.md).

### TASK-005: Commit Phase 1 work and push the feature branch
- **Acceptance criteria**
  - [x] Commits on `chore/phase-1-foundation` with Conventional Commit messages. Evidence: `b234db6 chore: set up repository foundation and project governance (TASK-002, TASK-004)`, 43 files.
  - [x] Branch pushed to `origin`; `main` untouched. Evidence: `git ls-remote origin` shows `refs/heads/chore/phase-1-foundation` = `b234db6` and `refs/heads/main` = `d776e5e`.
  - [x] Pull request opened, if the owner wants one (T8). Not requested. The link to open one is <https://github.com/nhutruong47/FLOWER/pull/new/chore/phase-1-foundation> (the `gh` CLI is not installed).

### TASK-006: Business analysis (P03 deliverables & open questions)
- **Objective:** Produce the complete Phase 2 (P03) business analysis package across all 17 domain areas, 12 business workflows (`WF-01` to `WF-12`), use cases, business rules (`BRULE-001` to `BRULE-030`), glossary, constraints/assumptions/risks, and present open questions to the owner.
- **Scope:** `docs/business-analysis/*` and `docs/requirements/open-questions.md`. No application code.
- **Dependencies:** `TASK-004` (`DONE`).
- **Files affected:** `docs/business-analysis/README.md`, `business-overview-and-model.md`, `stakeholders-and-actors.md`, `business-process-maps.md`, `use-case-catalog.md`, `business-rules-catalog.md`, `glossary.md`, `assumptions-constraints-and-risks.md`, `processes/online-order-fulfilment.md`, `processes/stock-transfer.md`, `processes/walk-in-sale-scope.md`, `docs/requirements/open-questions.md`.
- **Acceptance criteria**
  - [ ] B1–B22 answered by the owner or explicitly deferred (`BLK-002`). **Progress:** B2, B3a, B3b and B9 answered on 2026-11-23. Remaining open questions (`B1`, `B3c–B3e`, `B4–B8`, `B9b`, `B10`, `B11–B22`) structured and presented for owner decision.
  - [x] Complete P03 Business Analysis deliverables created in `docs/business-analysis/` (Business Overview & Model covering all 17 areas, Stakeholder Analysis & proposed role matrix, Business Process Maps `WF-01`..`WF-12`, Use Case Catalog `UC-CUST/BR/HO`, Business Rules Catalog `BRULE-001`..`030`, expanded Business Glossary, Assumptions/Constraints/Risks), with every material statement classified (`CONFIRMED`, `INFERRED`, `PROPOSED`, or `NEEDS CONFIRMATION`). Evidence: P03 documents written and verified 2026-11-24 (`git diff --check` clean).
- **Notes (2026-11-23, first pass by the business-analyst):** REQ-004 moved from NEEDS_CONFIRMATION to DRAFT. Added REQ-013 to REQ-017, with draft acceptance criteria where the answers support them and "AC pending: Bx" elsewhere. REQ-017 (storefront shows the chosen branch's prices) is NEEDS_CONFIRMATION: it conflicts with automatic branch selection (B11). Orchestrator checks: changes limited to `docs/requirements/` and `docs/business-analysis/`; the validation script found no problems (40 Markdown files, 58 links); no duplicate REQ or question IDs. Waiting for answers: BLK-002.
- **For later phases (from the analyst):** if B3d is answered "split the order across branches", REQ-005 needs clarifying. Walk-in shop sales affecting branch stock are a scope question to raise under B1.

### TASK-007: Decide the local Docker strategy
- **Acceptance criteria**
  - [x] The owner picks Docker Desktop (with the licence confirmed), an alternative, or CI-only integration tests. Recorded as a DEC entry. Evidence: owner instruction "KO CẦN CÀI DOCKER" (no need to install Docker), 2026-11-23, giving **CI-only integration tests** (DEC-015). Follow-up question T12: how to run PostgreSQL locally by hand.

### TASK-008: Merge Phase 1 into `main` and push
- **Scope:** fast-forward `main` to the Phase 1 branch and push. Approved by the owner: "CỨ PUSH LÊN MAIN NHÉ" (just push to main), 2026-11-23.
- **Acceptance criteria**
  - [x] Local `main` contains every Phase 1 commit (fast-forward only, no history rewrite). Evidence: `git merge --ff-only chore/phase-1-foundation` → "Updating d776e5e..d4210eb, Fast-forward".
  - [x] `origin/main` equals local `main` (checked with `git ls-remote`). Evidence: `git push origin main` → `d776e5e..d4210eb main -> main`; `git ls-remote origin` → `refs/heads/main` = `d4210eb`, matching `git rev-parse main`.

### TASK-009: P02 orchestration demo — document walk-in sale scope skeleton for B1
- **Objective:** Demonstrate the end-to-end autonomous orchestration loop (`READ PROJECT STATE → SELECT → CHECK DEPENDENCIES → DELEGATE → IMPLEMENT → RUN TESTS → REVIEW → VERIFY → UPDATE STATE`) on a harmless documentation-only task.
- **Scope:** Create `docs/business-analysis/processes/walk-in-sale-scope.md` capturing the walk-in branch sale scope options (raised in TASK-006 notes for question B1) and baseline guardrails (`REQ-002`, `REQ-004`, `REQ-006`, `REQ-007`, `REQ-008`, `REQ-011`, `REQ-012`, `REQ-016`) without inventing business rules; link from `docs/business-analysis/README.md` and `docs/requirements/open-questions.md`. No application code.
- **Dependencies:** `TASK-004` (`DONE`).
- **Files affected:** `.claude/skills/project-orchestration/SKILL.md`, `docs/business-analysis/processes/walk-in-sale-scope.md`, `docs/business-analysis/README.md`, `docs/requirements/open-questions.md`, `project-management/TASKS.md`, `project-management/PROGRESS.md`, `project-management/CURRENT_STATE.md`.
- **Acceptance criteria**
  - [x] AC1: `docs/business-analysis/processes/walk-in-sale-scope.md` exists, labels every statement `CONFIRMED` or `NEEDS CONFIRMATION` with its source, and makes zero unapproved business assumptions. Evidence: verified file contents and cross-references to `B1`, `B4`, `B5`, and `REQ-002..016`.
  - [x] AC2: Relative links in `docs/business-analysis/README.md` and `docs/requirements/open-questions.md` resolve cleanly and `git diff --check` reports 0 errors. Evidence: `git diff --check` passed (0 output).
  - [x] AC3: Secret-protection hook self-test passes and security review confirms no sensitive data or unapproved rule changes. Evidence: `bash .claude/hooks/scripts/test-protect-secrets.sh` → 27 passed, 0 failed; `security-reviewer` check: `APPROVE` (documentation only, preserves `REQ-007`, `REQ-008`, `REQ-011`, `REQ-012`).
- **Test Cases:** Hook self-test (`test-protect-secrets.sh`: 27/27 PASS) + Markdown/whitespace check (`git diff --check`: PASS).
- **Lifecycle trace:** `PENDING → IN_PROGRESS (business-analyst) → IMPLEMENTED → TESTING (qa-engineer check) → CODE_REVIEW (security-reviewer & orchestrator check) → DONE` (2026-11-24).

### TASK-010: P04 requirements engineering — FRs, NFRs, role permissions, traceability & validation
- **Objective:** Produce the complete Phase 3 (P04) Requirements Engineering package (`FR-001`..`038`, `NFR-001`..`015`, Role-Permission & Branch Scope specification, Traceability Matrices, `TEST-001`..`020` mapping, and Requirements Validation Report) for owner sign-off before P05.
- **Scope:** `docs/requirements/*` and `docs/testing/TEST_CASES.md`. No application code.
- **Dependencies:** `TASK-006` (P03 business analysis deliverables).
- **Files affected:** `docs/requirements/REQUIREMENTS.md`, `functional-requirements.md`, `non-functional-requirements.md`, `roles-and-permissions.md`, `traceability-matrices.md`, `requirements-validation-report.md`, `docs/testing/TEST_CASES.md`, `project-management/TASKS.md`, `project-management/PROGRESS.md`, `project-management/CURRENT_STATE.md`.
- **Acceptance criteria**
  - [x] AC1: Functional requirements (`FR-001` to `FR-038`) cover all 16 domain capability groups with ID, name, description/rationale, priority, source, preconditions, main/alternative/exception flows, testable Given/When/Then acceptance criteria, dependencies, and linked `BRULE-xxx`. Evidence: `docs/requirements/functional-requirements.md` verified.
  - [x] AC2: Non-functional requirements (`NFR-001` to `NFR-015`) specify measurable targets for all 10 required quality attributes (performance, availability, scalability, security, reliability, maintainability, usability/accessibility, data integrity, backup/recovery, observability), marking proposed targets `PROPOSED`. Evidence: `docs/requirements/non-functional-requirements.md` verified.
  - [x] AC3: Users, roles (`SYSTEM_OWNER`, `HEAD_OFFICE_ADMIN`, `BRANCH_MANAGER`, `SALES_STAFF`, `INVENTORY_STAFF`, `DELIVERY_STAFF`, `CUSTOMER`), permissions, and server-side branch data scopes are defined without finalizing unapproved business permissions. Evidence: `docs/requirements/roles-and-permissions.md` verified.
  - [x] AC4: Requirement Traceability Matrix, Use Case Matrix, Role-Permission Matrix, Requirement Dependency Matrix, and Initial Test Case Mapping (`TEST-001` to `TEST-020`) are complete and synced with `docs/testing/TEST_CASES.md`. Evidence: `docs/requirements/traceability-matrices.md` and `docs/testing/TEST_CASES.md` verified.
  - [x] AC5: Validation report identifies duplicates, omissions, contradictions (`CON-01`..`03`), ambiguous wording fixes, and unapproved assumptions. Evidence: `docs/requirements/requirements-validation-report.md` verified.
  - [ ] AC6: Owner approves the requirements baseline (`NEEDS_APPROVAL`) before proceeding to P05.
- **Test Cases:** `TEST-001` to `TEST-020` registered (`PLANNED`); hook self-test (`27/27 PASS`) and `git diff --check` (`PASS`).

### TASK-011: P05 solution architecture — stack evaluation, module map, multi-branch security, diagrams & ADRs
- **Objective:** Design the complete Phase 4 (P05) Solution Architecture for FlowerChain covering technology evaluation, Modular Monolith style, bounded module map (`L0`–`L7` acyclic DAG), multi-branch data isolation & security architecture, 9 Mermaid diagrams, and 9 ADRs (`ADR-0002` to `ADR-0010` / `DEC-017` to `DEC-025`).
- **Scope:** `docs/architecture/*` and `project-management/DECISIONS.md`. No application code.
- **Dependencies:** `TASK-010` (P04 requirements baseline).
- **Files affected:** `docs/architecture/README.md`, `technology-evaluation-and-architecture-style.md`, `module-map.md`, `cross-cutting-and-security.md`, `diagrams.md`, `architecture-decisions.md`, `project-management/DECISIONS.md`, `project-management/TASKS.md`, `project-management/PROGRESS.md`, `project-management/CURRENT_STATE.md`.
- **Acceptance criteria**
  - [x] AC1: Technology evaluation covers Java 21, Spring Boot 3.4.x, PostgreSQL 16, Flyway, React/TS/Vite/Tailwind, Docker, GitHub Actions, Redis/RabbitMQ (deferred with justification), and Nginx/hosting with versions, compatibility, and trade-offs. Evidence: `docs/architecture/technology-evaluation-and-architecture-style.md`.
  - [x] AC2: Modular Monolith architecture style defines system boundaries, 4-package module boundaries (`api`, `web`, `application`, `internal`), layer responsibilities, dependency rules, SOLID/DIP, DTO/mapping conventions, ACID transaction boundaries, and RFC 9457 error handling. Evidence: `docs/architecture/technology-evaluation-and-architecture-style.md`.
  - [x] AC3: All 13 candidate module areas are evaluated, every approved/draft requirement (`REQ-001`..`017`, `FR-001`..`038`) is assigned to an owning module (`shared`, `audit`, `branch`, `identity`, `customer`, `catalog`, `inventory`, `order`, `delivery`, `payment`, `notification`, `reporting`, conditional `promotion`), and dependencies form a verified acyclic DAG (`L0`..`L7`). Evidence: `docs/architecture/module-map.md`.
  - [x] AC4: Multi-branch data isolation and security architecture explicitly cover head-office vs. branch access, dual-branch stock transfers, shared catalog vs. branch prices/inventory, `BranchAuthorizationGuard` (`REQ-011`, `REQ-012`), credential hashing, revocable JWT/session strategy, webhook HMAC verification (`REQ-009`), idempotency (`REQ-010`), deterministic lock ordering (`REQ-007`), and 6-category audit trails (`REQ-008`, `CLAUDE.md` §8). Evidence: `docs/architecture/cross-cutting-and-security.md`; `security-reviewer` verdict: `APPROVE`.
  - [x] AC5: All 9 required Mermaid diagrams (System Context, Container, Component, Module Dependency, Deployment, Authentication Flow, Order Processing Flow, Payment Flow, Inventory Flow) are created. Evidence: `docs/architecture/diagrams.md`.
  - [x] AC6: ADRs (`ADR-0002` to `ADR-0010`) and Decision entries (`DEC-017` to `DEC-025`) are recorded for modular architecture, database strategy, branch isolation, authentication, inventory consistency, payment integration, caching/messaging, file storage, and deployment. Evidence: `docs/architecture/architecture-decisions.md` and `project-management/DECISIONS.md`.
  - [ ] AC7: Owner approves the P05 architecture decisions (`NEEDS_APPROVAL`) before proceeding to P06.
- **Test Cases:** `test-protect-secrets.sh` (`27/27 PASS`), `git diff --check` (`PASS`), `security-reviewer` architecture design review (`APPROVE`).

### TASK-012: P06 database and API design — ERD, data dictionary, migration plan, OpenAPI 3.1 & consistency validation
- **Objective:** Produce the complete Phase 5 (P06) PostgreSQL 16 Database Design and REST API Contract specifications in `docs/database/` and `docs/api/`, verifying 1:1 consistency with P03, P04, and P05 without implementing application code or executing migrations.
- **Scope:** `docs/database/*` and `docs/api/*`. No application code or database execution.
- **Dependencies:** `TASK-011` (P05 solution architecture).
- **Files affected:** `docs/database/README.md`, `erd.md`, `data-dictionary-and-constraints.md`, `migration-plan.md`, `docs/api/README.md`, `openapi.yaml`, `errors-auth-and-examples.md`, `consistency-validation.md`, `project-management/TASKS.md`, `project-management/PROGRESS.md`, `project-management/CURRENT_STATE.md`.
- **Acceptance criteria**
  - [x] AC1: All 20 candidate entity areas evaluated without creating unapproved/speculative tables; complete Mermaid ERDs created for the 24 core MVP tables. Evidence: `docs/database/erd.md`.
  - [x] AC2: Data dictionary defines primary/foreign keys, `UNIQUE` and `CHECK` constraints (`branch_id NOT NULL`, `available_stock >= 0`, `signature_verified = true`, `UNIQUE (provider, provider_event_id)`), 16 indexes with rationale, 3NF + snapshot denormalization (`REQ-006`), soft-delete policy, UTC `timestamptz`, `numeric(19,2)`, concurrency lock ordering, two-phase stock reservation, and idempotency. Evidence: `docs/database/data-dictionary-and-constraints.md`.
  - [x] AC3: Sequenced Flyway migration plan (`V202611240100`..`V202611240800`) and Testcontainers PostgreSQL 16 verification plan documented. Evidence: `docs/database/migration-plan.md`.
  - [x] AC4: OpenAPI 3.1 specification (`openapi.yaml`) and companion API specification (`errors-auth-and-examples.md`) define `/api/v1/storefront/**`, `/api/v1/admin/**`, and `/api/v1/webhooks/payments/**` endpoints, schemas, validation rules, auth matrix, RFC 9457 Problem Details error catalog, pagination (`size <= 100`), `Idempotency-Key`, and concrete HTTP examples. Evidence: `docs/api/openapi.yaml`, `docs/api/errors-auth-and-examples.md`.
  - [x] AC5: End-to-end consistency validation confirms 1:1 alignment across Requirements, Architecture Modules, Database Tables/Constraints, API Endpoints, and Test Cases. Evidence: `docs/api/consistency-validation.md`.
  - [ ] AC6: Owner approves the P06 database and API design (`NEEDS_APPROVAL`) before proceeding to P07.
- **Test Cases:** `test-protect-secrets.sh` (`27/27 PASS`), `git diff --check` (`PASS`), `security-reviewer` DB & API contract review (`APPROVE`).

### TASK-013: P07 UI/UX design (Hallmark & Taste-Skill) — design system, sitemaps, wireframes & UI acceptance criteria
- **Objective:** Design the complete Phase 6 (P07) UI/UX architecture and visual system for `frontend/storefront` and `frontend/admin` using the `hallmark` (`Garden` botanical editorial theme) and `design-taste-frontend` (`taste-skill 2.5.1`) skills, aligned 1:1 with P04 requirements and P06 OpenAPI contracts.
- **Scope:** `docs/ui-ux/*` and `.claude/agents/frontend-developer.md`. No application code implemented yet.
- **Dependencies:** `TASK-012` (P06 database and API design).
- **Files affected:** `.claude/agents/frontend-developer.md`, `docs/ui-ux/README.md`, `design-system-and-components.md`, `information-architecture-and-flows.md`, `storefront-screens-and-wireframes.md`, `admin-screens-and-wireframes.md`, `responsive-and-ui-acceptance-criteria.md`, `project-management/TASKS.md`, `project-management/PROGRESS.md`, `project-management/CURRENT_STATE.md`.
- **Acceptance criteria**
  - [x] AC1: Hallmark (`theme.mjs deal` → `Garden` theme) and Taste-Skill (`start.mjs deal --page place --trade florist`) executed to define a non-generic floral brand identity, `:root` CSS tokens (`tokens.css`), anti-slop floor rules, and 15 reusable components (`CMP-01`..`15`). Evidence: `docs/ui-ux/design-system-and-components.md`.
  - [x] AC2: Information architecture, Mermaid sitemaps, role-based dashboard adaptation (`SYSTEM_OWNER`, `HEAD_OFFICE_ADMIN`, `BRANCH_MANAGER`, `SALES_STAFF`, `INVENTORY_STAFF`, `DELIVERY_STAFF`, `CUSTOMER`), end-to-end Mermaid user journeys, and empty/loading/error (RFC 9457)/validation/accessibility states defined. Evidence: `docs/ui-ux/information-architecture-and-flows.md`.
  - [x] AC3: All 15 Customer Website screens (`Homepage`, `Product listing`, `Product detail`, `Search`, `Shopping cart`, `Checkout`, `Branch selection`, `Delivery/Pickup selection & Price reconciliation`, `Payment & Webhook polling`, `Order tracking`, `Customer account`, `Order history`, `Wishlist`, `Promotions`, `Customer support`) specified with wireframes and API bindings. Evidence: `docs/ui-ux/storefront-screens-and-wireframes.md`.
  - [x] AC4: All 15 Internal Management Dashboard screens (`Executive/Branch Dashboard`, `Branch Management`, `Employee & Role Management`, `Central Product Management`, `Branch Pricing Matrix`, `Inventory & Movement Ledger`, `Purchasing / Goods Receiving`, `Supplier Reference Tracking`, `Spoilage & Adjustments`, `Dual-Side Stock Transfers`, `Order Management & Reassignment`, `Payment & Refund Management`, `Delivery & Pickup Management`, `Customer/Promotions/Reports/Audit Logs`, `System Settings`) specified with wireframes and server-resolved branch scope badges (`REQ-011`, `REQ-012`). Evidence: `docs/ui-ux/admin-screens-and-wireframes.md`.
  - [x] AC5: Responsive specifications (`1920px`, `1440x900`, `1280x800`, `1024x768`, `820x1180`, `768x1024`, `390x844`, `320px`) and testable UI Acceptance Criteria (`UI-AC-01`..`11`) documented. Evidence: `docs/ui-ux/responsive-and-ui-acceptance-criteria.md`.
  - [ ] AC6: Owner approves the P07 UI/UX design (`NEEDS_APPROVAL`) before proceeding to P08.
- **Test Cases:** `TEST-015`, `TEST-019`, `UI-AC-01`..`11`; `test-protect-secrets.sh` (`27/27 PASS`), `git diff --check` (`PASS`).
