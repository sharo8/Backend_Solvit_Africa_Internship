-- Extra QR generation requests after daily base limit (supervisor-approved bonus, 1–2 per approval).

CREATE TABLE qr_extra_access_requests (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    intern_user_id BIGINT NOT NULL,
    supervisor_user_id BIGINT NOT NULL,
    request_date DATE NOT NULL,
    status VARCHAR(32) NOT NULL,
    bonus_generations INT NULL,
    message VARCHAR(2000) NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    resolved_at TIMESTAMP NULL,
    CONSTRAINT fk_qr_extra_intern FOREIGN KEY (intern_user_id) REFERENCES users (id),
    CONSTRAINT fk_qr_extra_sup FOREIGN KEY (supervisor_user_id) REFERENCES users (id)
);

CREATE INDEX idx_qr_extra_sup_status ON qr_extra_access_requests (supervisor_user_id, status);
CREATE INDEX idx_qr_extra_intern_date ON qr_extra_access_requests (intern_user_id, request_date);
