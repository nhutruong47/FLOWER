---
name: database-design
description: Repeatable workflow for FlowerChain PostgreSQL design - derive tables from approved requirements and the module map, encode baseline rules as constraints, write Flyway migrations, and verify them on a disposable Testcontainers database. Use in Phase 5 and for any schema change.
---

# Database design

## Required input
- Approved REQs, the module map (which module owns which data), and the access patterns
- The database conventions in `CLAUDE.md` §6 and the existing migrations

## Workflow
1. **List the entities** each approved REQ needs, with their owning module.
2. **Write table specs** in `docs/database/tables/<table>.md` using the format below.
3. **Map the baseline rules to constraints:**
   - `branch_id NOT NULL` with a foreign key on branch-owned data (REQ-002, REQ-005);
   - a `CHECK` that stock stays at or above zero (REQ-007);
   - an inventory audit table (REQ-008);
   - snapshot columns on order lines (REQ-006);
   - `UNIQUE` on provider event and transaction IDs (REQ-010).
4. **Plan indexes** for the known queries and for every foreign key. Choose row locks or optimistic locking (`version` column) for stock updates.
5. **Update the ERD** (`docs/database/erd.md`, Mermaid) and the migration plan.
6. **Write the migration** as `V<YYYYMMDDHHmm>__<description>.sql`, one logical change per file. Never edit a migration that has been merged.
7. **Verify on a disposable database.** A Testcontainers integration test applies all migrations from empty and checks the constraints by inserting invalid rows that must fail. Run it and record the result.

## Output format

Table spec:

```markdown
# <table_name>   (module: <module>, REQs: REQ-0xx)
| Column | Type | Null | Default | Constraint / FK | Notes |
Indexes: …   Check constraints: …   Unique: …
Write pattern / locking: …
```

## Quality checks
- Every table has an owning module, a primary key, `created_at`/`updated_at` (`timestamptz`), foreign keys, and the constraints required by the REQs.
- No floating-point money. Names are `snake_case` and table names plural.
- Migrations apply cleanly from empty, and the constraint tests were run in this session.
- No table exists for a feature that hasn't been approved.

## Stop conditions
- Data ownership or the meaning of data is unclear (e.g. pricing, B9).
- A change could lose or rewrite existing data. Get owner approval and a backup plan first.
- Docker is not available for Testcontainers (T4): verify in CI, or report a blocker. Never claim verification you didn't run.
- Any request to run SQL against a shared, staging or production database: refuse and escalate.
