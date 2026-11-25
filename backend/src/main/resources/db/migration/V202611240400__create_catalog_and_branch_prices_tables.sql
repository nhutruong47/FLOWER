-- V202611240400__create_catalog_and_branch_prices_tables.sql
-- Module: catalog (REQ-003, REQ-004, REQ-017, FR-010..014)

CREATE TABLE product_categories (
    id uuid PRIMARY KEY,
    slug varchar(100) NOT NULL,
    name varchar(150) NOT NULL,
    description varchar(500),
    display_order integer NOT NULL DEFAULT 0,
    status varchar(16) NOT NULL DEFAULT 'ACTIVE',
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT uq_product_categories_slug UNIQUE (slug),
    CONSTRAINT chk_product_categories_status CHECK (status IN ('ACTIVE', 'ARCHIVED'))
);

CREATE TABLE products (
    id uuid PRIMARY KEY,
    category_id uuid NOT NULL REFERENCES product_categories(id) ON DELETE RESTRICT,
    sku varchar(64) NOT NULL,
    name varchar(200) NOT NULL,
    description text,
    image_url varchar(500),
    unit_label varchar(32) NOT NULL DEFAULT 'BOUQUET',
    status varchar(16) NOT NULL DEFAULT 'ACTIVE',
    available_from timestamptz,
    available_until timestamptz,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT uq_products_sku UNIQUE (sku),
    CONSTRAINT chk_products_status CHECK (status IN ('ACTIVE', 'ARCHIVED')),
    CONSTRAINT chk_products_seasonal_window CHECK (available_until IS NULL OR available_from IS NULL OR available_until > available_from)
);

CREATE INDEX idx_products_category_status ON products (category_id, status);

CREATE TABLE branch_product_prices (
    id uuid PRIMARY KEY,
    branch_id uuid NOT NULL REFERENCES branches(id) ON DELETE RESTRICT,
    product_id uuid NOT NULL REFERENCES products(id) ON DELETE RESTRICT,
    unit_price numeric(19, 2) NOT NULL,
    currency_code varchar(3) NOT NULL DEFAULT 'VND',
    is_sellable boolean NOT NULL DEFAULT true,
    updated_by_staff_id uuid REFERENCES staff_users(id) ON DELETE RESTRICT,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT uq_branch_product_price UNIQUE (branch_id, product_id),
    CONSTRAINT chk_branch_prices_non_negative CHECK (unit_price >= 0)
);

CREATE INDEX idx_branch_prices_branch_sellable ON branch_product_prices (branch_id, is_sellable, product_id);
