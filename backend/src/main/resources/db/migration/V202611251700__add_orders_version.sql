-- V202611251700__add_orders_version.sql
-- Module: order (TASK-017 security review F1, REQ-010)
-- Optimistic-locking version for orders: a stale write now fails instead of silently overwriting a concurrent
-- payment confirmation or status change. Order mutators also lock the row (SELECT ... FOR UPDATE).

ALTER TABLE orders ADD COLUMN version bigint NOT NULL DEFAULT 0;
