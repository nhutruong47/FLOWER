-- V202611240800__create_notifications_table.sql
-- Module: notification (FR-033)

CREATE TABLE notifications (
    id uuid PRIMARY KEY,
    recipient_type varchar(16) NOT NULL,
    branch_id uuid REFERENCES branches(id) ON DELETE RESTRICT,
    customer_id uuid REFERENCES customers(id) ON DELETE RESTRICT,
    order_id uuid REFERENCES orders(id) ON DELETE RESTRICT,
    event_type varchar(64) NOT NULL,
    title varchar(200) NOT NULL,
    message varchar(500) NOT NULL,
    is_read boolean NOT NULL DEFAULT false,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT chk_notifications_recipient_type CHECK (recipient_type IN ('BRANCH_STAFF', 'CUSTOMER'))
);
