-- =============================================================================
-- V2: Dedicated Customers Table, Tokens Schema Extension & High-Performance Indexes
-- =============================================================================

-- 1. Create dedicated customers table
CREATE TABLE IF NOT EXISTS customers (
    id INT AUTO_INCREMENT PRIMARY KEY,
    full_name VARCHAR(100) NOT NULL,
    phone VARCHAR(20) NOT NULL,
    email VARCHAR(100) NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT uq_customers_phone UNIQUE (phone)
) ENGINE=InnoDB;

-- 2. Add customer_id, tracking_code, and duration_seconds columns to tokens
ALTER TABLE tokens
    ADD COLUMN customer_id INT NULL AFTER phone,
    ADD COLUMN tracking_code VARCHAR(64) NULL AFTER queue_date,
    ADD COLUMN duration_seconds INT NULL AFTER tracking_code;

-- 3. Populate customers table from existing distinct token records
INSERT IGNORE INTO customers (full_name, phone, created_at)
SELECT customer_name, phone, MIN(created_at)
FROM tokens
WHERE phone IS NOT NULL AND phone != ''
GROUP BY phone, customer_name;

-- 4. Backfill tokens.customer_id based on matching phone numbers
UPDATE tokens t
JOIN customers c ON t.phone = c.phone
SET t.customer_id = c.id
WHERE t.customer_id IS NULL;

-- 5. Backfill tokens.tracking_code with unguessable UUID-based codes
UPDATE tokens
SET tracking_code = UPPER(CONCAT(SUBSTRING(MD5(CONCAT(id, token_no, RAND())), 1, 4), '-',
                                SUBSTRING(MD5(CONCAT(id, RAND())), 5, 4), '-',
                                SUBSTRING(MD5(CONCAT(id, created_at, RAND())), 9, 4)))
WHERE tracking_code IS NULL;

-- 6. Backfill duration_seconds for existing completed tokens
UPDATE tokens
SET duration_seconds = TIMESTAMPDIFF(SECOND, started_at, completed_at)
WHERE status = 'COMPLETED'
  AND started_at IS NOT NULL
  AND completed_at IS NOT NULL
  AND duration_seconds IS NULL;

-- 7. Add foreign key constraint for customer_id
ALTER TABLE tokens
    ADD CONSTRAINT fk_tokens_customer FOREIGN KEY (customer_id) REFERENCES customers(id) ON DELETE SET NULL;

-- 8. Add required indexes
ALTER TABLE tokens
    ADD UNIQUE INDEX uq_tokens_tracking_code (tracking_code),
    ADD INDEX idx_tokens_status_service (status, service_id),
    ADD INDEX idx_tokens_customer_id (customer_id);
