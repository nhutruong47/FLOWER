-- V202611240500__create_inventory_and_transfers_tables.sql
-- Module: inventory (REQ-002, REQ-007, REQ-008, REQ-015, REQ-016, FR-015..020)

CREATE TABLE branch_inventories (
    id uuid PRIMARY KEY,
    branch_id uuid NOT NULL REFERENCES branches(id) ON DELETE RESTRICT,
    product_id uuid NOT NULL REFERENCES products(id) ON DELETE RESTRICT,
    available_stock integer NOT NULL DEFAULT 0,
    reserved_stock integer NOT NULL DEFAULT 0,
    version bigint NOT NULL DEFAULT 0,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT uq_branch_inventory_branch_product UNIQUE (branch_id, product_id),
    CONSTRAINT chk_branch_inventories_available_non_negative CHECK (available_stock >= 0),
    CONSTRAINT chk_branch_inventories_reserved_non_negative CHECK (reserved_stock >= 0)
);

CREATE INDEX idx_branch_inventories_branch_prod ON branch_inventories (branch_id, product_id, available_stock);

CREATE TABLE inventory_movements (
    id uuid PRIMARY KEY,
    branch_id uuid NOT NULL REFERENCES branches(id) ON DELETE RESTRICT,
    product_id uuid NOT NULL REFERENCES products(id) ON DELETE RESTRICT,
    reason_code varchar(32) NOT NULL,
    quantity_delta integer NOT NULL,
    available_after integer NOT NULL,
    reserved_after integer NOT NULL,
    source_document_type varchar(32) NOT NULL,
    source_document_id uuid NOT NULL,
    actor_id uuid,
    actor_type varchar(16) NOT NULL,
    note varchar(500),
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT chk_inv_movements_reason CHECK (reason_code IN ('GOODS_RECEIPT', 'ORDER_RESERVED', 'ORDER_CONFIRMED', 'ORDER_RELEASED', 'TRANSFER_OUT', 'TRANSFER_IN', 'SPOILAGE', 'DAMAGED', 'STOCKTAKE_ADJUSTMENT', 'RETURN_RESTOCK')),
    CONSTRAINT chk_inv_movements_delta_non_zero CHECK (quantity_delta <> 0),
    CONSTRAINT chk_inv_movements_avail_non_neg CHECK (available_after >= 0),
    CONSTRAINT chk_inv_movements_res_non_neg CHECK (reserved_after >= 0),
    CONSTRAINT chk_inv_movements_actor_type CHECK (actor_type IN ('STAFF', 'CUSTOMER', 'SYSTEM'))
);

CREATE INDEX idx_inv_movements_branch_created ON inventory_movements (branch_id, created_at DESC);
CREATE INDEX idx_inv_movements_product_created ON inventory_movements (branch_id, product_id, created_at DESC);
CREATE INDEX idx_inv_movements_source_doc ON inventory_movements (source_document_type, source_document_id);

CREATE TABLE goods_receipts (
    id uuid PRIMARY KEY,
    branch_id uuid NOT NULL REFERENCES branches(id) ON DELETE RESTRICT,
    receipt_number varchar(64) NOT NULL,
    supplier_name varchar(200) NOT NULL,
    supplier_reference varchar(100),
    received_by_staff_id uuid NOT NULL REFERENCES staff_users(id) ON DELETE RESTRICT,
    note varchar(500),
    received_at timestamptz NOT NULL DEFAULT now(),
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT uq_goods_receipts_number UNIQUE (receipt_number)
);

CREATE TABLE goods_receipt_lines (
    id uuid PRIMARY KEY,
    goods_receipt_id uuid NOT NULL REFERENCES goods_receipts(id) ON DELETE RESTRICT,
    product_id uuid NOT NULL REFERENCES products(id) ON DELETE RESTRICT,
    received_quantity integer NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT uq_goods_receipt_line_product UNIQUE (goods_receipt_id, product_id),
    CONSTRAINT chk_goods_receipt_qty_pos CHECK (received_quantity > 0)
);

CREATE TABLE stock_adjustments (
    id uuid PRIMARY KEY,
    branch_id uuid NOT NULL REFERENCES branches(id) ON DELETE RESTRICT,
    product_id uuid NOT NULL REFERENCES products(id) ON DELETE RESTRICT,
    adjustment_type varchar(32) NOT NULL,
    quantity_delta integer NOT NULL,
    reason_note varchar(500) NOT NULL,
    recorded_by_staff_id uuid NOT NULL REFERENCES staff_users(id) ON DELETE RESTRICT,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT chk_stock_adj_type CHECK (adjustment_type IN ('SPOILAGE', 'DAMAGED', 'STOCKTAKE_ADJUSTMENT')),
    CONSTRAINT chk_stock_adj_delta CHECK (quantity_delta <> 0 AND ((adjustment_type IN ('SPOILAGE', 'DAMAGED') AND quantity_delta < 0) OR adjustment_type = 'STOCKTAKE_ADJUSTMENT'))
);

CREATE TABLE stock_transfers (
    id uuid PRIMARY KEY,
    transfer_number varchar(64) NOT NULL,
    source_branch_id uuid NOT NULL REFERENCES branches(id) ON DELETE RESTRICT,
    target_branch_id uuid NOT NULL REFERENCES branches(id) ON DELETE RESTRICT,
    status varchar(24) NOT NULL,
    requested_by_staff_id uuid NOT NULL REFERENCES staff_users(id) ON DELETE RESTRICT,
    approved_by_staff_id uuid REFERENCES staff_users(id) ON DELETE RESTRICT,
    dispatched_by_staff_id uuid REFERENCES staff_users(id) ON DELETE RESTRICT,
    received_by_staff_id uuid REFERENCES staff_users(id) ON DELETE RESTRICT,
    note varchar(500),
    dispatched_at timestamptz,
    received_at timestamptz,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT uq_stock_transfers_number UNIQUE (transfer_number),
    CONSTRAINT chk_stock_transfers_distinct_branches CHECK (source_branch_id <> target_branch_id),
    CONSTRAINT chk_stock_transfers_status CHECK (status IN ('REQUESTED', 'APPROVED', 'IN_TRANSIT', 'COMPLETED', 'CANCELLED', 'REJECTED'))
);

CREATE INDEX idx_stock_transfers_source_status ON stock_transfers (source_branch_id, status, created_at DESC);
CREATE INDEX idx_stock_transfers_target_status ON stock_transfers (target_branch_id, status, created_at DESC);

CREATE TABLE stock_transfer_lines (
    id uuid PRIMARY KEY,
    stock_transfer_id uuid NOT NULL REFERENCES stock_transfers(id) ON DELETE RESTRICT,
    product_id uuid NOT NULL REFERENCES products(id) ON DELETE RESTRICT,
    requested_quantity integer NOT NULL,
    dispatched_quantity integer,
    received_quantity integer,
    discrepancy_note varchar(500),
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT uq_stock_transfer_line_product UNIQUE (stock_transfer_id, product_id),
    CONSTRAINT chk_stock_transfer_req_qty CHECK (requested_quantity > 0),
    CONSTRAINT chk_stock_transfer_disp_qty CHECK (dispatched_quantity IS NULL OR dispatched_quantity >= 0),
    CONSTRAINT chk_stock_transfer_rec_qty CHECK (received_quantity IS NULL OR (received_quantity >= 0 AND received_quantity <= dispatched_quantity))
);
