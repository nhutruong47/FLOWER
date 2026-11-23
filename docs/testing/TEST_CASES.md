# Test cases register

- **Last updated:** 2026-11-23
- **Statuses:** `PLANNED` · `AUTOMATED` (test exists in the code) · `PASSING` · `FAILING` · `RETIRED`
- **Levels:** `UNIT` (JUnit 5, Mockito) · `INTEGRATION` (Testcontainers PostgreSQL) · `E2E` (Playwright) · `MANUAL` (only with a written reason)
- **Rules:** every test case links to at least one REQ. Status `PASSING` needs the command, date and result recorded in the Evidence column.

| ID | Verifies | Scenario | Level | Location in code | Status | Evidence |
|---|---|---|---|---|---|---|
| _(none yet; cases are written in Phase 3 from the approved acceptance criteria)_ | | | | | | |

## Required coverage for the baseline rules

Each of these needs at least one TEST case when its module is built:

| Requirement | Scenario that must be tested |
|---|---|
| REQ-007 | Concurrent orders for the last units of stock: exactly one succeeds and stock never goes below zero |
| REQ-008 | Every stock change writes an audit record (who, when, why, branch, quantity change, source) |
| REQ-006 | After a catalog price change, existing orders still show the original price and product data |
| REQ-009 | A webhook with an invalid or missing signature is rejected and has no side effects |
| REQ-010 | The same webhook event delivered twice is processed once |
| REQ-011, REQ-012 | A user from branch A cannot read or change branch B's data, even with a forged `branchId` |
