-- V202611251000__create_document_number_sequences.sql
-- Modules: order (ORD- numbers), inventory (GR- and TRF- numbers). TASK-018.
--
-- Document numbers used to come from in-memory counters that restarted at the same value after every
-- application restart, so new documents collided with uq_orders_number, uq_goods_receipts_number and
-- uq_stock_transfers_number. Each document type now draws its number from its own PostgreSQL sequence.
-- Sequences are not transactional: a rolled-back document leaves a gap in the numbering, never a duplicate.

CREATE SEQUENCE order_number_seq AS bigint START WITH 2001 INCREMENT BY 1 NO CYCLE;
CREATE SEQUENCE goods_receipt_number_seq AS bigint START WITH 1001 INCREMENT BY 1 NO CYCLE;
CREATE SEQUENCE stock_transfer_number_seq AS bigint START WITH 1001 INCREMENT BY 1 NO CYCLE;

-- Databases that already hold documents numbered by the old counters continue after the highest number in
-- use. On an empty table each sequence keeps the old first number (ORD-2001, GR-1001, TRF-1001).
SELECT setval('order_number_seq',
              GREATEST(2000, COALESCE(MAX(CAST(substring(order_number FROM 5) AS bigint)), 2000)))
FROM orders
WHERE order_number ~ '^ORD-[0-9]{1,18}$';

SELECT setval('goods_receipt_number_seq',
              GREATEST(1000, COALESCE(MAX(CAST(substring(receipt_number FROM 4) AS bigint)), 1000)))
FROM goods_receipts
WHERE receipt_number ~ '^GR-[0-9]{1,18}$';

SELECT setval('stock_transfer_number_seq',
              GREATEST(1000, COALESCE(MAX(CAST(substring(transfer_number FROM 5) AS bigint)), 1000)))
FROM stock_transfers
WHERE transfer_number ~ '^TRF-[0-9]{1,18}$';
