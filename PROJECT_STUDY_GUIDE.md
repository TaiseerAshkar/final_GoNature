# GoNature — Project Study Guide

A plain-English reference for the live demo / Q&A. For every major file it answers three
questions: **why does this exist**, **what does it actually do**, and **how does it connect
to the rest of the system**. Read the "How the system talks to itself" section first — most
lecturer questions trace back to it.

---

## 0. The 30-second overview

GoNature is a **national-park reservation system** built as two separate Java programs that
talk over a network socket:

- **The Server** (`G9_Prototype_Server`) — owns the MySQL database. No GUI screen ever touches
  the database directly; everything goes through the server.
- **The Client** (`G9_Prototype_Client`) — a JavaFX desktop app. It draws all the screens and
  asks the server for data.

They never share memory. They only exchange **`Message` objects** over a TCP socket using a
framework called **OCSF** (Object Client-Server Framework). One server can serve many clients
at once.

```
[JavaFX screens] → ClientController → ChatClient ──socket──▶ EchoServer → DBController → MySQL
       ▲                                                          │
       └──────────────── Message reply ◀──────────────────────────┘
```

**Three roles of "person" exist:** *visitors* (incl. guides and subscribers, who log in with a
9-digit ID), and *staff* (park employee, park manager, department manager, service rep, who log
in with username + password). The server decides the role at login and the client opens the
matching home screen.

---

## 1. How the system talks to itself (the Message/DTO protocol)

**This is the single most important thing to understand, and the most likely thing to be asked.**

### The envelope: `Message`

Every single thing sent over the socket — in either direction — is one `Message` object. It has
just two fields:

```java
private String type;   // a command name, e.g. "BOOK_ORDER" or "LOGIN_SUCCESS"
private Object data;    // the payload — any Serializable object, or null
```

Think of `type` as the subject line and `data` as the attachment. The receiver looks at `type`
in a big `switch` statement to decide what to do, then casts `data` to the expected class.

### The payloads: DTOs (Data Transfer Objects)

The `data` field carries a **DTO** — a small "struct-like" class whose only job is to bundle
fields for the trip across the wire. Examples: `BookingRequest`, `BookingResult`,
`LoginResult`, `OrderDetail`, `Park`, `RegisterRequest`. Every DTO `implements Serializable`
so Java can flatten it to bytes and rebuild it on the other side.

**Why are the DTOs duplicated in both projects?** Because the client and server are separate
programs that don't share code. Each has its own copy of the `logic` package. For
serialization to work, both copies must describe the same fields and declare the same
`serialVersionUID`. (They do — see the consistency report.)

### The round-trip, step by step

The client uses a **blocking request/response** pattern. When a screen needs data:

1. The controller builds a `Message` and calls `ClientUI.chat.accept(msg)`.
2. `ClientController.accept()` calls `ChatClient.handleMessageFromClientUI()`, which sets a
   flag `awaitResponse = true`, sends the message, and **blocks in a loop** until the reply
   arrives (with a 15-second safety timeout).
3. The server's `EchoServer.handleMessageFromClient()` receives it, runs the right
   `DBController` method, and sends back a reply `Message`.
4. On the client, the OCSF reader thread runs `ChatClient.handleMessageFromServer()`, which
   stores the reply in a **public static field** (e.g. `ChatClient.lastBookingResult`) and
   clears `awaitResponse`.
5. The blocked controller wakes up and reads that static field.

So the controllers look like ordinary synchronous code (`send… then read the result`), even
though a socket round-trip happened in between. The static fields on `ChatClient` are the
"mailbox" where replies are dropped.

### Concrete example: clicking "Book a Visit"

1. **BookingController** validates the form, builds a `BookingRequest`, sends
   `Message("BOOK_ORDER", bookingRequest)`.
2. **EchoServer** catches `case "BOOK_ORDER"`, re-validates, grabs a per-park lock, and calls
   `DBController.checkAndBook()`.
3. **DBController** opens a transaction, does `SELECT … FOR UPDATE` on the park row, counts how
   many visitors already overlap that time window, and either inserts a `confirmed` order or
   reports the slot is full.
4. **EchoServer** sends back `Message("BOOKING_CONFIRMED", bookingResult)` (with the price
   quote) — or `BOOKING_FULL`, or `BOOKING_ERROR`. It also fires a confirmation email.
5. **ChatClient** stores it in `lastBookingResult`; **BookingController** wakes up and shows the
   confirmation screen.

If the slot was full, the client offers to join the waiting list, which sends
`JOIN_WAITING_LIST` instead.

---

## 2. Client networking & entry layer

### `ClientUI.java` — the application entry point
**Why it exists:** It's the JavaFX `Application` — the thing that actually starts when you run
the client jar.
**What it does:** On launch it shows a themed "Connect to GoNature" dialog asking for the
server's IP address (defaults to `localhost`). When you click Connect it creates the single
`ClientController` (port 5555) and opens the login screen. It also wires the window's X button
to disconnect cleanly.
**Connections:** Holds the app-wide `public static ClientController chat` that *every* screen
uses to talk to the server. Hands off to `LoginController` once connected.

### `ClientController.java` — the façade to the network
**Why it exists:** To give the GUI one simple object to talk to, hiding the OCSF plumbing.
**What it does:** Wraps a single `ChatClient`. Exposes three methods the screens use:
`accept(msg)` (send-and-wait), `sendLogout()` (fire-and-forget), and `disconnect()`. Implements
`ChatIF` so the network layer can print diagnostics without knowing about any UI widget.
**Connections:** Created by `ClientUI`; owns the `ChatClient`; called by every controller.

### `ChatClient.java` — the OCSF socket client
**Why it exists:** It's the actual network client (extends OCSF `AbstractClient`). It is the
"mailbox" where server replies land.
**What it does:** Two halves.
- **Sending half** — `handleMessageFromClientUI()` opens the connection, sets
  `awaitResponse = true`, sends the message, and busy-waits (polling every 100 ms, 15-second
  timeout) until the reply clears the flag. This is what makes the controllers feel synchronous.
- **Receiving half** — `handleMessageFromServer()` is a giant `switch` on the reply `type`. Each
  case casts `data` and stores it in a `public static` field (`lastLoginResult`,
  `lastMyOrders`, `lastBookingResult`, …). It clears `awaitResponse` **last**, after the field
  is written, to avoid a race where the waiting thread reads an empty mailbox.
**Connections:** The dozens of `last…` static fields are read by the controllers. There is also
a fire-and-forget `sendLogout()` that doesn't wait for a reply.

> **Likely question — "Why static fields instead of return values?"** Because the reply arrives
> on a *different thread* (the OCSF reader thread) than the one that sent the request (the GUI
> thread). The static field is the shared hand-off point between those two threads; the
> `awaitResponse` flag is the synchronisation signal.

---

## 3. The server core

### `EchoServer.java` — the message router (the heart of the server)
**Why it exists:** It's the OCSF server (extends `AbstractServer`). Every client request lands
here first. It is essentially one big switchboard.
**What it does:** `handleMessageFromClient()` is a ~40-case `switch` on `message.getType()`.
Each case: checks the payload type, does light validation, calls the matching `DBController`
method, and sends back a typed reply. It never contains SQL itself — it delegates all data work
to `DBController`. Examples of what it routes: login, booking, cancellation, check-in/exit,
all three reports, registration (visitor / subscriber / guide), park-settings approval,
promotions approval, live capacity lookups.
It also owns two pieces of **concurrency safety**:
- `activeSessions` — a thread-safe set that prevents the same account logging in twice at once.
- `parkLocks` — one lock object per park so two simultaneous bookings for the same park can't
  both grab the last slot (this is the first of two layers; the `SELECT … FOR UPDATE` in
  `checkAndBook` is the second).
On startup (`serverStarted()`) it confirms the DB connection and launches the three background
services (reminder, waitlist-timer, no-show). On client connect/disconnect it updates the
server UI's client list and frees the session slot.
**Connections:** Calls `DBController` for everything; calls `EmailService` to send mail; calls
`ServerPortFrameController` to write to the server's on-screen log; starts `ReminderService`,
`WaitlistTimerService`, `NoShowService`.

### `DBController.java` — all database access (the biggest, most important server file)
**Why it exists:** A single **singleton** that owns the one JDBC connection and contains *every*
SQL statement in the system. Centralising SQL here means the rest of the server never touches
the database directly.
**What it does:** Grouped by area —
- **Connection:** `connect()` reads `db.url`/`db.user` from `db.properties`; the password is
  supplied at runtime by the operator (never stored on disk).
- **Booking:** `getAvailableSpots()` (capacity check that respects visit-duration overlap),
  `checkAndBook()` (atomic transactional booking with row locking), `addToWaitingList()`.
- **Orders:** `getOrdersByVisitor()`, `getOrderById()`, `cancelOrder()`, `updateOrder()`,
  `getAllLiveOrders()`, `getTodayOrders()`.
- **Auth:** `loginVisitor()` (checks guides table first, then visitors; a LEFT JOIN to
  subscribers decides VISITOR vs SUBSCRIBER role), `loginUser()` (staff; maps the DB's
  lowercase role to the app's `SCREAMING_SNAKE_CASE` constant).
- **Entry control:** `checkInVisitor()` (rejects double-entry, matches a pre-booked order if one
  exists, otherwise treats it as a walk-in), `registerExit()`, `getCurrentOccupancy()`.
- **Pricing:** `calculateEntryPrice()` — the authoritative price engine (see box below).
- **Reports:** `getVisitorReport()`, `getCancelReport()`, `getCancellationDistribution()`
  (the day-of-week chart), `getUsageReport()`.
- **Waiting-list lifecycle:** `promoteFromWaitingList()`, `confirmPromotedVisit()`,
  `expireWaitlistEntry()`, plus the no-show sweep `markNoShowsAndPromote()`.
- **Registration:** `registerVisitor()` (also inserts a subscriber row when asked),
  `registerGuide()`, and the shared helper `ensureVisitorRow()` that creates a placeholder
  `visitors` row so foreign keys are satisfied for new guides and anonymous walk-ins.
- **Approval workflows:** park-settings requests and promotions (submit / list / approve /
  reject).
**Connections:** Called by `EchoServer` (and by the three background services). Talks to MySQL
via JDBC. Returns DTOs that get wrapped in `Message` replies.

> **The pricing rules (`calculateEntryPrice`) — memorise these, they're demo-critical:**
> - **Pre-booked GROUP led by a guide:** guide enters **free**; the other `(n−1)` pay a 25%
>   group discount **and** a 12% advance discount, stacked → `0.75 × 0.88 = 0.66` (**34% off**).
> - **Walk-in GROUP led by a guide:** the guide **pays** (included in the count); everyone gets
>   10% off → `n × fullPrice × 0.90`.
> - **Pre-booked SOLO/FAMILY:** 15% off (`× 0.85`).
> - **Walk-in SOLO/FAMILY:** full price (`× 1.00`).
> - **Subscriber** (Family Member Club): stacks an extra 10% (`× 0.90`) on the non-guide cases.
> - **Promotion:** the best active promotion for that park/date is applied **last**,
>   multiplicatively. Promotions never stack with each other (largest wins).

---

## 4. The background automation (server-side, runs on timers)

All three are **singletons** started once in `EchoServer.serverStarted()`. Each runs on its own
**daemon thread** (so it never blocks shutdown) and writes activity to the server's on-screen
"simulation log".

### `ReminderService.java` — day-before reminders + auto-cancel
**Why:** To remind visitors the day before, and to free slots that nobody confirms.
**What:** On server start (`runStartupCheck`) it finds all of *tomorrow's* orders, emails each a
reminder, shows a one-off "Reminder Sent" popup on the server, and schedules an **auto-cancel
check 2 hours later**. When that fires, any order the visitor didn't explicitly confirm gets
cancelled (and that freed slot is offered to the waiting list). `confirmVisit()` records a
visitor's confirmation so their order is skipped by the auto-cancel.
**Connects to:** `DBController.getTomorrowOrdersForReminder()` and `cancelOrder()`;
`EmailService`. *(The startup popup window the demo shows is this service.)*

### `WaitlistTimerService.java` — the 1-hour waiting-list confirmation window
**Why:** When a slot opens and a waiting visitor is promoted, they get **1 hour** to confirm or
they lose it.
**What:** Two safety nets that both funnel through the idempotent
`DBController.expireWaitlistEntry()`: (a) a precise one-shot timer set exactly 1 hour after a
promotion, and (b) a recurring 5-minute sweep that catches anything the one-shot missed (e.g.
after a server restart). On a genuine expiry it cancels the held order, emails the visitor,
shows a server popup, and promotes the next person in line.
**Connects to:** `DBController.promoteFromWaitingList()` schedules it; it calls
`getExpiredNotifiedEntries()` / `expireWaitlistEntry()`; `EmailService`.

### `NoShowService.java` — no-show detection
**Why:** A confirmed visitor who never shows up shouldn't hold a slot forever.
**What:** Every 5 minutes it asks the DB for today's confirmed orders whose visit time + a
15-minute grace period has passed with no check-in, marks each `no_show`, frees the slot to the
waiting list, and shows a server popup per detection.
**Connects to:** `DBController.markNoShowsAndPromote(15)`.

### `EmailService.java` — real outgoing email
**Why:** To send genuine confirmation / reminder / cancellation emails.
**What:** A utility class (no instances). Reads Gmail SMTP credentials from `db.properties`
(`mail.sender`, `mail.password` — a Gmail *App Password*). `sendEmail()` hands the actual send
to a small daemon thread pool so the SMTP round-trip never blocks a request handler. It uses
SSL on port 465 and forces TLS 1.2/1.3 (needed on Java 21). Failures are logged and swallowed —
**a failed email never breaks the booking flow.**
**Connects to:** Called by `EchoServer` and all three background services.

---

## 5. The client screens (controllers)

Each screen is a JavaFX **FXML** file (the layout) plus a **controller** (the behaviour). All
controllers reach the server the same way: build a `Message`, call `ClientUI.chat.accept(…)`,
read the result from a `ChatClient.last…` field.

### `LoginController.java` — the front door & role router
**Why:** One login form for everyone; it figures out who you are and opens the right home
screen.
**What:** Two tabs — *Visitor/Guide* (9-digit ID, blank password → sends `LOGIN_VISITOR`) and
*Staff* (username + password → sends `LOGIN_USER`). On success it stores the user in
`UserSession` and, based on the returned role, opens the matching home screen:
- VISITOR / GUIDE / SUBSCRIBER → `VisitorController`
- PARK_EMPLOYEE / PARK_MANAGER → `EmployeeController`
- DEPARTMENT_MANAGER → `AcademicFrameController`
- SERVICE_REP → `ServiceRepController`

It also offers "Create an Account" (→ `RegisterController`) and a Help dialog.
**Connects to:** `ChatClient` (login), `UserSession`, and every home-screen controller.

### `BookingController.java` — make a reservation
**Why:** The "Book a Visit" form.
**What:** Loads parks (`GET_PARKS`), live-checks availability as you pick a slot
(`GET_AVAILABLE_SPOTS`), validates the visit type (SOLO = 1, FAMILY = 2–15, GROUP = guides only,
2–16 incl. the free guide), and submits `BOOK_ORDER`. If the slot is full it offers
`JOIN_WAITING_LIST`. Its inline hint label shows the live discount text (e.g. "guide enters free
· 25%+12% advance discount"). On success it opens the confirmation screen with the price quote.
**Connects to:** Server booking handlers; `BookingConfirmationController`.

### `VisitorController.java` — the visitor/guide/subscriber home
**Why:** The landing screen after a visitor logs in.
**What:** Hub for "Book a Visit", "View My Reservations" (`GET_MY_ORDERS`), and a notifications
panel. It also handles **waiting-list promotion confirmations**: it polls
`GET_PENDING_WAITLIST_ORDERS` and lets the visitor confirm a held spot within the 1-hour window
(`CONFIRM_VISIT`). Guides see GROUP-booking help; regular visitors see FAMILY help.
**Connects to:** `BookingController`, `MyOrdersController`, server order/confirm handlers.

### `EntryControlController.java` — the gate console (check-in / exit)
**Why:** Used by park staff at the entrance to admit and release visitors.
**What:** Two tabs. **Check-In** takes a visitor ID + park; it first tries `FIND_TODAY_BOOKING`
to match a pre-booked order, otherwise it submits a walk-in `CHECK_IN_VISITOR` (with visitor
count and type). The server prices the entry and (for walk-ins) creates a completed order on the
spot — even creating a placeholder visitor row for a never-registered walk-in ID. **Register
Exit** sends `REGISTER_EXIT` to stamp the departure time.
**Connects to:** Server entry handlers (`checkInVisitor`, `registerExit`); reachable from the
employee and manager home screens.

### `ServiceRepController.java` — the service-representative desk
**Why:** A counter clerk who acts on visitors' behalf.
**What:** Buttons for: Load All Orders (`GET_LIVE_ORDERS`), update an order (`UPDATE_ORDER`),
**Register New Visitor** (opens `RegisterController`), **Register New Guide** (`REGISTER_GUIDE`,
pending manager approval), **Register Subscriber** (`REGISTER_SUBSCRIBER` — this is where Family
Member Club sign-up lives now, **not** in visitor self-service), and open the reports dashboard.
**Connects to:** Server registration/order handlers; `RegisterController`; `ReportsController`.

### `EmployeeController.java` — park employee / park manager home
**Why:** One screen, two role variants.
**What:** Both roles get Today's Reservations (`GET_TODAY_ORDERS`), Entry Control, Manage Waiting
List, and live occupancy (`GET_PARK_OCCUPANCY`). **Park managers** additionally get the Reports
dashboard (locked to their park), park-settings change requests, and promotions. The Help text
differs per role.
**Connects to:** `EntryControlController`, `WaitingListController`, `TodayReservationsController`,
`ReportsController`, `ParkSettingsController`, `PromotionsController`.

### `AcademicFrameController.java` — the department-manager home
**Why:** The top-level manager who approves things across *all* parks. (The "Academic" name is
historical; it's the Department Manager screen.)
**What:** Load all orders system-wide (`GET_LIVE_ORDERS`), update orders, and three approval
queues: guides (`GET_ALL_GUIDES` → `APPROVE_GUIDE`/`REJECT_GUIDE`), park-settings requests
(`GET_PENDING_SETTINGS` → approve/reject), and promotions (`GET_PENDING_PROMOTIONS` →
approve/reject). It also opens the full Reports dashboard.
**Connects to:** Server approval handlers; `ReportsController`.

### `ReportsController.java` — the analytics dashboard
**Why:** Shows the three required management reports.
**What:** A period selector plus three tabs:
- **Visitor Report** (`GET_VISITOR_REPORT`) — bar chart of daily visitors split by type.
- **Cancellation Report** (`GET_CANCEL_REPORT` + `GET_CANCEL_DISTRIBUTION`) — a table of all
  cancelled/no-show orders **plus** a day-of-week distribution bar chart with the average
  cancellations per weekday.
- **Usage Report** (`GET_USAGE_REPORT`) — line chart of hourly capacity utilisation.
A park filter narrows any report (locked to one park for park managers, free choice for the
department manager).
**Connects to:** Server report handlers; opened from the service-rep, manager, and
department-manager screens.

### Supporting controllers (briefly)
- `RegisterController` — the create-a-visitor form (plain visitors only; subscriber sign-up
  moved to the service rep).
- `BookingConfirmationController` — the post-booking confirmation/receipt screen with the price.
- `MyOrdersController` — list/cancel/confirm a visitor's own orders.
- `TodayReservationsController`, `WaitingListController` — read-only park lists for staff.
- `ParkSettingsController`, `PromotionsController` — park-manager forms that *submit requests*
  for department-manager approval (they don't change anything live).
- `UserSession` — a client-only singleton holding the logged-in user (role, name, park). It is
  never sent over the socket, which is why it has no server-side copy.
- `ThemeManager`, `HelpDialog`, `Toast`, `Animations`, `Icons`, `UiCells`, etc. — pure UI
  helpers (dark mode, the shared Help popup, toasts, styling).

---

## 6. The database, table by table

The schema lives in `G9_Assignment_DB.sql` (database name `park_db`). Nine tables.

### `parks`
The three national parks. Columns: `id`, `name`, `capacity` (physical max inside at once),
`max_orders` (the pre-booking cap, deliberately set below capacity to leave room for walk-ins),
`visit_duration_hours`, `full_price`. **Referenced by** almost everything via foreign key.

### `users`
Staff accounts (NOT visitors). Columns: `username`, `password`, name, `email`, `role`
(lowercase ENUM: `department_manager` / `park_manager` / `employee` / `service`), and a nullable
`park_id` (which park they belong to; null for department manager and service rep). `park_id` →
`parks.id`.

### `visitors`
The base "person" table, keyed by the 9-digit `id_number`. Columns: `id_number` (PK),
name, `email`, `phone`. **Every order and every guide/subscriber points back here** — it's the
parent of the foreign keys, which is why the server auto-creates a placeholder row for new
guides and anonymous walk-ins.

### `subscribers`
Family Member Club members. Columns: auto `subscriber_id` (PK), `id_number` (unique, → visitors),
name, `email`, `phone`, `credit_card` (nullable — null/blank means "pays cash"), `family_size`.
A visitor with a row here logs in with the **SUBSCRIBER** role and gets the extra 10% discount.
`id_number` → `visitors.id_number` (cascade).

### `guides`
Approved tour guides. Columns: `id_number` (PK, → visitors), name, contact, `is_approved`
(0 = pending, 1 = approved). A guide logs in with the **GUIDE** role and is the only kind of
person allowed to lead a GROUP booking. `id_number` → `visitors.id_number` (cascade).

### `orders`
The central reservations table. Columns: `id` (PK), `visitor_id` (→ visitors), `park_id`
(→ parks), `visit_date`, `visit_time`, `num_visitors`, `order_type` (stored lowercase
`individual`/`family`/`group`), `status` (ENUM: `pending` / `confirmed` / `cancelled` /
`completed` / `no_show`), `email`, `entry_time`, `exit_time`, `created_at`. **Status is the
lifecycle of a booking** — pending→confirmed→completed (checked in & out), or →cancelled /
→no_show. Walk-ins are inserted directly as `completed` with an `entry_time`.

### `waiting_list`
People queued for a full slot. Columns mirror an order plus the promotion machinery: `status`
(`WAITING` → `NOTIFIED`), `order_id` (the held order once promoted), `notified_at`,
`promoted_at`, `confirmation_deadline` (the 1-hour clock). When a slot frees up, the earliest
fitting `WAITING` row is promoted: a held `pending` order is created and this row flips to
`NOTIFIED`. `park_id` → parks, `visitor_id` → visitors.

### `promotions`
Park discount campaigns. Columns: `id`, `park_id` (→ parks), `description`, `discount_percent`
(0–50), `start_date`, `end_date`, `status` (ENUM: `PENDING` / `ACTIVE` / `REJECTED`),
`submitted_by`. A park manager submits one (PENDING); the department manager approves it
(ACTIVE). Only ACTIVE promotions inside their date window affect pricing.

### `park_settings_requests`
The approval queue for park-setting changes. Columns: `id`, `park_id` (→ parks), `requested_by`,
the four proposed values (`capacity`, `max_orders`, `visit_duration_hours`, `full_price`),
`status` (ENUM: `pending` / `approved` / `rejected`), `requested_at`, `reviewed_at`. A park
manager submits a change; approving it copies the values into the live `parks` row in one
transaction.

### How they connect (foreign keys)
```
parks ──┬─< orders >── visitors ──┬─< subscribers
        ├─< waiting_list >────────┤
        ├─< promotions            └─< guides
        ├─< park_settings_requests
        └─< users (park_id, nullable)
```
`visitors` is the hub for people; `parks` is the hub for places; `orders` ties a person to a
place at a time. Most foreign keys cascade on delete/update.

---

## 7. Quick answers to likely Q&A

- **"How do client and server communicate?"** One `Message` object (a `type` string + an
  `Object` payload) over a TCP socket, using the OCSF framework. The server `switch`es on the
  type. See §1.
- **"Why duplicate the DTO classes?"** Two independent programs can't share memory; each needs
  its own copy of the serializable classes, with matching fields and `serialVersionUID`.
- **"How do you prevent double-booking the last slot?"** Two layers: a per-park JVM lock in
  `EchoServer`, plus `SELECT … FOR UPDATE` inside a DB transaction in `checkAndBook`.
- **"What stops two people using one account at once?"** The `activeSessions` set in
  `EchoServer` rejects a second concurrent login of the same ID/username.
- **"Where is the pricing decided?"** Only on the server, in `DBController.calculateEntryPrice`.
  The client never computes prices; it just displays what the server returns.
- **"What are the background services?"** Reminder (day-before + 2-hour auto-cancel), Waitlist
  timer (1-hour confirmation window), No-show (15-minute grace sweep). All daemon-thread
  singletons started at server boot.
- **"Who can book a GROUP?"** Only a registered guide. Regular visitors use FAMILY.
- **"Where does subscriber sign-up happen?"** At the service-rep desk
  (`REGISTER_SUBSCRIBER`), not in visitor self-registration.
