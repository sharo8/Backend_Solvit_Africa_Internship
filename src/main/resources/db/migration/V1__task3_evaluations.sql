-- Task 3: align MySQL schema with Evaluation entity (JPA maps Instant -> datetime(6)).
-- Drops legacy evaluations rows if upgrading from the pre-Task-3 shape. FKs are applied by Hibernate (ddl-auto=update).
DROP TABLE IF EXISTS evaluations;

CREATE TABLE evaluations (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    intern_id BIGINT NOT NULL,
    evaluator_id BIGINT NOT NULL,
    group_id BIGINT NULL,
    type VARCHAR(32) NOT NULL,
    status VARCHAR(32) NOT NULL,
    technical_score INT NULL,
    communication_score INT NULL,
    attendance_score INT NULL,
    initiative_score INT NULL,
    overall_score INT NULL,
    strengths_note VARCHAR(2000) NULL,
    improvement_note VARCHAR(2000) NULL,
    supervisor_comment VARCHAR(2000) NULL,
    intern_response VARCHAR(2000) NULL,
    evaluation_date DATE NULL,
    intern_acknowledged TINYINT(1) NOT NULL DEFAULT 0,
    acknowledged_at DATETIME(6) NULL,
    active TINYINT(1) NOT NULL DEFAULT 1,
    created_at DATETIME(6) NULL,
    updated_at DATETIME(6) NULL,
    KEY idx_eval_intern (intern_id),
    KEY idx_eval_evaluator (evaluator_id),
    KEY idx_eval_group (group_id),
    KEY idx_eval_status (status),
    KEY idx_eval_type (type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
