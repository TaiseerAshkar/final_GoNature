-- ============================================================
--  GoNature — Park Settings Approval Workflow
--  Run against: park_db
--
--  Adds the park_settings_requests table used when park managers
--  propose parameter changes that require department manager approval.
-- ============================================================

USE park_db;

CREATE TABLE IF NOT EXISTS park_settings_requests (
    id                    INT            AUTO_INCREMENT PRIMARY KEY,
    park_id               INT            NOT NULL,
    requested_by          VARCHAR(50)    NOT NULL,          -- username of the park manager
    capacity              INT            NOT NULL,
    max_orders            INT            NOT NULL,
    visit_duration_hours  INT            NOT NULL,
    full_price            DECIMAL(10,2)  NOT NULL,
    status                ENUM('pending','approved','rejected') NOT NULL DEFAULT 'pending',
    requested_at          TIMESTAMP      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    reviewed_at           TIMESTAMP      NULL DEFAULT NULL,

    CONSTRAINT fk_psr_park
        FOREIGN KEY (park_id) REFERENCES parks(id)
        ON DELETE CASCADE ON UPDATE CASCADE,

    INDEX idx_psr_status  (status),
    INDEX idx_psr_park    (park_id)
);
