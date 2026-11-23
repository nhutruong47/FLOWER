---
name: business-analysis
description: Repeatable workflow for capturing FlowerChain business processes and turning owner input into traceable REQ entries with testable acceptance criteria and explicit open questions. Use for Phase 2 (business analysis) and Phase 3 (requirements approval) work, or whenever a new business need appears.
---

# Business analysis

## Required input
- The owner's answers, interview notes or business documents (the sources)
- `docs/requirements/REQUIREMENTS.md`, `docs/requirements/open-questions.md` and the baseline rules in `CLAUDE.md` §2
- The task ID and its acceptance criteria

## Workflow
1. **Collect sources.** List each source with its date and author. Use nothing without a source.
2. **Find the gaps.** For the process in scope, list the actors, triggers, steps, decisions, exceptions and data involved. Every gap becomes a question (B-number) in `open-questions.md`.
3. **Ask in batches.** Group questions by topic. Offer concrete options where possible, but never pick an answer for the owner.
4. **Describe the process.** Write `docs/business-analysis/processes/<name>.md` with numbered steps and exceptions. Label each statement CONFIRMED, INFERRED, NEEDS CONFIRMATION or NOT FOUND.
5. **Write requirements.** One rule or capability per REQ entry: next free ID, source, status `DRAFT` (or `NEEDS_CONFIRMATION` if it is conditional).
6. **Write acceptance criteria** in Given / When / Then form. Each one must be testable without interpretation.
7. **Check consistency.** Compare against REQ-001 to REQ-012 and existing REQs. Flag conflicts and duplicates.
8. **Prepare for approval** (Phase 3). Produce a list of DRAFT REQs for the owner to sign off. Only the owner sets `APPROVED`.

## Output format

REQ row in `REQUIREMENTS.md`:

```markdown
| REQ-0xx | <one-sentence requirement> | Business rule / Feature / Security / Non-functional | <source, date> | DRAFT | <related Qs> |
```

Acceptance criteria section in the process or requirement file:

```markdown
#### REQ-0xx acceptance criteria
- AC1: Given <context>, when <action>, then <observable result>.
```

## Quality checks
- Every REQ has a source, a status, and at least one testable acceptance criterion before it goes to the owner for approval.
- No statement without a source is written as fact. Inferences are labelled INFERRED.
- No REQ contradicts a baseline rule without an explicit flag.
- IDs are unique and never reused.

## Stop conditions
- The answer needed isn't in any source: add a question and wait.
- Sources contradict each other: record both and escalate.
- You are asked to approve requirements yourself: refuse, because approval is the owner's.
- You would have to choose a business number, rule or workflow: ask instead.
