# Database

- **Owner:** database-engineer · **Phase:** 5 · **Status:** empty.

This folder will hold:

- `erd.md`: the entity-relationship diagram, split by module.
- `tables/`: one spec per table (columns, constraints, indexes, the module that owns it, the REQs it supports).
- `migration-plan.md`: Flyway migration order and how existing data is handled.

Conventions are in [CLAUDE.md §6](../../CLAUDE.md#6-database-conventions-proposed-confirm-in-phase-5-dec-010). Migrations live in `backend/src/main/resources/db/migration/`, never here.
