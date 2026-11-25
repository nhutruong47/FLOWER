-- V202611240700__create_payments_webhooks_and_refunds_tables.sql
-- Module: payment (REQ-009, REQ-010, FR-025..027)

CREATE TABLE payments (
    id uuid PRIMARY KEY,
    order_id uuid NOT NULL REFERENCES orders(id) ON DELETE RESTRICT,
    provider varchar(32) NOT NULL,
    payment_method varchar(32) NOT NULL,
    provider_transaction_id varchar(128),
    status varchar(24) NOT NULL,
    amount numeric(19, 2) NOT NULL,
    currency_code varchar(3) NOT NULL DEFAULT 'VND',
    captured_at timestamptz,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT uq_payments_provider_transaction_id UNIQUE (provider, provider_transaction_id),
    CONSTRAINT chk_payments_status CHECK (status IN ('INITIATED', 'CAPTURED', 'FAILED', 'REFUNDED')),
    CONSTRAINT chk_payments_amount_non_neg CHECK (amount >= 0)
);

CREATE INDEX idx_payments_order ON payments (order_id);

CREATE TABLE payment_webhook_events (
    id uuid PRIMARY KEY,
    provider varchar(32) NOT NULL,
    provider_event_id varchar(128) NOT NULL,
    provider_transaction_id varchar(128),
    order_id uuid REFERENCES orders(id) ON DELETE RESTRICT,
    signature_verified boolean NOT NULL,
    processing_outcome varchar(32) NOT NULL,
    received_at timestamptz NOT NULL DEFAULT now(),
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT uq_payment_webhook_provider_event_id UNIQUE (provider, provider_event_id),
    CONSTRAINT chk_webhook_signature_verified CHECK (signature_verified = true)
);

CREATE TABLE payment_refunds (
    id uuid PRIMARY KEY,
    payment_id uuid NOT NULL REFERENCES payments(id) ON DELETE RESTRICT,
    order_id uuid NOT NULL REFERENCES orders(id) ON DELETE RESTRICT,
    refund_amount numeric(19, 2) NOT NULL,
    reason varchar(500) NOT NULL,
    external_refund_ref varchar(128),
    approved_by_staff_id uuid NOT NULL REFERENCES staff_users(id) ON DELETE RESTRICT,
    refunded_at timestamptz NOT NULL DEFAULT now(),
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT chk_payment_refunds_amount_pos CHECK (refund_amount > 0)
);
