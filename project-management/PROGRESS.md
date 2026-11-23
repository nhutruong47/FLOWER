# Progress log

Newest entries first. Each entry gives the date, what was done (with task IDs), the evidence, and what's next.

## 2026-11-23: Docker decision and merge to main (TASK-007 → DONE, TASK-008)

- The owner instructed: finish the remaining setup, push to `main`, and don't install Docker ("TỰ LÀM ĐI PHẦN CÒN LẠI, CỨ PUSH LÊN MAIN NHÉ, KO CẦN CÀI DOCKER").
- **DEC-015 (ACCEPTED): no local Docker.** Testcontainers integration tests run only in GitHub Actions, so the CI pipeline must be ready at the start of Phase 7. New open question **T12**: how to run PostgreSQL locally by hand.
- Updated everything that referred to the open Docker question: `CLAUDE.md` §9, `README.md` (layout and prerequisites), `open-questions.md` (T4 answered, T12 added), `ROADMAP.md`, the devops-engineer, database-engineer and solution-architect agents, and the deployment, database-design, testing and architecture-design skills. Historical records (DEC-009, DEC-014, ADR-0001) are left as they were.
- Not done, because only the owner can do them on their machine: restarting the IDE (so the PATH change takes effect) and closing the leftover Windows admin dialog. Stopping those installer processes automatically was refused earlier by the safety check.

## 2026-11-23: Orchestration check, autonomous development mode started (no task executed)

- **State verified:** working tree clean; `chore/phase-1-foundation` = `origin` = `f22b9e9`; `main` = `d776e5e` (not merged). The records match Git. Commands: `git status -sb`, `git ls-remote origin`.
- **Eligibility:** TASK-001 to TASK-005 are DONE. TASK-006 (Phase 2) has its dependencies met, but its acceptance criteria require **owner answers to B1–B10**, all still open, so it cannot run on its own without inventing requirements. TASK-007 is an owner decision. **No task can run autonomously.**
- **Requirements:** REQ-001 to REQ-012 are DRAFT or NEEDS_CONFIRMATION; nothing is APPROVED, so no feature work is allowed (Phase 3 gate).
- **Environment note:** this Claude session started before the PATH change, so `java` and `node` aren't visible to it. Restart the IDE before any build task. The leftover winget/msiexec admin dialog is still open.
- **Next recommended task:** TASK-006, once the owner provides answers.

## 2026-11-23: Phase 1 closed (TASK-002, TASK-003, TASK-004 → DONE)

**Done**
- The owner approved Phase 1 ("LÀM CHO HOÀN THÀNH", i.e. finish it). TASK-002 and TASK-004 are DONE; DEC-013 is ACCEPTED.
- BLK-001 resolved. The account has no local admin rights, so the winget/MSI install could not finish. Installed portable Temurin JDK 21.0.13.1+2 and Node.js v26.13.1 in `%LOCALAPPDATA%\Programs\`, and set `JAVA_HOME` and the user PATH (DEC-014, which supersedes DEC-009).
- Stopping the stuck winget/msiexec processes was refused by the auto-mode safety check. They are harmless; the owner can close the admin dialog.

**Evidence**
- SHA-256 checksums match the publishers' values. JDK: `bbebea14…a4ad4fbf2` (Adoptium `.sha256.txt`). Node: `e2edda75…a2474f1f7` (nodejs.org `SHASUMS256.txt`).
- In a shell with the new PATH: `java -version` → `openjdk 21.0.13.1 2026-11-17 LTS` (Temurin-21.0.13.1+2); `javac 21.0.13.1`; `node -v` → `v26.13.1`; `npm -v` → `11.20.0`.

**TASK-005 → DONE:** commit `b234db6` (43 files) pushed to `origin/chore/phase-1-foundation`, confirmed with `git ls-remote`. `main` is unchanged at `d776e5e`. Pull request link: <https://github.com/nhutruong47/FLOWER/pull/new/chore/phase-1-foundation>.

**Next:** Phase 2 (TASK-006) needs the owner's answers to B1–B10. The owner also needs to decide whether to merge into `main`.

## 2026-11-23: Phase 1, project governance scaffold (TASK-004 → CODE_REVIEW)

**Done**
- Checked that nothing existed before writing. No `CLAUDE.md`, `.claude/` or `project-management/` existed. `docs/requirements/` and `docs/adr/` already existed and were kept.
- Created `CLAUDE.md`: overview, scope, the 12 baseline business rules (REQ-001 to REQ-012), stack, architecture, proposed conventions, security, testing, Git, documentation, and rules for existing code.
- Created 8 agents in `.claude/agents/`. The security-reviewer is read-only (`Read, Grep, Glob`). Each agent preloads its matching skill.
- Created 8 skills in `.claude/skills/`. `project-orchestration` enforces dependencies, phase gates and DONE-with-evidence.
- Created `.claude/settings.json` (conservative permissions plus a secret-guard hook) and `.claude/hooks/` (script, self-test, README).
- Created `project-management/` (roadmap, tasks, progress, decisions, blockers, bugs, current state) and the `docs/` discipline folders, plus the REQ and TEST registers.
- Updated `README.md` (status line, governance links) and `docs/requirements/open-questions.md` (B2–B5 linked to REQs; added B9, B10, T11).

**Evidence**
- Claude Code version: `claude.exe --version` → `2.1.330 (Claude Code)`. Organization-managed settings are empty (`{}`).
- Configuration syntax checked against the official docs (permissions, settings, hooks, sub-agents, skills, memory pages on code.claude.com).
- Hook self-test: `bash .claude/hooks/scripts/test-protect-secrets.sh` → 27 passed, 0 failed. The first run found a real bug (a second `.env` mention in the same command was missed); it was fixed and re-run.
- Live checks in this session: a Read of `.env.qa` was blocked by the hook; a Read under `~/.ssh/` was blocked by the deny rules.
- Validation script (JSON, Markdown fences, relative links and anchors, frontmatter, LF endings, `bash -n`): results in the Phase 1 report.

**Next:** owner review of Phase 1, then TASK-005 (commit and push) and TASK-006 (business analysis).

## 2026-11-23: Toolchain install (TASK-003 → IN_PROGRESS, blocked)

- Started `winget install` for Temurin JDK 21.0.13 and Node.js LTS 26.13.1. The JDK download and hash check succeeded.
- The install has been waiting for Windows admin (UAC) approval since 11:39. Logged as **BLK-001**.

## 2026-11-23: Phase 1, repository foundation (TASK-002 → CODE_REVIEW)

- Branch `chore/phase-1-foundation` created. Repo-local setting `core.longpaths=true`.
- Added `.gitignore`, `.gitattributes` and `.editorconfig`, extended `README.md`, added ADR-0001 and `open-questions.md`.
- Evidence: `git check-ignore -v` (`.env` ignored, `.env.example` not ignored); `git check-attr eol` (LF, and CRLF for `.cmd`); no CR characters in the new files.
- Owner decisions recorded: Maven, two frontend apps, npm, install JDK and Node only (DEC-003, DEC-005, DEC-009).

## 2026-11-23: Phase 0, workspace inspection (TASK-001 → DONE)

- Repository initialized and the first commit `d776e5e` pushed to `github.com/nhutruong47/FLOWER` (`main`).
- Inspection report delivered: the workspace was empty and no toolchain was installed. The owner reviewed it.
