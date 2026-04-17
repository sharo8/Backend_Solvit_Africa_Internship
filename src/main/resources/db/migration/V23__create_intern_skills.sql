CREATE TABLE IF NOT EXISTS intern_skills (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    intern_id BIGINT NOT NULL,
    skill_name VARCHAR(255) NOT NULL,
    initial_level VARCHAR(32) NOT NULL,
    current_level VARCHAR(32) NOT NULL,
    assessed_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    assessed_by BIGINT NULL,
    notes TEXT NULL,
    KEY idx_intern_skills_intern (intern_id),
    KEY idx_intern_skills_name (skill_name),
    KEY idx_intern_skills_assessed_by (assessed_by),
    CONSTRAINT fk_intern_skills_intern FOREIGN KEY (intern_id) REFERENCES interns(id),
    CONSTRAINT fk_intern_skills_assessed_by FOREIGN KEY (assessed_by) REFERENCES users(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
