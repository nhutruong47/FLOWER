-- V202611240100__create_shared_and_branch_tables.sql
-- Modules: shared, branch (REQ-001, REQ-014, FR-007..009, FR-037)

CREATE TABLE idempotency_records (
    id uuid PRIMARY KEY,
    idempotency_key varchar(128) NOT NULL,
    actor_scope varchar(128) NOT NULL,
    endpoint_pattern varchar(128) NOT NULL,
    request_hash varchar(64) NOT NULL,
    response_status integer NOT NULL,
    response_body text NOT NULL,
    expires_at timestamptz NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT uq_idempotency_key_scope UNIQUE (idempotency_key, actor_scope, endpoint_pattern)
);

CREATE TABLE branches (
    id uuid PRIMARY KEY,
    code varchar(32) NOT NULL,
    name varchar(150) NOT NULL,
    street_address varchar(255) NOT NULL,
    district varchar(100) NOT NULL,
    city varchar(100) NOT NULL,
    phone varchar(32) NOT NULL,
    latitude numeric(9, 6) NOT NULL,
    longitude numeric(9, 6) NOT NULL,
    max_delivery_radius_km numeric(6, 2) NOT NULL DEFAULT 15.00,
    operating_hours varchar(128) NOT NULL,
    status varchar(16) NOT NULL DEFAULT 'ACTIVE',
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT uq_branches_code UNIQUE (code),
    CONSTRAINT chk_branches_latitude CHECK (latitude BETWEEN -90 AND 90),
    CONSTRAINT chk_branches_longitude CHECK (longitude BETWEEN -180 AND 180),
    CONSTRAINT chk_branches_radius CHECK (max_delivery_radius_km > 0),
    CONSTRAINT chk_branches_status CHECK (status IN ('ACTIVE', 'INACTIVE'))
);

CREATE TABLE system_settings (
    id uuid PRIMARY KEY,
    setting_key varchar(64) NOT NULL,
    setting_value varchar(255) NOT NULL,
    description varchar(255),
    updated_by_staff_id uuid,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT uq_system_settings_key UNIQUE (setting_key)
);
