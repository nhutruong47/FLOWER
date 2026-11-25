-- V202611240600__create_orders_and_deliveries_tables.sql
-- Modules: order, delivery (REQ-005, REQ-006, REQ-013, REQ-014, FR-021..024, FR-028..030)

CREATE TABLE orders (
    id uuid PRIMARY KEY,
    order_number varchar(64) NOT NULL,
    customer_id uuid NOT NULL REFERENCES customers(id) ON DELETE RESTRICT,
    processing_branch_id uuid NOT NULL REFERENCES branches(id) ON DELETE RESTRICT,
    fulfilment_type varchar(16) NOT NULL,
    status varchar(32) NOT NULL,
    payment_status varchar(24) NOT NULL,
    currency_code varchar(3) NOT NULL DEFAULT 'VND',
    items_subtotal numeric(19, 2) NOT NULL,
    delivery_fee numeric(19, 2) NOT NULL DEFAULT 0.00,
    discount_amount numeric(19, 2) NOT NULL DEFAULT 0.00,
    total_amount numeric(19, 2) NOT NULL,
    recipient_name varchar(150) NOT NULL,
    recipient_phone varchar(32) NOT NULL,
    delivery_street_address varchar(255),
    delivery_district varchar(100),
    delivery_city varchar(100),
    delivery_latitude numeric(9, 6),
    delivery_longitude numeric(9, 6),
    requested_slot_start timestamptz,
    requested_slot_end timestamptz,
    reservation_expires_at timestamptz,
    cancellation_reason varchar(500),
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT uq_orders_number UNIQUE (order_number),
    CONSTRAINT chk_orders_fulfilment_type CHECK (fulfilment_type IN ('DELIVERY', 'PICKUP')),
    CONSTRAINT chk_orders_status CHECK (status IN ('PENDING_PAYMENT', 'CONFIRMED', 'PREPARING', 'READY_FOR_DELIVERY', 'OUT_FOR_DELIVERY', 'READY_FOR_PICKUP', 'COMPLETED', 'CANCELLED')),
    CONSTRAINT chk_orders_payment_status CHECK (payment_status IN ('UNPAID', 'PAID', 'PARTIALLY_REFUNDED', 'REFUNDED')),
    CONSTRAINT chk_orders_subtotal_non_neg CHECK (items_subtotal >= 0),
    CONSTRAINT chk_orders_deliv_fee_non_neg CHECK (delivery_fee >= 0),
    CONSTRAINT chk_orders_discount_non_neg CHECK (discount_amount >= 0),
    CONSTRAINT chk_orders_total_non_neg CHECK (total_amount >= 0),
    CONSTRAINT chk_orders_delivery_address_required CHECK (fulfilment_type = 'PICKUP' OR (delivery_street_address IS NOT NULL AND delivery_district IS NOT NULL AND delivery_city IS NOT NULL))
);

CREATE INDEX idx_orders_branch_status_created ON orders (processing_branch_id, status, created_at DESC);
CREATE INDEX idx_orders_customer_created ON orders (customer_id, created_at DESC);
CREATE INDEX idx_orders_reservation_expiry ON orders (status, reservation_expires_at) WHERE status = 'PENDING_PAYMENT';

CREATE TABLE order_lines (
    id uuid PRIMARY KEY,
    order_id uuid NOT NULL REFERENCES orders(id) ON DELETE RESTRICT,
    product_id uuid NOT NULL REFERENCES products(id) ON DELETE RESTRICT,
    sku_snapshot varchar(64) NOT NULL,
    product_name_snapshot varchar(200) NOT NULL,
    unit_price_snapshot numeric(19, 2) NOT NULL,
    quantity integer NOT NULL,
    line_total_snapshot numeric(19, 2) NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT uq_order_lines_order_product UNIQUE (order_id, product_id),
    CONSTRAINT chk_order_lines_price_non_neg CHECK (unit_price_snapshot >= 0),
    CONSTRAINT chk_order_lines_qty_pos CHECK (quantity > 0),
    CONSTRAINT chk_order_lines_total_match CHECK (line_total_snapshot = unit_price_snapshot * quantity)
);

CREATE INDEX idx_order_lines_order ON order_lines (order_id);

CREATE TABLE order_status_history (
    id uuid PRIMARY KEY,
    order_id uuid NOT NULL REFERENCES orders(id) ON DELETE RESTRICT,
    from_status varchar(32),
    to_status varchar(32) NOT NULL,
    previous_branch_id uuid REFERENCES branches(id) ON DELETE RESTRICT,
    new_branch_id uuid REFERENCES branches(id) ON DELETE RESTRICT,
    actor_id uuid,
    actor_type varchar(16) NOT NULL,
    reason_note varchar(500),
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT chk_order_history_actor_type CHECK (actor_type IN ('STAFF', 'CUSTOMER', 'SYSTEM', 'WEBHOOK'))
);

CREATE TABLE order_returns (
    id uuid PRIMARY KEY,
    order_id uuid NOT NULL REFERENCES orders(id) ON DELETE RESTRICT,
    branch_id uuid NOT NULL REFERENCES branches(id) ON DELETE RESTRICT,
    status varchar(32) NOT NULL,
    customer_reason varchar(500) NOT NULL,
    resolution_note varchar(500),
    restocked boolean NOT NULL DEFAULT false,
    reviewed_by_staff_id uuid REFERENCES staff_users(id) ON DELETE RESTRICT,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT chk_order_returns_status CHECK (status IN ('REQUESTED', 'APPROVED_REFUND', 'APPROVED_REPLACEMENT', 'REJECTED'))
);

CREATE TABLE order_deliveries (
    id uuid PRIMARY KEY,
    order_id uuid NOT NULL REFERENCES orders(id) ON DELETE RESTRICT,
    branch_id uuid NOT NULL REFERENCES branches(id) ON DELETE RESTRICT,
    fulfilment_type varchar(16) NOT NULL,
    assigned_delivery_staff_id uuid REFERENCES staff_users(id) ON DELETE RESTRICT,
    courier_name varchar(150),
    tracking_reference varchar(128),
    handover_note varchar(500),
    dispatched_at timestamptz,
    completed_at timestamptz,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT uq_order_deliveries_order UNIQUE (order_id),
    CONSTRAINT chk_order_deliveries_type CHECK (fulfilment_type IN ('DELIVERY', 'PICKUP'))
);
