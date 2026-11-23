---
name: architecture-design
description: Repeatable workflow for FlowerChain solution architecture - derive the modular-monolith module map from approved requirements, define module APIs and dependencies, design cross-cutting concerns (branch-level authorization, auditing, idempotency), and record DEC entries and ADRs. Use in Phase 4 or whenever a change affects module boundaries or conventions.
---

# Architecture design

## Required input
- Approved REQs (`docs/requirements/REQUIREMENTS.md`), the baseline rules REQ-001 to REQ-012, and `CLAUDE.md`
- Existing ADRs (`docs/adr/`) and `project-management/DECISIONS.md`
- Open questions that affect the design

## Workflow
1. **Group requirements into modules.** Cluster approved REQs by business capability. Each cluster that owns its own data and rules is a module candidate.
2. **Assign ownership.** Every REQ gets exactly one owning module, and every table and rule has exactly one owner.
3. **Define each module's public API.** List the operations other modules may call (`<module>.api`) and the events it publishes. Everything else is internal.
4. **Check dependencies.** Draw the module dependency graph (Mermaid). It must have **no cycles**. Break cycles with events or by moving responsibilities.
5. **Design the cross-cutting concerns:**
   - branch-level authorization: where branch scope is resolved, how queries are filtered, how a client `branchId` is checked (REQ-011, REQ-012);
   - audit trail (REQ-008);
   - idempotency for orders and payments (REQ-010);
   - webhook verification (REQ-009);
   - error model and transaction boundaries.
6. **Confirm the conventions.** Review `CLAUDE.md` §5–7 (DEC-010). Propose changes as DEC entries.
7. **Record decisions.** One DEC entry per decision (status `PROPOSED`). Write an ADR for anything that is costly to reverse.
8. **Get a review.** Ask the security-reviewer to check step 5, then present the design to the owner for approval.
9. **Break the work into tasks.** List the tasks per module, with dependencies, for the orchestrator.

## Output format

`docs/architecture/module-map.md`:

```markdown
| Module | Responsibility | Owns data | Public API / events | Depends on | REQs |
```

ADR (`docs/adr/NNNN-title.md`): Status, Date, Context, Options considered, Decision, Consequences.

## Quality checks
- 100% of approved REQs are assigned to a module, and no module exists without a REQ.
- The dependency graph has no cycles, and no cross-module access to entities or repositories is planned.
- Branch scoping, auditing, idempotency and webhook verification each have an explicit design.
- Every new technology has a DEC entry. Nothing was decided silently.

## Stop conditions
- A requirement is too ambiguous to place in a module, or two requirements conflict.
- A decision needs owner input on cost, licensing, hosting (T6, T12) or business rules.
- The design can't satisfy a baseline rule.
