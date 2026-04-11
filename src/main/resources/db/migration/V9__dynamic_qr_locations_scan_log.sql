-- Dynamic intern QR attendance: optional locations + audit scan log (does not replace `attendances` table)

CREATE TABLE IF NOT EXISTS attendance_locations (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    address VARCHAR(500) NULL,
    check_in_start_time TIME NULL,
    check_in_deadline TIME NULL,
    check_out_deadline TIME NULL,
    expected_hours_per_day INT NULL,
    latitude DECIMAL(10, 7) NULL,
    longitude DECIMAL(10, 7) NULL,
    active TINYINT(1) NOT NULL DEFAULT 1
);

CREATE TABLE IF NOT EXISTS attendance_scan_logs (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    intern_user_id BIGINT NULL,
    scanned_by_user_id BIGINT NULL,
    location_id BIGINT NULL,
    token_hash CHAR(64) NOT NULL,
    result VARCHAR(32) NOT NULL,
    message VARCHAR(500) NULL,
    ip_address VARCHAR(45) NULL,
    created_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    CONSTRAINT fk_scan_log_intern FOREIGN KEY (intern_user_id) REFERENCES users (id),
    CONSTRAINT fk_scan_log_scanner FOREIGN KEY (scanned_by_user_id) REFERENCES users (id),
    CONSTRAINT fk_scan_log_location FOREIGN KEY (location_id) REFERENCES attendance_locations (id)
);

CREATE INDEX idx_scan_log_created ON attendance_scan_logs (created_at);
CREATE INDEX idx_scan_log_intern ON attendance_scan_logs (intern_user_id);
