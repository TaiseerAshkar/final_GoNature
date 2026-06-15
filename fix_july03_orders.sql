-- =============================================================
--  GoNature — Fix bad demo data for park_id=1 (Yosemite), 2026-07-03
--
--  Problem : duplicate visitor bookings on the same date, and total
--            num_visitors exceeds park capacity.
--
--  Solution: delete all orders for that park+date, then re-insert
--            11 clean rows (one per unique visitor), totalling 87
--            visitors (85-90 range, confirmed, diverse times).
--
--  Run with:  mysql -u root -p park_db < fix_july03_orders.sql
-- =============================================================

USE park_db;

-- ── Step 1: Remove all bad orders for this park+date ─────────
DELETE FROM orders
WHERE park_id = 1
  AND visit_date = '2026-07-03';

-- ── Step 2: Insert 11 clean orders, each unique visitor ───────
--  Visitor totals:
--    123456789  8 visitors
--    987654321 10 visitors
--    111222333  7 visitors
--    444555666  9 visitors
--    222333444  1 visitor
--    333444555  8 visitors
--    555666777  6 visitors
--    666777888  7 visitors
--    888999000  5 visitors
--    999000111  9 visitors
--    777888999 16 visitors (group guide)
--  ─────────────────────────────────
--  TOTAL       87 visitors  ✓ (85-90 range, within park capacity)

INSERT INTO orders
  (visitor_id, park_id, visit_date, visit_time, num_visitors, order_type, status, email)
VALUES
  ('123456789', 1, '2026-07-03', '08:00:00',  8, 'family', 'confirmed', 'alice.johnson@demo.com'),
  ('987654321', 1, '2026-07-03', '09:00:00', 10, 'family', 'confirmed', 'bob.martinez@demo.com'),
  ('111222333', 1, '2026-07-03', '09:30:00',  7, 'family', 'confirmed', 'carol.white@demo.com'),
  ('444555666', 1, '2026-07-03', '10:00:00',  9, 'family', 'confirmed', 'david.brown@demo.com'),
  ('222333444', 1, '2026-07-03', '10:00:00',  1, 'solo',   'confirmed', 'emma.davis@demo.com'),
  ('333444555', 1, '2026-07-03', '11:00:00',  8, 'family', 'confirmed', 'frank.wilson@demo.com'),
  ('555666777', 1, '2026-07-03', '11:00:00',  6, 'family', 'confirmed', 'grace.taylor@demo.com'),
  ('666777888', 1, '2026-07-03', '12:00:00',  7, 'family', 'confirmed', 'henry.anderson@demo.com'),
  ('888999000', 1, '2026-07-03', '13:00:00',  5, 'family', 'confirmed', 'iris.thompson@demo.com'),
  ('999000111', 1, '2026-07-03', '13:00:00',  9, 'family', 'confirmed', 'james.garcia@demo.com'),
  ('777888999', 1, '2026-07-03', '14:00:00', 16, 'group',  'confirmed', 'eva.guide@demo.com');

-- ── Verify ────────────────────────────────────────────────────
SELECT
    visitor_id,
    visit_time,
    num_visitors,
    order_type,
    status
FROM orders
WHERE park_id = 1 AND visit_date = '2026-07-03'
ORDER BY visit_time, visitor_id;

SELECT SUM(num_visitors) AS total_visitors_july03
FROM orders
WHERE park_id = 1
  AND visit_date = '2026-07-03'
  AND status = 'confirmed';
