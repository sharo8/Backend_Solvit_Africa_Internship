CREATE TABLE IF NOT EXISTS ai_configurations (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    config_key VARCHAR(120) NOT NULL,
    config_value VARCHAR(255) NOT NULL,
    description TEXT NULL,
    updated_by BIGINT NULL,
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    KEY idx_ai_config_updated_by (updated_by),
    CONSTRAINT uq_ai_config_key UNIQUE (config_key),
    CONSTRAINT fk_ai_config_updated_by FOREIGN KEY (updated_by) REFERENCES users(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
