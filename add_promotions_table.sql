-- =============================================================
--  GoNature — Promotions feature migration
--
--  Adds the `promotions` table to an existing park_db WITHOUT
--  rebuilding the whole schema (GoNature_DB.sql does a DROP
--  DATABASE, which would wipe live data).
--
--  Run with:  mysql -u root -p park_db < add_promotions_table.sql
-- =============================================================

USE park_db;

CREATE TABLE IF NOT EXISTS promotions (
    id               INT            AUTO_INCREMENT PRIMARY KEY,
    park_id          INT            NOT NULL,
    description      VARCHAR(255)   NOT NULL,
    discount_percent DOUBLE         NOT NULL,
    start_date       DATE           NOT NULL,
    end_date         DATE           NOT NULL,
    status           ENUM('PENDING', 'ACTIVE', 'REJECTED')
                                    NOT NULL DEFAULT 'PENDING',
    submitted_by     VARCHAR(50)    NOT NULL,
    created_at       TIMESTAMP      DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT chk_promo_percent CHECK (discount_percent >= 0 AND discount_percent <= 50),
    CONSTRAINT chk_promo_dates   CHECK (end_date >= start_date),

    CONSTRAINT fk_promo_park
        FOREIGN KEY (park_id) REFERENCES parks(id)
        ON DELETE CASCADE
        ON UPDATE CASCADE,

    INDEX idx_promo_park_status (park_id, status)
);

-- Optional sample promotion (already approved/active) for demo purposes.
-- Uncomment to insert a 15% summer discount on Yosemite (park 1).
-- INSERT INTO promotions (park_id, description, discount_percent, start_date, end_date, status, submitted_by)
-- VALUES (1, 'Summer Special — 15% off all visits', 15.0, '2026-06-01', '2026-09-30', 'ACTIVE', 'manager1');
