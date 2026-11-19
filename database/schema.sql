-- =============================================================================
-- SMART QUEUE AND TOKEN MANAGEMENT SYSTEM
-- Production Database Schema for MySQL 8.0+
-- =============================================================================

CREATE DATABASE IF NOT EXISTS smart_queue_db
CHARACTER SET utf8mb4
COLLATE utf8mb4_unicode_ci;

USE smart_queue_db;

-- -----------------------------------------------------------------------------
-- 1. USERS TABLE
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS users (
    id INT AUTO_INCREMENT PRIMARY KEY,
    username VARCHAR(50) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    full_name VARCHAR(100) NOT NULL,
    role ENUM('ADMIN', 'STAFF') NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB;

-- -----------------------------------------------------------------------------
-- 2. CUSTOMERS TABLE (Guest/Customer multi-visit profiles)
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS customers (
    id INT AUTO_INCREMENT PRIMARY KEY,
    full_name VARCHAR(100) NOT NULL,
    phone VARCHAR(20) NOT NULL,
    email VARCHAR(100) NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT uq_customers_phone UNIQUE (phone)
) ENGINE=InnoDB;

-- -----------------------------------------------------------------------------
-- 3. SERVICES TABLE
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS services (
    id INT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(100) NOT NULL UNIQUE,
    prefix CHAR(2) NOT NULL UNIQUE,
    default_avg_minutes INT NOT NULL DEFAULT 5,
    active BOOLEAN NOT NULL DEFAULT TRUE
) ENGINE=InnoDB;

-- -----------------------------------------------------------------------------
-- 4. COUNTERS TABLE
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS counters (
    id INT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(50) NOT NULL,
    service_id INT NOT NULL,
    staff_id INT NULL,
    status ENUM('OPEN', 'CLOSED', 'BUSY') NOT NULL DEFAULT 'CLOSED',
    current_token_id INT NULL,
    CONSTRAINT fk_counters_service FOREIGN KEY (service_id) REFERENCES services(id) ON DELETE CASCADE,
    CONSTRAINT fk_counters_staff FOREIGN KEY (staff_id) REFERENCES users(id) ON DELETE SET NULL
) ENGINE=InnoDB;

-- -----------------------------------------------------------------------------
-- 5. TOKENS TABLE
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS tokens (
    id INT AUTO_INCREMENT PRIMARY KEY,
    token_no VARCHAR(10) NOT NULL,
    service_id INT NOT NULL,
    customer_name VARCHAR(100) NOT NULL,
    phone VARCHAR(20) NOT NULL,
    customer_id INT NULL,
    priority ENUM('NORMAL', 'SENIOR', 'EMERGENCY') NOT NULL DEFAULT 'NORMAL',
    status ENUM('WAITING', 'CALLED', 'SERVING', 'COMPLETED', 'SKIPPED', 'CANCELLED') NOT NULL DEFAULT 'WAITING',
    counter_id INT NULL,
    queue_date DATE NOT NULL,
    tracking_code VARCHAR(64) NOT NULL UNIQUE,
    duration_seconds INT NULL,
    recall_count INT NOT NULL DEFAULT 0,
    active_counter_id INT GENERATED ALWAYS AS (
        CASE WHEN status IN ('CALLED', 'SERVING') THEN counter_id ELSE NULL END
    ) STORED,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    called_at TIMESTAMP NULL,
    started_at TIMESTAMP NULL,
    completed_at TIMESTAMP NULL,
    CONSTRAINT fk_tokens_service FOREIGN KEY (service_id) REFERENCES services(id) ON DELETE CASCADE,
    CONSTRAINT fk_tokens_counter FOREIGN KEY (counter_id) REFERENCES counters(id) ON DELETE SET NULL,
    CONSTRAINT fk_tokens_customer FOREIGN KEY (customer_id) REFERENCES customers(id) ON DELETE SET NULL,
    CONSTRAINT uq_service_date_token UNIQUE (service_id, queue_date, token_no),
    UNIQUE INDEX uq_tokens_active_counter (active_counter_id),
    INDEX idx_tokens_status_service (status, service_id),
    INDEX idx_tokens_queue_date (queue_date),
    INDEX idx_tokens_customer_id (customer_id),
    INDEX idx_tokens_lookup (service_id, status, priority, created_at),
    INDEX idx_tokens_phone (phone)
) ENGINE=InnoDB;

-- Add circular foreign key for counters.current_token_id safely
SET @fk_exists := (
    SELECT COUNT(*) 
    FROM information_schema.TABLE_CONSTRAINTS 
    WHERE CONSTRAINT_SCHEMA = DATABASE() 
      AND TABLE_NAME = 'counters' 
      AND CONSTRAINT_NAME = 'fk_counters_token'
);
SET @sql := IF(@fk_exists = 0, 
    'ALTER TABLE counters ADD CONSTRAINT fk_counters_token FOREIGN KEY (current_token_id) REFERENCES tokens(id) ON DELETE SET NULL', 
    'SELECT 1'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- -----------------------------------------------------------------------------
-- 6. TOKEN SEQUENCE TABLE (Row-level locking for atomic numbering)
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS token_sequence (
    service_id INT NOT NULL,
    queue_date DATE NOT NULL,
    last_number INT NOT NULL DEFAULT 0,
    PRIMARY KEY (service_id, queue_date),
    CONSTRAINT fk_seq_service FOREIGN KEY (service_id) REFERENCES services(id) ON DELETE CASCADE
) ENGINE=InnoDB;

-- -----------------------------------------------------------------------------
-- 7. TOKEN EVENTS TABLE (Immutable state transition audit log)
-- -----------------------------------------------------------------------------
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

-- -----------------------------------------------------------------------------
-- 8. AUDIT LOG TABLE (Extended enterprise audit trail)
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS audit_log (
    id INT AUTO_INCREMENT PRIMARY KEY,
    user_id INT NULL,
    action VARCHAR(100) NOT NULL,
    event_type VARCHAR(50) NULL,
    token_id INT NULL,
    entity_type VARCHAR(50) NULL,
    entity_id INT NULL,
    metadata JSON NULL,
    ip_address VARCHAR(45) NULL,
    timestamp TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_audit_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE SET NULL,
    CONSTRAINT fk_audit_token FOREIGN KEY (token_id) REFERENCES tokens(id) ON DELETE SET NULL,
    INDEX idx_audit_event_time (event_type, timestamp)
) ENGINE=InnoDB;

-- -----------------------------------------------------------------------------
-- 9. SERVICE DAILY STATS TABLE (Analytical Reporting Cache)
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS service_stats (
    id INT AUTO_INCREMENT PRIMARY KEY,
    service_id INT NOT NULL,
    stat_date DATE NOT NULL,
    total_tokens INT NOT NULL DEFAULT 0,
    completed_tokens INT NOT NULL DEFAULT 0,
    skipped_tokens INT NOT NULL DEFAULT 0,
    avg_wait_seconds INT NOT NULL DEFAULT 0,
    avg_service_seconds INT NOT NULL DEFAULT 0,
    CONSTRAINT uq_service_stat_date UNIQUE (service_id, stat_date),
    CONSTRAINT fk_stats_service FOREIGN KEY (service_id) REFERENCES services(id) ON DELETE CASCADE
) ENGINE=InnoDB;

-- -----------------------------------------------------------------------------
-- 10. REFRESH TOKENS TABLE (Rotating JWT Refresh Tokens)
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS refresh_tokens (
    id INT AUTO_INCREMENT PRIMARY KEY,
    user_id INT NOT NULL,
    token_hash VARCHAR(255) NOT NULL UNIQUE,
    expiry_date TIMESTAMP NOT NULL,
    revoked BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_refresh_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    INDEX idx_refresh_user_id (user_id),
    INDEX idx_refresh_token_hash (token_hash)
) ENGINE=InnoDB;
