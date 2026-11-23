# Tasks

- **Last updated:** 2026-11-23

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

A blocked task keeps its status and references its BLK ID.

## Board

| ID | Title | Phase | Owner | Status | Depends on |
|---|---|---|---|---|---|
| TASK-001 | Workspace inspection | 0 | Orchestrator | DONE | – |
| TASK-002 | Repository foundation files | 0–1 | Orchestrator | DONE | TASK-001 |
| TASK-003 | Install local toolchain (JDK 21, Node.js LTS) | 1 | Orchestrator + owner | DONE | – |
| TASK-004 | Project governance scaffold | 1 | Orchestrator | DONE | TASK-001 |
| TASK-005 | Commit Phase 1 work and push the feature branch | 1 | Orchestrator | IN_PROGRESS | TASK-002, TASK-004, owner approval |
| TASK-006 | Business analysis: answer B1–B10, draft processes and roles | 2 | business-analyst | PENDING | TASK-004 |
| TASK-007 | Decide the local Docker strategy (T4) | 1–4 | Owner | PENDING | – |

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
  - [ ] Commits on `chore/phase-1-foundation` with Conventional Commit messages.
  - [ ] Branch pushed to `origin`; `main` untouched.
  - [ ] Pull request opened, if the owner wants one (T8).

### TASK-006: Business analysis kickoff
- **Acceptance criteria**
  - [ ] B1–B10 answered by the owner or explicitly deferred.
  - [ ] Draft process descriptions and a role list in `docs/business-analysis/`, each item labelled with its source.

### TASK-007: Decide the local Docker strategy
- **Acceptance criteria**
  - [ ] The owner picks Docker Desktop (with the licence confirmed), an alternative, or CI-only integration tests. Recorded as a DEC entry.
