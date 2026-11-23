---
name: frontend-development
description: Repeatable workflow for FlowerChain frontend work - UI/UX flows and wireframes (Phase 6) and implementing approved tasks in the storefront or admin React/TypeScript/Vite/Tailwind apps with tests, using only the agreed API contract. Use for any change under frontend/ or docs/ui-ux/.
---

# Frontend development

## Required input
- The task ID and acceptance criteria. Its dependencies must be DONE and its REQs APPROVED.
- UI/UX designs (`docs/ui-ux/`) and the API contract (`docs/api/`)
- `CLAUDE.md` §4, §5, §8 and §9

## Workflow

**A. Design (Phase 6)**
1. For each approved REQ in scope, write the user flow (start, steps, errors, end) in `docs/ui-ux/<app>/`.
2. Make low-fidelity wireframes (Markdown, Mermaid or images) for every screen. Link each screen to its REQs.
3. Define or extend the design tokens (colour, type, spacing) mapped to Tailwind. Use brand inputs only if provided (B8).
4. Present the flows to the owner for approval.

**B. Build (Phases 8–9)**
1. **Check readiness.** Dependencies are DONE, the design is approved, the contract exists, and the toolchain works (`node -v`, `npm -v`).
2. **Branch.** Create `feat/TASK-xxx-<short-name>`.
3. **Write tests first** where practical: component tests for behaviour, and Playwright for journeys named in the acceptance criteria.
4. **Implement in the right app** (storefront or admin). Shared code goes only in `packages/shared`. API calls go only through the shared client.
5. **Handle every state:** loading, empty, error (Problem Details), and unauthorized (redirect or message). Never rely on the UI for authorization.
6. **Run the real commands** listed in `CLAUDE.md` §3 (build, lint, tests). Everything must pass.
7. **Record and hand off.** Write the commands and results into the task notes and set the task to IMPLEMENTED.

## Output format

Handoff note:

```markdown
TASK-xxx → IMPLEMENTED
Changed: <files>   App(s): storefront | admin | shared
Build/tests: <command> → <result> (<date>)
Acceptance criteria → evidence: AC1 → <test or recorded manual check>, …
Contract changes needed: <none | list for solution-architect>
```

## Quality checks
- No imports between the storefront and admin apps. No `any` without a reason. No `dangerouslySetInnerHTML` with server or user data.
- No secrets in client code; only safe `VITE_` variables.
- Labelled form controls, keyboard access, semantic elements.
- Every acceptance criterion has evidence from a run in this session.

## Stop conditions
- The design or the API contract is missing, or doesn't support the requirement.
- A branding, copy or language decision is needed (B7, B8).
- The toolchain is unavailable (BLK-001). Report it; never claim the build passes.
- Implementing would mean enforcing a business rule or permission only in the browser.
