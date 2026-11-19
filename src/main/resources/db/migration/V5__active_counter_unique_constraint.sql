-- =============================================================================
-- V5: Single Active Token Database Guarantee & Recall Limit Tracking
-- =============================================================================

-- 1. Resolve any historical lingering duplicate active tokens (keep newest, mark older as SKIPPED)
UPDATE tokens t
JOIN (
    SELECT id
    FROM (
        SELECT id,
               ROW_NUMBER() OVER (PARTITION BY counter_id ORDER BY id DESC) as rn
        FROM tokens
        WHERE status IN ('CALLED', 'SERVING') AND counter_id IS NOT NULL
    ) ranked
    WHERE ranked.rn > 1
) duplicates ON t.id = duplicates.id
SET t.status = 'SKIPPED', t.completed_at = CURRENT_TIMESTAMP;

-- 2. Add recall_count column if not already present
SET @col_exists := (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'tokens' AND COLUMN_NAME = 'recall_count');
SET @sql := IF(@col_exists = 0, 'ALTER TABLE tokens ADD COLUMN recall_count INT NOT NULL DEFAULT 0 AFTER duration_seconds', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 3. Drop fk_tokens_counter if it has ON DELETE SET NULL (incompatible with generated column)
SET @fk_exists := (SELECT COUNT(*) FROM information_schema.TABLE_CONSTRAINTS WHERE CONSTRAINT_SCHEMA = DATABASE() AND TABLE_NAME = 'tokens' AND CONSTRAINT_NAME = 'fk_tokens_counter');
SET @sql := IF(@fk_exists > 0, 'ALTER TABLE tokens DROP FOREIGN KEY fk_tokens_counter', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 4. Add stored generated column active_counter_id
SET @gen_exists := (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'tokens' AND COLUMN_NAME = 'active_counter_id');
SET @sql := IF(@gen_exists = 0, 'ALTER TABLE tokens ADD COLUMN active_counter_id INT GENERATED ALWAYS AS (CASE WHEN status IN (\'CALLED\', \'SERVING\') THEN counter_id ELSE NULL END) STORED', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 5. Restore foreign key with ON DELETE RESTRICT (compatible with generated columns)
SET @fk_exists := (SELECT COUNT(*) FROM information_schema.TABLE_CONSTRAINTS WHERE CONSTRAINT_SCHEMA = DATABASE() AND TABLE_NAME = 'tokens' AND CONSTRAINT_NAME = 'fk_tokens_counter');
SET @sql := IF(@fk_exists = 0, 'ALTER TABLE tokens ADD CONSTRAINT fk_tokens_counter FOREIGN KEY (counter_id) REFERENCES counters(id) ON DELETE RESTRICT', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 6. Add UNIQUE constraint on active_counter_id
SET @idx_exists := (SELECT COUNT(*) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'tokens' AND INDEX_NAME = 'uq_tokens_active_counter');
SET @sql := IF(@idx_exists = 0, 'ALTER TABLE tokens ADD UNIQUE INDEX uq_tokens_active_counter (active_counter_id)', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
