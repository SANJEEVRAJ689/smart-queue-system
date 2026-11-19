-- =============================================================================
-- V4: Extended Enterprise Audit Log Schema
-- =============================================================================

ALTER TABLE audit_log
    ADD COLUMN event_type VARCHAR(50) NULL AFTER action,
    ADD COLUMN entity_type VARCHAR(50) NULL AFTER token_id,
    ADD COLUMN entity_id INT NULL AFTER entity_type,
    ADD COLUMN metadata JSON NULL AFTER entity_id,
    ADD COLUMN ip_address VARCHAR(45) NULL AFTER metadata;

-- Synchronize legacy rows with extended audit format
UPDATE audit_log
SET event_type = action,
    entity_type = IF(token_id IS NOT NULL, 'TOKEN', 'SYSTEM'),
    entity_id = token_id
WHERE event_type IS NULL;

-- Add index on event_type and timestamp for fast audit querying
ALTER TABLE audit_log
    ADD INDEX idx_audit_event_time (event_type, timestamp);
