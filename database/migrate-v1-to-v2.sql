-- Back up the database before running this migration.
-- Run once against an existing StockFlow v1 database whose order_history table
-- uses an integer order_id and a priority column.

USE stockflow;

ALTER TABLE order_history DROP PRIMARY KEY;
ALTER TABLE order_history
    CHANGE COLUMN order_id legacy_order_id INT NOT NULL,
    CHANGE COLUMN priority legacy_priority INT NULL DEFAULT 0;

ALTER TABLE order_history ADD COLUMN order_id CHAR(36) NULL;
UPDATE order_history SET order_id = UUID() WHERE order_id IS NULL;
ALTER TABLE order_history
    MODIFY COLUMN order_id CHAR(36) NOT NULL,
    ADD PRIMARY KEY (order_id);
