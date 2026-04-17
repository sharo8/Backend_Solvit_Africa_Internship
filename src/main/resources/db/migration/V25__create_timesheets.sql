CREATE TABLE IF NOT EXISTS timesheets (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    intern_id BIGINT NOT NULL,
    week_number INT NOT NULL,
    week_start_date DATE NOT NULL,
    week_end_date DATE NOT NULL,
    hours_logged DECIMAL(6,2) NOT NULL,
    hours_verified DECIMAL(6,2) NULL,
    status VARCHAR(32) NOT NULL,
    supervisor_note TEXT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    KEY idx_timesheets_intern (intern_id),
    KEY idx_timesheets_week (week_number),
    KEY idx_timesheets_status (status),
    CONSTRAINT fk_timesheets_intern FOREIGN KEY (intern_id) REFERENCES interns(id),
    CONSTRAINT uq_timesheets_intern_week UNIQUE (intern_id, week_number)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
