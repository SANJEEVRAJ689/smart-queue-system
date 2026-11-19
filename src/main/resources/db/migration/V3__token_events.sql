-- =============================================================================
-- V3: Immutable Token State Transition Audit History (token_events)
-- =============================================================================

CREATE TABLE IF NOT EXISTS token_events (
    id INT AUTO_INCREMENT PRIMARY KEY,
    token_id INT NOT NULL,
    from_status VARCHAR(20) NULL,
    to_status VARCHAR(20) NOT NULL,
    user_id INT NULL,
    counter_id INT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_te_token FOREIGN KEY (token_id) REFERENCES tokens(id) ON DELETE CASCADE,
    CONSTRAINT fk_te_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE SET NULL,
    CONSTRAINT fk_te_counter FOREIGN KEY (counter_id) REFERENCES counters(id) ON DELETE SET NULL,
    INDEX idx_te_token_created (token_id, created_at)
) ENGINE=InnoDB;

-- Backfill initial state creation event for all existing tokens
INSERT INTO token_events (token_id, from_status, to_status, user_id, counter_id, created_at)
SELECT id, NULL, 'WAITING', NULL, NULL, created_at
FROM tokens;

-- Backfill called transition events for tokens that reached CALLED, SERVING, or COMPLETED
INSERT INTO token_events (token_id, from_status, to_status, user_id, counter_id, created_at)
SELECT id, 'WAITING', 'CALLED', NULL, counter_id, called_at
FROM tokens
WHERE called_at IS NOT NULL;

-- Backfill serving transition events for tokens that reached SERVING or COMPLETED
INSERT INTO token_events (token_id, from_status, to_status, user_id, counter_id, created_at)
SELECT id, 'CALLED', 'SERVING', NULL, counter_id, started_at
FROM tokens
WHERE started_at IS NOT NULL;

-- Backfill terminal transition events (COMPLETED, SKIPPED, CANCELLED)
INSERT INTO token_events (token_id, from_status, to_status, user_id, counter_id, created_at)
SELECT id, 
       IF(started_at IS NOT NULL, 'SERVING', 'CALLED'), 
       status, 
       NULL, 
       counter_id, 
       IFNULL(completed_at, created_at)
FROM tokens
WHERE status IN ('COMPLETED', 'SKIPPED', 'CANCELLED');
