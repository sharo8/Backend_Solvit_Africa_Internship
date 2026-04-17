-- Fix production/runtime mismatch where old MySQL ENUM values reject newer NotificationType constants
-- (e.g. PENDING_INTERN_REGISTRATION) and cause register/login flows to fail with 500.
ALTER TABLE notifications
    MODIFY COLUMN notification_type VARCHAR(64) NULL;
