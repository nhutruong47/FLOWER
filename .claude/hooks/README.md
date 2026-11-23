# Claude Code hooks

- **Last updated:** 2026-11-23 · **Verified on:** Claude Code 2.1.330, Windows 10, Git Bash

Hooks are configured in [../settings.json](../settings.json), and the scripts live in [scripts/](scripts/). The rule is simple: **a hook is added only when its behaviour has been verified by actually running it.** A hook never claims a check it doesn't perform.

## Active hooks

| Hook | Event and matcher | What it does | Verification |
|---|---|---|---|
| [protect-secrets.sh](scripts/protect-secrets.sh) | `PreToolUse` on `Read\|Edit\|Write\|MultiEdit\|NotebookEdit\|Grep\|Glob\|Bash\|PowerShell` | **Blocks** file tools aimed at `.env`, `.env.*` (except `.env.example`, `.env.sample`, `.env.template`), `*.pem`, `*.key`, `*.p12` and `*.jks`. **Asks** the human before shell commands that mention a `.env` file. | `bash .claude/hooks/scripts/test-protect-secrets.sh`: 27/27 cases pass. Live check 2026-11-23: a Read of `.env.qa` was blocked with the hook's message. |

**Known limits of protect-secrets.sh** (by design, it's a second layer):
- Text matching can't catch every way of reading a file through a shell (variables, encodings, scripts). The first layer is `permissions.deny` in `settings.json`.
- It **fails open**. If `bash` is missing, the hook errors and the action continues under the normal permission rules, because a non-zero exit other than 2 doesn't block. Claude Code on Windows normally runs hooks through Git Bash.
- It **over-asks** rather than under-asks. A shell command whose description merely mentions `.env` will prompt the human.
- Glob (listing file names) is allowed. Only reading file contents is blocked.

## Hooks deliberately not created yet

| Planned hook | Why not yet | Add when |
|---|---|---|
| Format on edit (`PostToolUse`: Spotless for Java, Prettier for TS) | There is no code and no formatter configured; JDK and Node aren't installed (BLK-001). An unverified formatter hook could corrupt files or fail silently. | `backend/` and `frontend/` exist with formatter config, and the hook has been run on sample files |
| Test gate (`Stop` or `TaskCompleted`: run the affected tests) | There are no tests to run. A hook that "validates tests" without running them is forbidden. | The test commands exist and run in under the hook timeout. The hook must run the real command and report its real exit code |
| Migration guard (block edits to merged Flyway migrations) | No migrations exist yet | Phase 5, once the migration folder exists |

## Adding or changing a hook

1. Write the script in `scripts/` using only tools every developer has (bash, sed, grep).
2. Add self-test cases and run them. All must pass.
3. Wire it into `settings.json`, then check it live with a harmless probe.
4. Record it in this table, and in `project-management/DECISIONS.md` if it changes how the team works.
