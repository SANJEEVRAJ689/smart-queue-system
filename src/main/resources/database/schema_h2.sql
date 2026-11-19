-- H2 Schema in MySQL Mode
CREATE TABLE IF NOT EXISTS users (
    id INT AUTO_INCREMENT PRIMARY KEY,
    username VARCHAR(50) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    full_name VARCHAR(100) NOT NULL,
    role VARCHAR(20) NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS services (
    id INT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(100) NOT NULL UNIQUE,
    prefix CHAR(2) NOT NULL UNIQUE,
    default_avg_minutes INT NOT NULL DEFAULT 5,
    active BOOLEAN NOT NULL DEFAULT TRUE
);

CREATE TABLE IF NOT EXISTS counters (
    id INT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(50) NOT NULL,
    service_id INT NOT NULL,
    staff_id INT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'CLOSED',
    current_token_id INT NULL,
    FOREIGN KEY (service_id) REFERENCES services(id) ON DELETE CASCADE,
    FOREIGN KEY (staff_id) REFERENCES users(id) ON DELETE SET NULL
);

CREATE TABLE IF NOT EXISTS tokens (
    id INT AUTO_INCREMENT PRIMARY KEY,
    token_no VARCHAR(10) NOT NULL,
    service_id INT NOT NULL,
    customer_name VARCHAR(100) NOT NULL,
    phone VARCHAR(20) NOT NULL,
    priority VARCHAR(20) NOT NULL DEFAULT 'NORMAL',
    status VARCHAR(20) NOT NULL DEFAULT 'WAITING',
    counter_id INT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    called_at TIMESTAMP NULL,
    started_at TIMESTAMP NULL,
    completed_at TIMESTAMP NULL,
    queue_date DATE NOT NULL,
    FOREIGN KEY (service_id) REFERENCES services(id) ON DELETE CASCADE,
    FOREIGN KEY (counter_id) REFERENCES counters(id) ON DELETE SET NULL,
    CONSTRAINT uq_service_date_token UNIQUE (service_id, queue_date, token_no)
);

CREATE INDEX IF NOT EXISTS idx_tokens_lookup ON tokens (service_id, status, priority, created_at);
CREATE INDEX IF NOT EXISTS idx_tokens_queue_date ON tokens (queue_date);
CREATE INDEX IF NOT EXISTS idx_tokens_phone ON tokens (phone);

CREATE TABLE IF NOT EXISTS token_sequence (
    service_id INT NOT NULL,
    queue_date DATE NOT NULL,
    last_number INT NOT NULL DEFAULT 0,
    PRIMARY KEY (service_id, queue_date),
    FOREIGN KEY (service_id) REFERENCES services(id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS audit_log (
    id INT AUTO_INCREMENT PRIMARY KEY,
    user_id INT NULL,
    action VARCHAR(100) NOT NULL,
    token_id INT NULL,
    timestamp TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE SET NULL,
    FOREIGN KEY (token_id) REFERENCES tokens(id) ON DELETE SET NULL
);

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
    FOREIGN KEY (service_id) REFERENCES services(id) ON DELETE CASCADE
);
