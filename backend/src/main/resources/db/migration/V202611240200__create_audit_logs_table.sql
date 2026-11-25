-- V202611240200__create_audit_logs_table.sql
-- Module: audit (CLAUDE.md §8, FR-036)

CREATE TABLE audit_logs (
    id uuid PRIMARY KEY,
    category varchar(32) NOT NULL,
    action varchar(64) NOT NULL,
    branch_id uuid REFERENCES branches(id) ON DELETE RESTRICT,
    target_entity_type varchar(64) NOT NULL,
    target_entity_id uuid,
    actor_id uuid,
    actor_type varchar(32) NOT NULL,
    old_values text,
    new_values text,
    ip_address varchar(64),
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT chk_audit_logs_category CHECK (category IN ('LOGIN', 'ROLE_CHANGE', 'PRICE_CHANGE', 'ORDER_STATUS', 'REFUND', 'SETTING')),
    CONSTRAINT chk_audit_logs_actor_type CHECK (actor_type IN ('STAFF', 'CUSTOMER', 'SYSTEM', 'WEBHOOK'))
);

CREATE INDEX idx_audit_logs_branch_cat_created ON audit_logs (branch_id, category, created_at DESC);
CREATE INDEX idx_audit_logs_cat_created ON audit_logs (category, created_at DESC);
