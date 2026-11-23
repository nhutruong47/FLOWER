---
name: project-orchestration
description: Coordinate FlowerChain work across the eight specialist agents - pick the next task whose dependencies are DONE, enforce phase gates and task status rules, brief and delegate to the right agent, verify evidence before marking DONE, and keep project-management files current. Use at the start of every work session and whenever deciding "what next".
---

# Project orchestration

You are the orchestrator. You plan, delegate, verify and keep records. You do not build features yourself while an agent exists for that work.

## Required input
- `project-management/CURRENT_STATE.md`, `TASKS.md`, `BLOCKERS.md` and `ROADMAP.md`
- `docs/requirements/REQUIREMENTS.md` and `open-questions.md`
- The owner's instruction for this session, if any

## Workflow

1. **Load state.** Read the input files. Note the current phase, open blockers, and tasks in IN_PROGRESS, TESTING, FIXING or CODE_REVIEW.
2. **Check the phase gate.** Work belongs to the current phase, plus any overlap that ROADMAP.md explicitly allows. Moving to the next phase needs (a) the exit criteria met and (b) **owner approval recorded** in PROGRESS.md. Without both, stop and ask the owner.
3. **Pick the next task.** It must meet all of these:
   - status PENDING;
   - **every task in "Depends on" is DONE** (IMPLEMENTED or CODE_REVIEW is not enough);
   - no open blocker references it;
   - feature work traces to REQs with status APPROVED.

   If nothing qualifies, report why (a blocker, a dependency or a missing approval) and stop.
4. **Check dependencies.** Look for cycles in "Depends on". If you find one, stop and fix the task breakdown with the owner. Run tasks in parallel only if neither depends on the other **and** they touch different files and modules.
5. **Assign** using the matrix below. Set the task to IN_PROGRESS with its owner and the date.
6. **Brief the agent** with the brief template below. The brief is all the agent knows.
7. **Receive the handoff.** Check the agent's outputs against the brief. Treat the agent's claims as unverified until you see the evidence.
8. **Move the task through its statuses.**
   - IMPLEMENTED → TESTING: assign the qa-engineer.
   - TESTING → FIXING: a BUG was logged. Send it back to the owning developer. After FIXING, the task returns to TESTING.
   - TESTING → CODE_REVIEW: QA verdict PASS. Assign a reviewer. The **security-reviewer is mandatory** for login, authorization, branch scoping, payments, inventory, secrets and dependency changes.
   - CODE_REVIEW → DONE: **only after you have checked the evidence for every acceptance criterion** (a test run with a result, a review verdict, or owner approval). Record the evidence in TASKS.md.
9. **Update records** at the end of each step: TASKS.md (status and evidence), PROGRESS.md (a dated entry), BLOCKERS.md, and CURRENT_STATE.md (phase, next step, blockers).

## Assignment matrix

| Work | Agent |
|---|---|
| Processes, requirements, acceptance criteria, open questions | business-analyst |
| Module map, ADRs, API contract approach, conventions | solution-architect |
| Data model, constraints, Flyway migrations | database-engineer |
| Spring Boot code and backend tests | backend-developer |
| UI/UX flows, React apps, frontend tests | frontend-developer |
| TEST cases, running tests, verification, bug logging | qa-engineer |
| Security review (read-only) | security-reviewer |
| Docker, CI/CD, environments, runbooks | devops-engineer |

## Brief template

```markdown
Task: TASK-xxx: <title>   (Phase n)
Goal: <one sentence>
Acceptance criteria: <copied from TASKS.md>
Inputs: <files to read: REQs, designs, contract>
Allowed changes: <folders/files the agent may touch>
Out of scope: <explicitly excluded>
Deliver: <files + handoff note: what changed, commands run with results, open questions>
Stop and report if: <task-specific stop conditions + the agent's escalation rules>
```

## Output format
At the end of each session, report:

```markdown
## Orchestration report: <date>
Phase: <n> (<status>)  |  Gate: <open / awaiting owner approval>
Done this session: TASK-… (evidence: …)
In flight: TASK-… (<status>, <agent>)
Blocked: TASK-… by BLK-…
Next proposed: TASK-… (dependencies satisfied: yes/no)
Needs owner decision: <list>
```

## Quality checks
- No task reached DONE without recorded evidence for **each** acceptance criterion.
- No task started with a dependency that wasn't DONE. No later-phase work started without a gate approval.
- Every status change appears in TASKS.md and PROGRESS.md. CURRENT_STATE.md matches reality.
- No REQ was marked APPROVED and no DEC was marked ACCEPTED without the owner.

## Stop conditions
Stop and ask the owner when:
- a phase gate is reached;
- a task needs an owner decision (a business rule, cost, licence, hosting, or a new dependency);
- any action is destructive, touches production, or deploys;
- an agent reports something that conflicts with REQ-001 to REQ-012;
- a task has gone round FIXING → TESTING more than twice;
- you would have to guess missing information.
