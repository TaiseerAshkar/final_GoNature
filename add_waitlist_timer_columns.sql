-- =============================================================
--  GoNature — Waitlist 1-hour confirmation window migration
--
--  Adds the promotion/timer columns to an existing waiting_list
--  table WITHOUT rebuilding the schema (GoNature_DB.sql does a
--  DROP DATABASE, which would wipe live data).
--
--  Idempotent: each column is added only if it does not already
--  exist, so the script is safe to re-run.
--
--  Run with:  mysql -u root -p park_db < add_waitlist_timer_columns.sql
-- =============================================================

USE park_db;

-- ── status ('WAITING' | 'NOTIFIED') ──────────────────────────
SET @c := (SELECT COUNT(*) FROM information_schema.columns
           WHERE table_schema = DATABASE()
             AND table_name = 'waiting_list' AND column_name = 'status');
SET @s := IF(@c = 0,
    'ALTER TABLE waiting_list ADD COLUMN status VARCHAR(20) NOT NULL DEFAULT ''WAITING'' AFTER order_type',
    'SELECT 1');
PREPARE stmt FROM @s; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- ── order_id (held PENDING order created on promotion) ────────
SET @c := (SELECT COUNT(*) FROM information_schema.columns
           WHERE table_schema = DATABASE()
             AND table_name = 'waiting_list' AND column_name = 'order_id');
SET @s := IF(@c = 0,
    'ALTER TABLE waiting_list ADD COLUMN order_id INT NULL DEFAULT NULL AFTER status',
    'SELECT 1');
PREPARE stmt FROM @s; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- ── promoted_at (when the entry was promoted) ────────────────
SET @c := (SELECT COUNT(*) FROM information_schema.columns
           WHERE table_schema = DATABASE()
             AND table_name = 'waiting_list' AND column_name = 'promoted_at');
SET @s := IF(@c = 0,
    'ALTER TABLE waiting_list ADD COLUMN promoted_at TIMESTAMP NULL DEFAULT NULL',
    'SELECT 1');
PREPARE stmt FROM @s; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- ── confirmation_deadline (promoted_at + 1 hour) ─────────────
SET @c := (SELECT COUNT(*) FROM information_schema.columns
           WHERE table_schema = DATABASE()
             AND table_name = 'waiting_list' AND column_name = 'confirmation_deadline');
SET @s := IF(@c = 0,
    'ALTER TABLE waiting_list ADD COLUMN confirmation_deadline TIMESTAMP NULL DEFAULT NULL',
    'SELECT 1');
PREPARE stmt FROM @s; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- Backfill: any pre-existing rows become plain WAITING entries.
UPDATE waiting_list SET status = 'WAITING' WHERE status IS NULL OR status = '';
