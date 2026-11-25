---
name: requirements-engineering
description: Repeatable workflow for turning FlowerChain business analysis findings into structured, unambiguous requirements (REQ-xxx) and business rules (BRULE-xxx) with testable Given/When/Then acceptance criteria, traceability, and Phase 3 approval packages. Use when authoring, refining, validating, or preparing requirements for owner sign-off.
---

# Requirements engineering

## Required input
- Business process descriptions and glossary in `docs/business-analysis/`
- Owner answers and open questions in `docs/requirements/open-questions.md`
- `docs/requirements/REQUIREMENTS.md` and the baseline guardrails in `CLAUDE.md` §2
- The task ID and its acceptance criteria in `project-management/TASKS.md`

## Workflow
1. **Verify traceability to sources.** Every requirement (`REQ-xxx`) and business rule (`BRULE-xxx`) must cite a confirmed owner statement, brief, or approved document. If a rule is missing a source, log a question in `open-questions.md` and do not invent a requirement.
2. **Classify the entry.**
   - **Business rule (`BRULE-xxx` / `REQ-xxx` type `Business rule`):** an invariant that must hold across all workflows (e.g., non-negative stock, branch data isolation, price snapshots).
   - **Functional requirement (`Feature`):** an observable capability for an actor (customer, branch staff, head office).
   - **Security / Non-functional requirement (`Security` / `Non-functional`):** authorization, webhook verification, idempotency, auditability, or performance constraints.
3. **Ensure atomicity.** Write one rule or capability per ID. Split compound statements so each ID can be approved, implemented, and tested independently.
4. **Write testable acceptance criteria.** Use Given / When / Then format:
   - Include the normal (happy) path.
   - Include boundary, negative, concurrency, and authorization failure paths (especially cross-branch denial and non-negative inventory).
   - If a criterion depends on an open question `Bx`, mark it explicitly as `AC pending: Bx`.
5. **Cross-check for conflicts.** Check every entry against the baseline rules (`REQ-001` to `REQ-012`) and existing requirements. If two statements conflict (such as automatic branch selection vs. customer-selected branch pricing), set the status to `NEEDS_CONFIRMATION` and link the blocking question.
6. **Map verification readiness.** Ensure each acceptance criterion can be translated directly into a `TEST-xxx` case (UNIT, INTEGRATION, or E2E) by the `qa-engineer`.
7. **Package for Phase 3 approval.** Present the register of `DRAFT` requirements grouped by domain area, highlighting any open dependencies or deferred items. Only the project owner may change a requirement's status to `APPROVED`.

## Output format

Requirement entry in `docs/requirements/REQUIREMENTS.md`:

```markdown
| REQ-0xx | <atomic requirement statement> | Business rule / Feature / Security / Non-functional | <source, date> | DRAFT | <related BRULE / open questions> |
```

Acceptance criteria block:

```markdown
#### REQ-0xx acceptance criteria
- AC1: Given <precondition>, when <actor/system action>, then <verifiable outcome>.
- AC2 (negative / scope): Given <unauthorized branch or invalid state>, when <action is attempted>, then <request is rejected and state is unchanged>.
```

## Quality checks
- Every `REQ-xxx` / `BRULE-xxx` has a unique ID (never reused), explicit source, current status (`DRAFT`, `NEEDS_CONFIRMATION`, `APPROVED`, `REJECTED`, `SUPERSEDED`), and testable acceptance criteria.
- No vague terms ("fast", "user-friendly", "appropriate", "etc.") appear in requirements or acceptance criteria.
- Every branch-owned requirement states how branch isolation (`REQ-011`, `REQ-012`) applies.
- No requirement is marked `APPROVED` without explicit owner sign-off.

## Stop conditions
- A requirement requires guessing a business policy, threshold, role permission, or provider: stop, record the question in `open-questions.md`, and escalate.
- A requirement contradicts `REQ-001` through `REQ-012`: stop and escalate to the owner.
- You are asked to self-approve requirements without owner review: refuse and escalate.
