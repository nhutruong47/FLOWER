---
name: testing
description: Repeatable workflow for FlowerChain verification - derive TEST cases from acceptance criteria, implement them at the right level (JUnit 5/Mockito, Testcontainers, Playwright), actually run them, record evidence, give a PASS/FAIL verdict, and log BUG entries. Use when a task is in TESTING or when writing test cases in Phase 3.
---

# Testing

## Required input
- The task ID, its acceptance criteria and REQs
- The code under test, and the test commands in `CLAUDE.md` §3
- `docs/testing/TEST_CASES.md` and `project-management/BUGS.md`

## Workflow
1. **Turn each acceptance criterion into TEST cases** (next free TEST ID). Include the failure paths, not only the happy path.
2. **Choose the level for each case:**
   - UNIT: domain and service logic, with mocked collaborators;
   - INTEGRATION: repositories, migrations, security filters, concurrency, against real PostgreSQL via Testcontainers;
   - E2E: user journeys across the UI and API, with Playwright.
3. **Always include the baseline scenarios** that apply (`CLAUDE.md` §9): concurrent stock, audit records, snapshots, forged and duplicate webhooks, cross-branch access.
4. **Implement the tests** in the module's test folder. Tests must be deterministic: no sleeps for timing, fixed clocks, isolated data.
5. **Run them.** Execute the real commands and capture the summary (passed, failed, skipped).
6. **Give a verdict.**
   - All criteria pass: **PASS**, and the task moves to CODE_REVIEW.
   - Anything fails: log a BUG with steps to reproduce; the verdict is **FAIL** and the task moves to FIXING.
7. **Record the evidence.** Update TEST statuses (with command, date and result) and the task notes.

## Output format

```markdown
## Verification: TASK-xxx (<date>)
Command(s): <exact command>  →  <passed>/<failed>/<skipped>
| AC | TEST | Level | Result |
| AC1 | TEST-0xx | INTEGRATION | PASS |
Verdict: PASS | FAIL (BUG-0xx, …)
```

## Quality checks
- Every acceptance criterion maps to at least one TEST that was **run in this session**.
- No skipped or disabled tests unless the owner approved it, with the reason recorded.
- Integration tests use Testcontainers PostgreSQL, never H2 or mocks of the database.
- Never change production code to make a test pass. Defects become BUG entries.

## Stop conditions
- An acceptance criterion can't be tested as written: send it back to the business-analyst.
- The test environment is missing (JDK, Node, Docker; see BLK-001 and T4). Report it; never mark PASS.
- A test is flaky and you can't find out why. Escalate instead of retrying until it passes.
