-- =============================================================
--  GoNature — Demo data for July / August / September 2026
--  Presentation date: 28 June 2026
--
--  Safe to run against a live DB — inserts only, no deletes.
--  Run with:  mysql -u root -p park_db < populate_demo.sql
--
--  Visitor IDs used
--    123456789  Alice Johnson
--    987654321  Bob Martinez
--    111222333  Carol White
--    444555666  David Brown
--    222333444  Emma Davis
--    333444555  Frank Wilson
--    555666777  Grace Taylor
--    666777888  Henry Anderson
--    888999000  Iris Thompson
--    999000111  James Garcia
--    777888999  Eva Guide  (approved GROUP guide — all GROUP rows use this ID)
--
--  Park IDs
--    1  Yosemite National Park
--    2  Grand Canyon National Park
--    3  Yellowstone National Park
-- =============================================================

USE park_db;

-- ─────────────────────────────────────────────────────────────
--  JULY 2026
--  Goal: busy summer month — 20 orders across 3 parks
--        visitor report:  14 confirmed/completed rows on 8 dates
--        usage chart:     visit_times 08–15 well spread
--        cancel table:    6 cancelled / no_show
-- ─────────────────────────────────────────────────────────────

-- July 2 — Park 1, two bookings same day (SOLO + FAMILY)
INSERT INTO orders (visitor_id, park_id, visit_date, visit_time, num_visitors, order_type, status, email, entry_time, exit_time)
VALUES ('123456789', 1, '2026-07-02', '09:00:00', 1, 'solo', 'completed', 'alice.johnson@demo.com',
        '2026-07-02 09:10:00', '2026-07-02 12:45:00');

INSERT INTO orders (visitor_id, park_id, visit_date, visit_time, num_visitors, order_type, status, email, entry_time, exit_time)
VALUES ('444555666', 1, '2026-07-02', '11:00:00', 4, 'family', 'completed', 'david.brown@demo.com',
        '2026-07-02 11:08:00', '2026-07-02 15:00:00');

-- July 5 — Park 2, SOLO + GROUP same day
INSERT INTO orders (visitor_id, park_id, visit_date, visit_time, num_visitors, order_type, status, email, entry_time, exit_time)
VALUES ('333444555', 2, '2026-07-05', '10:00:00', 1, 'solo', 'completed', 'frank.wilson@demo.com',
        '2026-07-05 10:05:00', '2026-07-05 13:30:00');

INSERT INTO orders (visitor_id, park_id, visit_date, visit_time, num_visitors, order_type, status, email, entry_time, exit_time)
VALUES ('777888999', 2, '2026-07-05', '14:00:00', 12, 'group', 'completed', 'eva.guide@demo.com',
        '2026-07-05 14:02:00', '2026-07-05 17:30:00');

-- July 8 — Park 3 FAMILY + Park 1 GROUP
INSERT INTO orders (visitor_id, park_id, visit_date, visit_time, num_visitors, order_type, status, email, entry_time, exit_time)
VALUES ('987654321', 3, '2026-07-08', '09:00:00', 3, 'family', 'completed', 'bob.martinez@demo.com',
        '2026-07-08 09:15:00', '2026-07-08 13:00:00');

INSERT INTO orders (visitor_id, park_id, visit_date, visit_time, num_visitors, order_type, status, email, entry_time, exit_time)
VALUES ('777888999', 1, '2026-07-08', '11:00:00', 10, 'group', 'completed', 'eva.guide@demo.com',
        '2026-07-08 11:00:00', '2026-07-08 15:30:00');

-- July 12 — Park 2 FAMILY (completed)
INSERT INTO orders (visitor_id, park_id, visit_date, visit_time, num_visitors, order_type, status, email, entry_time, exit_time)
VALUES ('555666777', 2, '2026-07-12', '10:00:00', 6, 'family', 'completed', 'grace.taylor@demo.com',
        '2026-07-12 10:10:00', '2026-07-12 14:00:00');

-- July 15 — confirmed bookings (future from demo day)
INSERT INTO orders (visitor_id, park_id, visit_date, visit_time, num_visitors, order_type, status, email)
VALUES ('111222333', 3, '2026-07-15', '08:00:00', 1, 'solo', 'confirmed', 'carol.white@demo.com');

INSERT INTO orders (visitor_id, park_id, visit_date, visit_time, num_visitors, order_type, status, email)
VALUES ('222333444', 1, '2026-07-15', '13:00:00', 5, 'family', 'confirmed', 'emma.davis@demo.com');

-- July 19 — confirmed bookings
INSERT INTO orders (visitor_id, park_id, visit_date, visit_time, num_visitors, order_type, status, email)
VALUES ('666777888', 2, '2026-07-19', '09:00:00', 1, 'solo', 'confirmed', 'henry.anderson@demo.com');

INSERT INTO orders (visitor_id, park_id, visit_date, visit_time, num_visitors, order_type, status, email)
VALUES ('777888999', 3, '2026-07-19', '11:00:00', 8, 'group', 'confirmed', 'eva.guide@demo.com');

-- July 22 — Park 1 FAMILY confirmed
INSERT INTO orders (visitor_id, park_id, visit_date, visit_time, num_visitors, order_type, status, email)
VALUES ('888999000', 1, '2026-07-22', '14:00:00', 4, 'family', 'confirmed', 'iris.thompson@demo.com');

-- July 26 — Park 3 SOLO + Park 2 FAMILY confirmed
INSERT INTO orders (visitor_id, park_id, visit_date, visit_time, num_visitors, order_type, status, email)
VALUES ('999000111', 3, '2026-07-26', '09:00:00', 1, 'solo', 'confirmed', 'james.garcia@demo.com');

INSERT INTO orders (visitor_id, park_id, visit_date, visit_time, num_visitors, order_type, status, email)
VALUES ('123456789', 2, '2026-07-28', '10:00:00', 7, 'family', 'confirmed', 'alice.johnson@demo.com');

-- July cancelled / no_show (for cancellation report)
INSERT INTO orders (visitor_id, park_id, visit_date, visit_time, num_visitors, order_type, status, email)
VALUES ('987654321', 1, '2026-07-10', '09:00:00', 1, 'solo', 'cancelled', 'bob.martinez@demo.com');

INSERT INTO orders (visitor_id, park_id, visit_date, visit_time, num_visitors, order_type, status, email)
VALUES ('444555666', 2, '2026-07-17', '11:00:00', 3, 'family', 'no_show', 'david.brown@demo.com');

INSERT INTO orders (visitor_id, park_id, visit_date, visit_time, num_visitors, order_type, status, email)
VALUES ('555666777', 3, '2026-07-24', '10:00:00', 1, 'solo', 'cancelled', 'grace.taylor@demo.com');

INSERT INTO orders (visitor_id, park_id, visit_date, visit_time, num_visitors, order_type, status, email)
VALUES ('666777888', 1, '2026-07-28', '12:00:00', 2, 'family', 'no_show', 'henry.anderson@demo.com');

INSERT INTO orders (visitor_id, park_id, visit_date, visit_time, num_visitors, order_type, status, email)
VALUES ('333444555', 2, '2026-07-30', '15:00:00', 1, 'solo', 'cancelled', 'frank.wilson@demo.com');

INSERT INTO orders (visitor_id, park_id, visit_date, visit_time, num_visitors, order_type, status, email)
VALUES ('888999000', 3, '2026-07-31', '13:00:00', 4, 'family', 'cancelled', 'iris.thompson@demo.com');


-- ─────────────────────────────────────────────────────────────
--  AUGUST 2026
--  Peak summer — 21 orders, highest visitor counts
-- ─────────────────────────────────────────────────────────────

-- Aug 1 — Park 1 FAMILY + Park 2 GROUP (very busy day)
INSERT INTO orders (visitor_id, park_id, visit_date, visit_time, num_visitors, order_type, status, email, entry_time, exit_time)
VALUES ('999000111', 1, '2026-08-01', '09:00:00', 5, 'family', 'completed', 'james.garcia@demo.com',
        '2026-08-01 09:05:00', '2026-08-01 13:15:00');

INSERT INTO orders (visitor_id, park_id, visit_date, visit_time, num_visitors, order_type, status, email, entry_time, exit_time)
VALUES ('777888999', 2, '2026-08-01', '13:00:00', 15, 'group', 'completed', 'eva.guide@demo.com',
        '2026-08-01 13:00:00', '2026-08-01 17:45:00');

-- Aug 4 — Park 3 SOLO completed
INSERT INTO orders (visitor_id, park_id, visit_date, visit_time, num_visitors, order_type, status, email, entry_time, exit_time)
VALUES ('123456789', 3, '2026-08-04', '10:00:00', 1, 'solo', 'completed', 'alice.johnson@demo.com',
        '2026-08-04 10:12:00', '2026-08-04 13:00:00');

-- Aug 6 — Park 1 FAMILY completed
INSERT INTO orders (visitor_id, park_id, visit_date, visit_time, num_visitors, order_type, status, email, entry_time, exit_time)
VALUES ('444555666', 1, '2026-08-06', '11:00:00', 6, 'family', 'completed', 'david.brown@demo.com',
        '2026-08-06 11:05:00', '2026-08-06 15:30:00');

-- Aug 9 — Park 2 SOLO + Park 3 GROUP
INSERT INTO orders (visitor_id, park_id, visit_date, visit_time, num_visitors, order_type, status, email, entry_time, exit_time)
VALUES ('987654321', 2, '2026-08-09', '09:00:00', 1, 'solo', 'completed', 'bob.martinez@demo.com',
        '2026-08-09 09:18:00', '2026-08-09 12:45:00');

INSERT INTO orders (visitor_id, park_id, visit_date, visit_time, num_visitors, order_type, status, email, entry_time, exit_time)
VALUES ('777888999', 3, '2026-08-09', '14:00:00', 12, 'group', 'completed', 'eva.guide@demo.com',
        '2026-08-09 14:00:00', '2026-08-09 18:00:00');

-- Aug 12 — Park 1 FAMILY completed
INSERT INTO orders (visitor_id, park_id, visit_date, visit_time, num_visitors, order_type, status, email, entry_time, exit_time)
VALUES ('111222333', 1, '2026-08-12', '08:00:00', 4, 'family', 'completed', 'carol.white@demo.com',
        '2026-08-12 08:10:00', '2026-08-12 12:30:00');

-- Aug 15 — confirmed bookings across parks
INSERT INTO orders (visitor_id, park_id, visit_date, visit_time, num_visitors, order_type, status, email)
VALUES ('222333444', 2, '2026-08-15', '10:00:00', 1, 'solo', 'confirmed', 'emma.davis@demo.com');

INSERT INTO orders (visitor_id, park_id, visit_date, visit_time, num_visitors, order_type, status, email)
VALUES ('333444555', 3, '2026-08-15', '12:00:00', 7, 'family', 'confirmed', 'frank.wilson@demo.com');

-- Aug 18 — Park 1 GROUP confirmed
INSERT INTO orders (visitor_id, park_id, visit_date, visit_time, num_visitors, order_type, status, email)
VALUES ('777888999', 1, '2026-08-18', '09:00:00', 10, 'group', 'confirmed', 'eva.guide@demo.com');

-- Aug 20 — Park 2 FAMILY confirmed
INSERT INTO orders (visitor_id, park_id, visit_date, visit_time, num_visitors, order_type, status, email)
VALUES ('444555666', 2, '2026-08-20', '11:00:00', 5, 'family', 'confirmed', 'david.brown@demo.com');

-- Aug 23 — Park 3 SOLO confirmed
INSERT INTO orders (visitor_id, park_id, visit_date, visit_time, num_visitors, order_type, status, email)
VALUES ('555666777', 3, '2026-08-23', '14:00:00', 1, 'solo', 'confirmed', 'grace.taylor@demo.com');

-- Aug 25 — Park 1 FAMILY + Aug 28 Park 2 SOLO + Aug 30 Park 3 GROUP confirmed
INSERT INTO orders (visitor_id, park_id, visit_date, visit_time, num_visitors, order_type, status, email)
VALUES ('666777888', 1, '2026-08-25', '09:00:00', 3, 'family', 'confirmed', 'henry.anderson@demo.com');

INSERT INTO orders (visitor_id, park_id, visit_date, visit_time, num_visitors, order_type, status, email)
VALUES ('888999000', 2, '2026-08-28', '10:00:00', 1, 'solo', 'confirmed', 'iris.thompson@demo.com');

INSERT INTO orders (visitor_id, park_id, visit_date, visit_time, num_visitors, order_type, status, email)
VALUES ('777888999', 3, '2026-08-30', '13:00:00', 9, 'group', 'confirmed', 'eva.guide@demo.com');

-- August cancelled / no_show
INSERT INTO orders (visitor_id, park_id, visit_date, visit_time, num_visitors, order_type, status, email)
VALUES ('999000111', 1, '2026-08-05', '10:00:00', 1, 'solo', 'cancelled', 'james.garcia@demo.com');

INSERT INTO orders (visitor_id, park_id, visit_date, visit_time, num_visitors, order_type, status, email)
VALUES ('123456789', 2, '2026-08-11', '09:00:00', 4, 'family', 'no_show', 'alice.johnson@demo.com');

INSERT INTO orders (visitor_id, park_id, visit_date, visit_time, num_visitors, order_type, status, email)
VALUES ('987654321', 3, '2026-08-17', '11:00:00', 1, 'solo', 'cancelled', 'bob.martinez@demo.com');

INSERT INTO orders (visitor_id, park_id, visit_date, visit_time, num_visitors, order_type, status, email)
VALUES ('111222333', 1, '2026-08-22', '14:00:00', 6, 'family', 'no_show', 'carol.white@demo.com');

INSERT INTO orders (visitor_id, park_id, visit_date, visit_time, num_visitors, order_type, status, email)
VALUES ('222333444', 2, '2026-08-26', '09:00:00', 1, 'solo', 'cancelled', 'emma.davis@demo.com');

INSERT INTO orders (visitor_id, park_id, visit_date, visit_time, num_visitors, order_type, status, email)
VALUES ('333444555', 3, '2026-08-31', '12:00:00', 3, 'family', 'cancelled', 'frank.wilson@demo.com');


-- ─────────────────────────────────────────────────────────────
--  SEPTEMBER 2026
--  Post-peak, quieter — 18 orders across 3 parks
-- ─────────────────────────────────────────────────────────────

-- Sep 2 — Park 1 FAMILY + Park 2 GROUP completed
INSERT INTO orders (visitor_id, park_id, visit_date, visit_time, num_visitors, order_type, status, email, entry_time, exit_time)
VALUES ('444555666', 1, '2026-09-02', '10:00:00', 4, 'family', 'completed', 'david.brown@demo.com',
        '2026-09-02 10:08:00', '2026-09-02 13:45:00');

INSERT INTO orders (visitor_id, park_id, visit_date, visit_time, num_visitors, order_type, status, email, entry_time, exit_time)
VALUES ('777888999', 2, '2026-09-02', '14:00:00', 9, 'group', 'completed', 'eva.guide@demo.com',
        '2026-09-02 14:00:00', '2026-09-02 17:15:00');

-- Sep 5 — Park 3 SOLO completed
INSERT INTO orders (visitor_id, park_id, visit_date, visit_time, num_visitors, order_type, status, email, entry_time, exit_time)
VALUES ('555666777', 3, '2026-09-05', '09:00:00', 1, 'solo', 'completed', 'grace.taylor@demo.com',
        '2026-09-05 09:20:00', '2026-09-05 12:30:00');

-- Sep 7 — Park 1 FAMILY completed
INSERT INTO orders (visitor_id, park_id, visit_date, visit_time, num_visitors, order_type, status, email, entry_time, exit_time)
VALUES ('666777888', 1, '2026-09-07', '11:00:00', 3, 'family', 'completed', 'henry.anderson@demo.com',
        '2026-09-07 11:05:00', '2026-09-07 14:30:00');

-- Sep 10 — Park 2 SOLO + Park 3 GROUP completed
INSERT INTO orders (visitor_id, park_id, visit_date, visit_time, num_visitors, order_type, status, email, entry_time, exit_time)
VALUES ('888999000', 2, '2026-09-10', '09:00:00', 1, 'solo', 'completed', 'iris.thompson@demo.com',
        '2026-09-10 09:10:00', '2026-09-10 12:00:00');

INSERT INTO orders (visitor_id, park_id, visit_date, visit_time, num_visitors, order_type, status, email, entry_time, exit_time)
VALUES ('777888999', 3, '2026-09-10', '13:00:00', 11, 'group', 'completed', 'eva.guide@demo.com',
        '2026-09-10 13:00:00', '2026-09-10 17:00:00');

-- Sep 14 — Park 1 FAMILY confirmed
INSERT INTO orders (visitor_id, park_id, visit_date, visit_time, num_visitors, order_type, status, email)
VALUES ('999000111', 1, '2026-09-14', '10:00:00', 5, 'family', 'confirmed', 'james.garcia@demo.com');

-- Sep 17 — Park 2 SOLO + Park 3 FAMILY confirmed
INSERT INTO orders (visitor_id, park_id, visit_date, visit_time, num_visitors, order_type, status, email)
VALUES ('123456789', 2, '2026-09-17', '08:00:00', 1, 'solo', 'confirmed', 'alice.johnson@demo.com');

INSERT INTO orders (visitor_id, park_id, visit_date, visit_time, num_visitors, order_type, status, email)
VALUES ('987654321', 3, '2026-09-17', '12:00:00', 6, 'family', 'confirmed', 'bob.martinez@demo.com');

-- Sep 21 — Park 1 GROUP confirmed
INSERT INTO orders (visitor_id, park_id, visit_date, visit_time, num_visitors, order_type, status, email)
VALUES ('777888999', 1, '2026-09-21', '09:00:00', 8, 'group', 'confirmed', 'eva.guide@demo.com');

-- Sep 24 — Park 2 FAMILY + Sep 28 Park 3 SOLO confirmed
INSERT INTO orders (visitor_id, park_id, visit_date, visit_time, num_visitors, order_type, status, email)
VALUES ('111222333', 2, '2026-09-24', '11:00:00', 4, 'family', 'confirmed', 'carol.white@demo.com');

INSERT INTO orders (visitor_id, park_id, visit_date, visit_time, num_visitors, order_type, status, email)
VALUES ('222333444', 3, '2026-09-28', '14:00:00', 1, 'solo', 'confirmed', 'emma.davis@demo.com');

-- September cancelled / no_show
INSERT INTO orders (visitor_id, park_id, visit_date, visit_time, num_visitors, order_type, status, email)
VALUES ('333444555', 1, '2026-09-06', '10:00:00', 1, 'solo', 'cancelled', 'frank.wilson@demo.com');

INSERT INTO orders (visitor_id, park_id, visit_date, visit_time, num_visitors, order_type, status, email)
VALUES ('444555666', 2, '2026-09-12', '09:00:00', 5, 'family', 'no_show', 'david.brown@demo.com');

INSERT INTO orders (visitor_id, park_id, visit_date, visit_time, num_visitors, order_type, status, email)
VALUES ('555666777', 3, '2026-09-18', '11:00:00', 1, 'solo', 'cancelled', 'grace.taylor@demo.com');

INSERT INTO orders (visitor_id, park_id, visit_date, visit_time, num_visitors, order_type, status, email)
VALUES ('666777888', 1, '2026-09-22', '14:00:00', 2, 'family', 'no_show', 'henry.anderson@demo.com');

INSERT INTO orders (visitor_id, park_id, visit_date, visit_time, num_visitors, order_type, status, email)
VALUES ('888999000', 2, '2026-09-26', '09:00:00', 1, 'solo', 'cancelled', 'iris.thompson@demo.com');

INSERT INTO orders (visitor_id, park_id, visit_date, visit_time, num_visitors, order_type, status, email)
VALUES ('999000111', 3, '2026-09-30', '13:00:00', 3, 'family', 'cancelled', 'james.garcia@demo.com');

-- =============================================================
--  Summary
--  July    : 20 orders — 14 confirmed/completed, 6 cancelled/no_show
--  August  : 21 orders — 15 confirmed/completed, 6 cancelled/no_show
--  September: 18 orders — 12 confirmed/completed, 6 cancelled/no_show
--  Total   : 59 orders inserted
-- =============================================================
