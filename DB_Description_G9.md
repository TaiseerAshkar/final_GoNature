# GoNature — Test Database Description
**Group 9 | Assignment 3 | Demo date: 28 June 2026**

---

## 1. Schema Overview — Tables

| Table | Description | Seed rows |
|---|---|---|
| `parks` | National parks with capacity, booking limit, visit duration, and full ticket price | 3 |
| `users` | Staff accounts (department manager, park managers, employees, service rep) with username/password and optional park assignment | 8 |
| `visitors` | Every person who has ever interacted with the system — visitor, subscriber, and guide records all anchor here via PK `id_number` | 29 |
| `subscribers` | Family Member Club members; extends `visitors` with family size and optional credit card (NULL = cash payment) | 10 |
| `guides` | Approved tour guides; extends `visitors` with an `is_approved` flag (0 = pending, 1 = approved) | 6 |
| `orders` | All park visit bookings across all statuses — completed, confirmed, cancelled, and no-show | **4,888** |
| `waiting_list` | Visitors waiting for a slot to open; includes timer columns for the 1-hour confirmation window | 8 |
| `promotions` | Park-manager-submitted discount promotions awaiting department-manager approval | 3 |
| `park_settings_requests` | Pending change requests for park capacity / max orders / price, requiring department-manager approval | 3 |

---

## 2. Parks

| ID | Name | Capacity | Max Orders | Visit Duration | Full Price |
|---|---|---|---|---|---|
| 1 | Yosemite National Park | 100 | 90 | 4 hours | $50.00 |
| 2 | Grand Canyon National Park | 150 | 135 | 4 hours | $45.00 |
| 3 | Yellowstone National Park | 120 | 108 | 4 hours | $40.00 |

Peak visitor hours used in the seed data: Yosemite → 10:00, Grand Canyon → 12:00, Yellowstone → 13:00.

---

## 3. Demo Credentials

### 3.1 Staff — login via the "Staff Login" screen

| Username | Password | Role | Assigned Park |
|---|---|---|---|
| `admin` | `admin123` | Department Manager | (all parks) |
| `manager1` | `pass123` | Park Manager | Yosemite |
| `manager2` | `pass123` | Park Manager | Grand Canyon |
| `manager3` | `pass123` | Park Manager | Yellowstone |
| `employee1` | `pass123` | Park Employee | Yosemite |
| `employee2` | `pass123` | Park Employee | Grand Canyon |
| `employee3` | `pass123` | Park Employee | Yellowstone |
| `service` | `service123` | Service Representative | (all parks) |

### 3.2 Visitors — login via the "Visitor / Subscriber" screen using ID number

| ID Number | Name | Notes |
|---|---|---|
| `123456789` | Alice Cohen | **Subscriber** (sub-ID 1, family size 4, card on file `4111111111111111`) |
| `987654321` | Bob Levi | Plain visitor |
| `305928471` | Yoni Cohen | **Subscriber** (sub-ID 2, family size 3, card on file) |
| `312654987` | Maya Shapiro | **Subscriber** (sub-ID 3, family size 5, **cash — no card**) |
| `318472659` | Noa Friedman | **Subscriber** (sub-ID 4, family size 2, card on file) |
| `332158746` | Tamar Levi | **Subscriber** (sub-ID 5, family size 4, cash) |
| `341209856` | Shira Goldberg | **Subscriber** (sub-ID 6, family size 3, card on file) |
| `327654129` | Yael Avraham | **Subscriber** (sub-ID 7, family size 5, card on file) |
| `339182746` | Hila Rosen | **Subscriber** (sub-ID 8, family size 2, cash) |
| `351749862` | Roni Klein | **Subscriber** (sub-ID 9, family size 4, card on file) |
| `284619375` | Inbal Dayan | **Subscriber** (sub-ID 10, family size 3, cash) |
| `204857361` | Dan Ben-David | Plain visitor |
| `257391846` | Itai Mizrahi | Plain visitor |
| `268741935` | Eitan Bar-On | Plain visitor |

### 3.3 Approved Guides — login with ID number, guide role granted on login

| ID Number | Name | Email |
|---|---|---|
| `777888999` | Eva Guide | eva.guide@email.com |
| `213761018` | Taiseer Ashkar | taiseer.ashkar@gmail.com |
| `295837164` | Gilad Naveh | gilad.naveh@guides.com |
| `308561927` | Liat Barak | liat.barak@guides.com |
| `276194835` | Ariel Stern | ariel.stern@guides.com |
| `341875962` | Doron Gal | doron.gal@guides.com |

All 6 guides have `is_approved = 1`.

---

## 4. Order Data — Date Ranges and Volumes

| Status | Count | Date Range | Notes |
|---|---|---|---|
| `completed` | 3,223 | 2026-04-01 – 2026-06-20 | entry_time and exit_time both set; real 3–5 h visit durations |
| `confirmed` | 1,617 | 2026-06-21 – 2026-07-31 | Future bookings; no entry or exit time |
| `cancelled` | 34 | 2026-04-03 – 2026-07-22 | Scattered across all three parks and all visit types |
| `no_show` | 14 | 2026-04-02 – 2026-06-16 | Past-dated; no check-in recorded |
| **Total** | **4,888** | | |

Visit types present: `individual` (exactly 1 visitor), `family` (2–15), `group` (2–15, led by an approved guide).

The completed data has a realistic daily peak shape — Yosemite concentrates volume around 10:00, Grand Canyon around 12:00, Yellowstone around 13:00 — so the Usage Report (average visitors per operating day by hour) produces a visible peak/valley curve.

---

## 5. Showcase Slot — Yosemite, 3 July 2026 @ 10:00

This slot is the fixed demo highlight: **86 confirmed visitors**, making Yosemite essentially at capacity (max_orders = 90) for that time window.

### Confirmed orders at this slot (7 orders = 86 visitors)

| Visitor ID | Name | Visitors | Type |
|---|---|---|---|
| `339182746` | Hila Rosen | 13 | family |
| `213761018` | Taiseer Ashkar | 9 | group |
| `305928471` | Yoni Cohen | 11 | family |
| `351749862` | Roni Klein | 14 | family |
| `358214967` | Adi Cohen | 11 | family |
| `204857361` | Dan Ben-David | 15 | family |
| `332158746` | Tamar Levi | 13 | family |
| | **Total** | **86** | |

### Waiting list for this slot (8 entries)

| Position | Visitor ID | Name | Party size |
|---|---|---|---|
| 1 | `987654321` | Bob Levi | 6 |
| 2 | `318472659` | Noa Friedman | 7 |
| 3 | `257391846` | Itai Mizrahi | 5 |
| 4 | `209384756` | Omer Katz | 8 |
| 5 | `341209856` | Shira Goldberg | 10 |
| 6 | `268741935` | Eitan Bar-On | 9 |
| 7 | `327654129` | Yael Avraham | 6 |
| 8 | `215386479` | Amit Peretz | 7 |

All 8 entries have status `WAITING` (not yet notified). Combined waitlist headcount: **58 visitors** — demonstrates that even if one confirmed slot cancels, promoting entry #1 (6 visitors) would not overflow the park.

---

## 6. Active Promotions

| ID | Park | Description | Discount | Period | Status | Submitted by |
|---|---|---|---|---|---|---|
| 1 | Yosemite | Summer Sale | **10%** | 28 Jun – 31 Jul 2026 | **ACTIVE** | manager1 |
| 2 | Yellowstone | Weekend Special | 5% | 1 Jul – 31 Jul 2026 | PENDING (awaiting dept-manager approval) | manager3 |
| 3 | Grand Canyon | Holiday Discount | 8% | 1 Jul – 31 Jul 2026 | REJECTED | manager2 |

The Yosemite Summer Sale is live on demo day (28 June falls within the active window). Bookings to Yosemite on or after 28 June will receive a 10% promotional discount stacked on the base price.

---

## 7. Pending Park Settings Requests

| Park | Requested by | Capacity | Max Orders | Price | Status |
|---|---|---|---|---|---|
| Yosemite | manager1 | 100 → 110 | 90 → 95 | $50 → $52 | pending |
| Yellowstone | manager3 | 120 → 130 | 108 → 115 | $40 → $42 | pending |
| Grand Canyon | manager2 | 150 → 160 | 135 → 140 | $45 → $47 | approved (20 Jun 2026) |

The two pending requests can be approved or rejected live during the demo to show the department-manager approval workflow.

---

## 8. Foreign Key Relationships

```
parks
 ├── users.park_id → parks.id               (ON DELETE SET NULL)
 ├── orders.park_id → parks.id              (ON DELETE CASCADE)
 ├── waiting_list.park_id → parks.id        (ON DELETE CASCADE)
 ├── promotions.park_id → parks.id          (ON DELETE CASCADE)
 └── park_settings_requests.park_id → parks.id (ON DELETE CASCADE)

visitors
 ├── subscribers.id_number → visitors.id_number (ON DELETE CASCADE)
 ├── guides.id_number → visitors.id_number      (ON DELETE CASCADE)
 ├── orders.visitor_id → visitors.id_number     (ON DELETE CASCADE)
 └── waiting_list.visitor_id → visitors.id_number (ON DELETE CASCADE)
```

Every subscriber and every guide must have a matching row in `visitors` first (the application's `ensureVisitorRow()` helper enforces this during walk-in check-in). Deleting a visitor cascades to their orders, waitlist entries, subscriber record, and guide record automatically.

---

## 9. Database Setup

```bash
# Load the complete schema + seed data from scratch:
mysql -u root -p < fresh_database.sql

# The script:
#   1. Drops and recreates park_db
#   2. Creates all 9 tables with constraints and indexes
#   3. Inserts all seed data in FK-safe order
#   4. Prints a verification count per table when complete
```

Connection string used by the server: `jdbc:mysql://localhost:3306/park_db`  
Character set: `utf8mb4` / collation `utf8mb4_unicode_ci`

---

*Generated for GoNature Group 9, Assignment 3 — demo date 28 June 2026.*
