CREATE TABLE public_holiday (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    holiday_date DATE NOT NULL,
    name VARCHAR(255) NULL,
    UNIQUE KEY uk_public_holiday_date (holiday_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE consecutive_absence_warning (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    intern_user_id BIGINT NOT NULL,
    streak_end_date DATE NOT NULL,
    created_at DATETIME(6) NOT NULL,
    UNIQUE KEY uk_intern_streak_end (intern_user_id, streak_end_date),
    KEY idx_caw_intern (intern_user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
