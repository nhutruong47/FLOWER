-- V202611240300__create_identity_and_customer_tables.sql
-- Modules: identity, customer (REQ-011, REQ-012, FR-001..006, FR-031)

CREATE TABLE staff_users (
    id uuid PRIMARY KEY,
    email varchar(255) NOT NULL,
    password_hash varchar(255) NOT NULL,
    full_name varchar(150) NOT NULL,
    phone varchar(32),
    status varchar(16) NOT NULL DEFAULT 'ACTIVE',
    last_login_at timestamptz,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT uq_staff_users_email UNIQUE (email),
    CONSTRAINT chk_staff_users_status CHECK (status IN ('ACTIVE', 'DISABLED'))
);

CREATE TABLE staff_user_roles (
    id uuid PRIMARY KEY,
    staff_user_id uuid NOT NULL REFERENCES staff_users(id) ON DELETE RESTRICT,
    role_code varchar(32) NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT uq_staff_user_role UNIQUE (staff_user_id, role_code),
    CONSTRAINT chk_staff_user_role_code CHECK (role_code IN ('SYSTEM_OWNER', 'HEAD_OFFICE_ADMIN', 'BRANCH_MANAGER', 'SALES_STAFF', 'INVENTORY_STAFF', 'DELIVERY_STAFF'))
);

CREATE TABLE staff_branch_assignments (
    id uuid PRIMARY KEY,
    staff_user_id uuid NOT NULL REFERENCES staff_users(id) ON DELETE RESTRICT,
    branch_id uuid NOT NULL REFERENCES branches(id) ON DELETE RESTRICT,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT uq_staff_branch_assignment UNIQUE (staff_user_id, branch_id)
);

CREATE INDEX idx_staff_branch_assignments_user ON staff_branch_assignments (staff_user_id, branch_id);

CREATE TABLE staff_refresh_sessions (
    id uuid PRIMARY KEY,
    staff_user_id uuid NOT NULL REFERENCES staff_users(id) ON DELETE RESTRICT,
    token_hash varchar(64) NOT NULL,
    expires_at timestamptz NOT NULL,
    revoked_at timestamptz,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT uq_staff_refresh_token_hash UNIQUE (token_hash)
);

CREATE TABLE customers (
    id uuid PRIMARY KEY,
    email varchar(255) NOT NULL,
    password_hash varchar(255) NOT NULL,
    full_name varchar(150) NOT NULL,
    phone varchar(32) NOT NULL,
    status varchar(16) NOT NULL DEFAULT 'ACTIVE',
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT uq_customers_email UNIQUE (email),
    CONSTRAINT chk_customers_status CHECK (status IN ('ACTIVE', 'DISABLED'))
);

CREATE TABLE customer_addresses (
    id uuid PRIMARY KEY,
    customer_id uuid NOT NULL REFERENCES customers(id) ON DELETE RESTRICT,
    label varchar(64) NOT NULL,
    recipient_name varchar(150) NOT NULL,
    recipient_phone varchar(32) NOT NULL,
    street_address varchar(255) NOT NULL,
    district varchar(100) NOT NULL,
    city varchar(100) NOT NULL,
    latitude numeric(9, 6),
    longitude numeric(9, 6),
    is_default boolean NOT NULL DEFAULT false,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT chk_customer_addr_lat CHECK (latitude IS NULL OR latitude BETWEEN -90 AND 90),
    CONSTRAINT chk_customer_addr_lng CHECK (longitude IS NULL OR longitude BETWEEN -180 AND 180)
);
