# Database

- **Owner:** database-engineer · **Phase:** 5 (P06 Database Design) · **Status:** PROPOSED (complete P06 PostgreSQL 16 database design awaiting owner approval before P07).
- **Last updated:** 2026-11-24

This folder holds the comprehensive P06 Database Design deliverables for FlowerChain:

1. **[erd.md](erd.md):**
   - Evaluation of all 20 candidate entity areas against approved requirements (`REQ-001`..`017`, `FR-001`..`038`) and module ownership (`docs/architecture/module-map.md`).
   - Complete Mermaid Entity-Relationship Diagrams (ERDs) covering all 24 core MVP tables across `shared`, `audit`, `branch`, `identity`, `customer`, `catalog`, `inventory`, `order`, `delivery`, `payment`, and `notification`.
2. **[data-dictionary-and-constraints.md](data-dictionary-and-constraints.md):**
   - Global PostgreSQL 16 standards (`snake_case` plural tables, `UUID` PKs, UTC `timestamptz`, `numeric(19,2)` money, `ON DELETE RESTRICT` soft-delete/lifecycle status policy, 3NF + controlled snapshot denormalization for `REQ-006`).
   - Full column-by-column data dictionary, primary/foreign keys, `UNIQUE` constraints, and `CHECK` constraints (`branch_id NOT NULL`, `available_stock >= 0`, `signature_verified = true`, `UNIQUE (provider, provider_event_id)`).
   - Index design & rationale (`16` indexes supporting branch-filtered queries and foreign keys).
   - Concurrency, deterministic lock ordering (`SELECT ... FOR UPDATE`), two-phase stock reservation (`available_stock` / `reserved_stock`), overselling prevention (`REQ-007`), and idempotency analysis (`REQ-010`).
3. **[migration-plan.md](migration-plan.md):**
   - Flyway migration rules (`V<YYYYMMDDHHmm>__<short_description>.sql`, `ddl-auto=validate`, immutable merged migrations).
   - Sequenced 8-migration execution plan (`V202611240100` to `V202611240800`, plus optional `V202611240900` for vouchers) and Testcontainers PostgreSQL 16 verification plan (`DEC-015`).

Executable SQL migrations will be created in `backend/src/main/resources/db/migration/` in Phase 7 after owner approval.
