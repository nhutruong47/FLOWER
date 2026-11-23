# Blockers

- **Last updated:** 2026-11-23
- **Statuses:** `OPEN` · `RESOLVED` (record how and when)
- **Rules:** a blocked task keeps its current status and lists the BLK ID in its notes. Every blocker has an owner who can unblock it.

| ID | Blocker | Blocks | Owner | Raised | Status |
|---|---|---|---|---|---|
| BLK-001 | The JDK 21 install (winget) is waiting for Windows admin (UAC) approval. The Node.js LTS install is queued behind it. | TASK-003; Phases 7–9 locally | Project owner | 2026-11-23 11:39 | RESOLVED 2026-11-23 |
| BLK-002 | Phase 2 needs owner answers: B1, B4–B8, B10, and the follow-up questions raised by the first answers (see [open-questions.md](../docs/requirements/open-questions.md)). | TASK-006 completion; Phase 3 gate | Project owner | 2026-11-23 | OPEN |

## BLK-001: resolution (2026-11-23)

- **Root cause:** the Windows account is not in the local Administrators group, so the MSI installers could only continue with an administrator's credentials.
- **Resolution:** installed portable builds in the user profile instead (no admin): Temurin JDK 21.0.13.1+2 and Node.js v26.13.1, both checked against the publishers' SHA-256 checksums. `JAVA_HOME` and the user PATH are set. Recorded as DEC-014.
- **Leftover:** the original winget/MSI request may still show a Windows admin dialog. Click **No** to close it. Stopping those processes automatically was not permitted.

## BLK-001: original details

- **Seen:** the installer has been waiting on the Windows admin (UAC) prompt since 11:39. No JDK or Node.js folder exists yet.
- **Ways to unblock:**
  1. Approve the UAC prompts for the JDK and Node.js installers.
  2. If there are no admin rights, cancel the install and use the portable JDK 21 and Node.js ZIP builds in the user profile (no admin needed).
- **Impact if it stays open:** nothing can be built locally. It does **not** block Phases 2–6, which are documents only.
