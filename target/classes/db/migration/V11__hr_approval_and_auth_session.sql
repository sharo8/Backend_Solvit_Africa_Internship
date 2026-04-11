-- Self-service interns: HR/Admin approval gate. Single active JWT session per user (sid claim).
ALTER TABLE users ADD COLUMN hr_approval_status VARCHAR(32) NOT NULL DEFAULT 'APPROVED'
 COMMENT 'PENDING until HR/Admin approves self-registered interns';

ALTER TABLE users
    ADD COLUMN auth_session_id VARCHAR(64) NULL COMMENT 'Must match JWT sid; new login invalidates previous tokens';
