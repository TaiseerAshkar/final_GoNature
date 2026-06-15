package Server;
import logic.BookingRequest;
import logic.BookingResult;
import logic.CancelResult;
import logic.EntryCheckRequest;
import logic.EntryResult;
import logic.ExitRequest;
import logic.LoginResult;
import logic.OrderDetail;
import logic.Park;
import logic.ReportCancelRow;
import logic.ReportUsageRow;
import logic.ReportVisitorRow;
import logic.GuideDetail;
import logic.ParkSettingsRequest;
import logic.Promotion;
import logic.RegisterGuideRequest;
import logic.RegisterRequest;
import logic.RegisterResult;
import java.io.InputStream;
import java.sql.*;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

/**
 * singleton data-access layer for the GoNature server.
 *
 * <p>all SQL goes through this class. connection opened once by {@link EchoServer#serverStarted()}.
 * URL and username come from {@code /db.properties}; password is supplied at runtime via
 * {@link #setPassword(String)}.
 *
 * <p>method categories:
 * <ul>
 *   <li>parks: {@link #getParks()}</li>
 *   <li>booking: {@link #getAvailableSpots}, {@link #createOrder}, {@link #addToWaitingList}</li>
 *   <li>orders: {@link #getOrdersByVisitor}, {@link #cancelOrder}, {@link #updateOrder}</li>
 *   <li>auth: {@link #loginVisitor}, {@link #loginUser}</li>
 *   <li>entry control: {@link #checkInVisitor}, {@link #registerExit}</li>
 *   <li>reports: {@link #getVisitorReport}, {@link #getCancelReport}, {@link #getUsageReport}</li>
 *   <li>reminders: {@link #getTomorrowOrdersForReminder()}</li>
 * </ul>
 */
public class DBController {

    /** the sole instance — created lazily on first call to {@link #getInstance()}. */
    private static DBController instance = null;

    /** active JDBC connection. {@code null} until {@link #connect()} succeeds. */
    private Connection connection;

    /**
     * set by {@link #loginUser} before each {@code return null} so the caller can
     * show the specific message ("ID not registered" vs "Incorrect password").
     */
    volatile String loginUserFailReason = null;

    /** JDBC URL loaded from {@code db.properties}; falls back to the localhost default if absent. */
    private static String dbUrl  = "jdbc:mysql://localhost/park_db?serverTimezone=UTC";

    /** database username loaded from {@code db.properties}; falls back to {@code "root"} if absent. */
    private static String dbUser = "root";

    /** database password — supplied at runtime via {@link #setPassword(String)}; never stored on disk. */
    private static String dbPass = "";

    /**
     * stores the password entered by the operator at startup. must be called before {@link #connect()}.
     *
     * @param pass the plain-text password for the database user
     */
    public static void setPassword(String pass) { dbPass = pass; }

    /** use {@link #getInstance()}, not new. */
    private DBController() {}

    /**
     * returns (or creates) the singleton.
     *
     * @return the shared {@code DBController} instance
     */
    public static DBController getInstance() {
        if (instance == null) instance = new DBController();
        return instance;
    }

    /**
     * loads {@code db.url} and {@code db.user} from the {@code /db.properties} classpath resource.
     * if the file is absent the hardcoded defaults are kept and a warning is printed.
     */
    private void loadConfig() {
        try (InputStream is = getClass().getResourceAsStream("/db.properties")) {
            if (is == null) {
                System.out.println("db.properties not found — using default DB settings.");
                return;
            }
            Properties props = new Properties();
            props.load(is);
            if (props.containsKey("db.url"))  dbUrl  = props.getProperty("db.url");
            if (props.containsKey("db.user")) dbUser = props.getProperty("db.user");
            System.out.println("DB config loaded from db.properties.");
        } catch (Exception e) {
            System.out.println("Failed to load db.properties: " + e.getMessage() +
                " — using default DB settings.");
        }
    }

    /**
     * loads config from {@code db.properties} and opens a JDBC connection.
     *
     * @return {@code true} if the connection was established; {@code false} on any error
     */
    public boolean connect() {
        loadConfig();
        try {
            Class.forName("com.mysql.cj.jdbc.Driver");
            connection = DriverManager.getConnection(dbUrl, dbUser, dbPass);
            System.out.println("DB connected!");
            return true;
        } catch (Exception e) {
            System.out.println("DB connection failed: " + e.getMessage());
            return false;
        }
    }

    // ── Park settings ────────────────────────────────────────────────────────

    /**
     * fetches a single park row by primary key.
     *
     * @param parkId the park's primary-key ID
     * @return a {@link Park} snapshot, or {@code null} if not found or on DB error
     */
    public Park getParkById(int parkId) {
        if (connection == null) return null;
        try (PreparedStatement st = connection.prepareStatement(
                "SELECT id, name, capacity, max_orders, visit_duration_hours, full_price " +
                "FROM parks WHERE id = ?")) {
            st.setInt(1, parkId);
            try (ResultSet rs = st.executeQuery()) {
                if (rs.next()) {
                    return new Park(
                        rs.getInt("id"),
                        rs.getString("name"),
                        rs.getInt("capacity"),
                        rs.getInt("max_orders"),
                        rs.getInt("visit_duration_hours"),
                        rs.getDouble("full_price")
                    );
                }
            }
        } catch (SQLException e) {
            System.out.println("getParkById error: " + e.getMessage());
        }
        return null;
    }

    /**
     * updates the four editable park parameters. the park name and ID are not changed here.
     * capacity is assumed valid (caller must enforce maxOrders ≤ capacity).
     *
     * @param parkId             the park's primary-key ID
     * @param capacity           the new maximum simultaneous-visitor count
     * @param maxOrders          the new maximum concurrent bookable orders
     * @param visitDurationHours the new default visit window in hours
     * @param fullPrice          the new full per-person admission price
     * @return {@code true} if exactly one row was updated; {@code false} otherwise
     */
    public boolean updateParkSettings(int parkId, int capacity, int maxOrders,
                                      int visitDurationHours, double fullPrice) {
        if (connection == null) return false;
        try {
            PreparedStatement st = connection.prepareStatement(
                "UPDATE parks SET capacity=?, max_orders=?, " +
                "visit_duration_hours=?, full_price=? WHERE id=?");
            st.setInt(1,    capacity);
            st.setInt(2,    maxOrders);
            st.setInt(3,    visitDurationHours);
            st.setDouble(4, fullPrice);
            st.setInt(5,    parkId);
            return st.executeUpdate() == 1;
        } catch (SQLException e) {
            System.out.println("updateParkSettings error: " + e.getMessage());
            return false;
        }
    }

    // ── Parks ────────────────────────────────────────────────────────────────

    /**
     * retrieves all parks ordered alphabetically by name.
     *
     * @return a list of {@link Park} objects; empty if the table has no rows or on error
     */
    public List<Park> getParks() {
        List<Park> parks = new ArrayList<>();
        if (connection == null) return parks;
        try {
            PreparedStatement st = connection.prepareStatement(
                "SELECT id, name, capacity, max_orders, visit_duration_hours, full_price " +
                "FROM parks ORDER BY name");
            ResultSet rs = st.executeQuery();
            while (rs.next()) {
                parks.add(new Park(
                    rs.getInt("id"),
                    rs.getString("name"),
                    rs.getInt("capacity"),
                    rs.getInt("max_orders"),
                    rs.getInt("visit_duration_hours"),
                    rs.getDouble("full_price")
                ));
            }
        } catch (SQLException e) {
            System.out.println("getParks error: " + e.getMessage());
        }
        return parks;
    }

    // ── Booking ──────────────────────────────────────────────────────────────

    /**
     * returns how many visitor slots are still free for the given park, date, and time.
     * accounts for visit-duration overlap: two bookings conflict when their duration windows intersect.
     *
     * @param parkId    the park's primary-key ID
     * @param visitDate the requested visit date in {@code yyyy-MM-dd} format
     * @param visitTime the requested visit time in {@code HH:mm} format
     * @return the number of free visitor slots, or {@code -1} on DB error or unknown park
     */
    public int getAvailableSpots(int parkId, String visitDate, String visitTime) {
        System.out.printf("[getAvailableSpots] parkId=%d  date=%s  time=%s%n",
            parkId, visitDate, visitTime);
        if (connection == null) {
            System.out.println("[getAvailableSpots] ERROR — connection is null");
            return -1;
        }
        try (PreparedStatement st = connection.prepareStatement(
                "SELECT p.capacity - COALESCE(SUM(o.num_visitors), 0) AS available " +
                "FROM parks p " +
                "LEFT JOIN orders o " +
                "       ON  o.park_id = p.id " +
                "       AND o.visit_date = ? " +
                "       AND o.status NOT IN ('no_show', 'cancelled') " +
                "       AND TIME(o.visit_time) < ADDTIME(TIME(?), SEC_TO_TIME(p.visit_duration_hours * 3600)) " +
                "       AND ADDTIME(TIME(o.visit_time), SEC_TO_TIME(p.visit_duration_hours * 3600)) > TIME(?) " +
                "WHERE p.id = ? " +
                "GROUP BY p.id, p.capacity")) {
            st.setString(1, visitDate);
            st.setString(2, visitTime);
            st.setString(3, visitTime);
            st.setInt(4, parkId);
            try (ResultSet rs = st.executeQuery()) {
                if (rs.next()) {
                    int result = rs.getInt("available");
                    System.out.printf("[getAvailableSpots] result=%d%n", result);
                    // overlapping bookings (walk-ins, waiting-list promotions, multi-hour visit
                    // windows) can push the occupied sum past capacity, making this negative.
                    // negative means "overbooked", NOT "error" — clamp to 0 free spots so the
                    // callers' -1 error sentinel stays unambiguous and full slots route to the
                    // waiting-list offer instead of a bogus "could not check capacity" error.
                    if (result < 0) {
                        System.out.printf("[getAvailableSpots] slot overbooked by %d — clamping to 0%n",
                            -result);
                        result = 0;
                    }
                    return result;
                }
            }
            // No rows means park ID doesn't exist in the parks table
            System.out.printf("[getAvailableSpots] WARN — no row for parkId=%d (park not found)%n", parkId);
            return -1;
        } catch (SQLException e) {
            System.out.println("[getAvailableSpots] SQL ERROR: " + e.getMessage());
            return -1;
        }
    }

    /**
     * counts how many active orders overlap the requested park/date/time window.
     * uses the same visit-duration overlap logic as {@link #getAvailableSpots}; orders with
     * status {@code 'no_show'} or {@code 'cancelled'} are excluded since they no longer hold a slot.
     * used to enforce the park's {@code max_orders} cap before confirming a new booking.
     *
     * @param parkId    the park's primary-key ID
     * @param visitDate the requested visit date in {@code yyyy-MM-dd} format
     * @param visitTime the requested visit time in {@code HH:mm} format
     * @return the number of active overlapping orders, or {@code -1} on DB error
     */
    public int countActiveOrders(int parkId, String visitDate, String visitTime) {
        if (connection == null) return -1;
        try {
            PreparedStatement st = connection.prepareStatement(
                "SELECT COUNT(*) AS cnt " +
                "FROM parks p " +
                "JOIN orders o " +
                "      ON  o.park_id = p.id " +
                "      AND o.visit_date = ? " +
                "      AND o.status NOT IN ('no_show', 'cancelled') " +
                "      AND TIME(o.visit_time) < ADDTIME(TIME(?), SEC_TO_TIME(p.visit_duration_hours * 3600)) " +
                "      AND ADDTIME(TIME(o.visit_time), SEC_TO_TIME(p.visit_duration_hours * 3600)) > TIME(?) " +
                "WHERE p.id = ?");
            st.setString(1, visitDate);
            st.setString(2, visitTime);
            st.setString(3, visitTime);
            st.setInt(4, parkId);
            ResultSet rs = st.executeQuery();
            if (rs.next()) return rs.getInt("cnt");
            return 0;
        } catch (SQLException e) {
            System.out.println("countActiveOrders error: " + e.getMessage());
            return -1;
        }
    }

    /**
     * Atomically checks slot availability and inserts a confirmed order in one transaction.
     *
     * <p>Uses {@code SELECT ... FOR UPDATE} on the parks row so that any concurrent call
     * for the same park blocks at the DB level until this transaction commits or rolls back.
     * Together with the per-park JVM lock in {@link EchoServer}, this eliminates the
     * check-then-act race that could otherwise allow two visitors to both see one free spot
     * and both succeed in booking it.
     *
     * <p>Return value semantics:
     * <ul>
     *   <li>{@code >= 1} — booking confirmed; value is the new order ID</li>
     *   <li>{@code 0}    — slot is full (capacity or max-orders cap reached)</li>
     *   <li>{@code -1}   — DB error or park not found</li>
     * </ul>
     *
     * @param req the validated booking request
     * @return orderId on success, {@code 0} if full, {@code -1} on error
     * @throws SQLException if a non-recoverable SQL error occurs (caller should surface it)
     */
    public int checkAndBook(BookingRequest req) throws SQLException {
        if (connection == null) return -1;

        boolean oldAutoCommit = connection.getAutoCommit();
        connection.setAutoCommit(false);
        try {
            // Lock the parks row for this park ID.  Any concurrent checkAndBook for the
            // same park blocks here until we commit or roll back, so both threads cannot
            // simultaneously read "1 spot free" and both insert an order.
            int capacity;
            int maxOrders;
            int durationHours;
            try (PreparedStatement st = connection.prepareStatement(
                    "SELECT capacity, max_orders, visit_duration_hours " +
                    "FROM parks WHERE id = ? FOR UPDATE")) {
                st.setInt(1, req.getParkId());
                try (ResultSet rs = st.executeQuery()) {
                    if (!rs.next()) {
                        connection.rollback();
                        System.out.printf("[checkAndBook] park %d not found%n", req.getParkId());
                        return -1;
                    }
                    capacity      = rs.getInt("capacity");
                    maxOrders     = rs.getInt("max_orders");
                    durationHours = rs.getInt("visit_duration_hours");
                }
            }

            // Count visitor slots already occupied in the overlapping time window.
            // This read is consistent within our transaction and sees all previously
            // committed rows — including any that a just-committed concurrent booking added.
            int occupied = 0;
            try (PreparedStatement st = connection.prepareStatement(
                    "SELECT COALESCE(SUM(num_visitors), 0) AS occupied " +
                    "FROM orders " +
                    "WHERE park_id = ? " +
                    "  AND visit_date = ? " +
                    "  AND status NOT IN ('no_show', 'cancelled') " +
                    "  AND TIME(visit_time) < ADDTIME(TIME(?), SEC_TO_TIME(? * 3600)) " +
                    "  AND ADDTIME(TIME(visit_time), SEC_TO_TIME(? * 3600)) > TIME(?)")) {
                st.setInt(1,    req.getParkId());
                st.setString(2, req.getVisitDate());
                st.setString(3, req.getVisitTime());
                st.setInt(4,    durationHours);
                st.setInt(5,    durationHours);
                st.setString(6, req.getVisitTime());
                try (ResultSet rs = st.executeQuery()) {
                    if (rs.next()) occupied = rs.getInt("occupied");
                }
            }

            int available = Math.max(0, capacity - occupied);
            System.out.printf("[checkAndBook] parkId=%d date=%s time=%s"
                + " capacity=%d occupied=%d available=%d requested=%d%n",
                req.getParkId(), req.getVisitDate(), req.getVisitTime(),
                capacity, occupied, available, req.getNumVisitors());

            if (available < req.getNumVisitors()) {
                connection.rollback();
                return 0;
            }

            // Also enforce the max-orders (distinct bookings) cap for this slot.
            int activeOrders = 0;
            try (PreparedStatement st = connection.prepareStatement(
                    "SELECT COUNT(*) AS cnt FROM orders " +
                    "WHERE park_id = ? " +
                    "  AND visit_date = ? " +
                    "  AND status NOT IN ('no_show', 'cancelled') " +
                    "  AND TIME(visit_time) < ADDTIME(TIME(?), SEC_TO_TIME(? * 3600)) " +
                    "  AND ADDTIME(TIME(visit_time), SEC_TO_TIME(? * 3600)) > TIME(?)")) {
                st.setInt(1,    req.getParkId());
                st.setString(2, req.getVisitDate());
                st.setString(3, req.getVisitTime());
                st.setInt(4,    durationHours);
                st.setInt(5,    durationHours);
                st.setString(6, req.getVisitTime());
                try (ResultSet rs = st.executeQuery()) {
                    if (rs.next()) activeOrders = rs.getInt("cnt");
                }
            }

            if (activeOrders >= maxOrders) {
                connection.rollback();
                System.out.printf("[checkAndBook] max_orders cap reached (%d)%n", maxOrders);
                return 0;
            }

            // Both checks passed — insert the confirmed order.
            String orderTypeValue = toDbOrderType(req.getOrderType());
            int orderId;
            try (PreparedStatement st = connection.prepareStatement(
                    "INSERT INTO orders " +
                    "(visitor_id, park_id, visit_date, visit_time, num_visitors, order_type, status, email) " +
                    "VALUES (?, ?, ?, ?, ?, ?, 'confirmed', ?)",
                    Statement.RETURN_GENERATED_KEYS)) {
                st.setString(1, req.getVisitorId());
                st.setInt(2,    req.getParkId());
                st.setString(3, req.getVisitDate());
                st.setString(4, req.getVisitTime());
                st.setInt(5,    req.getNumVisitors());
                st.setString(6, orderTypeValue);
                st.setString(7, req.getEmail());
                st.executeUpdate();
                try (ResultSet keys = st.getGeneratedKeys()) {
                    if (!keys.next()) {
                        connection.rollback();
                        return -1;
                    }
                    orderId = keys.getInt(1);
                }
            }

            connection.commit();
            System.out.printf("[checkAndBook] committed — orderId=%d parkId=%d%n",
                orderId, req.getParkId());
            return orderId;

        } catch (SQLException e) {
            System.out.printf("[checkAndBook] SQL ERROR — rolling back: %s%n", e.getMessage());
            try { connection.rollback(); } catch (SQLException ignored) {}
            throw e;
        } finally {
            try { connection.setAutoCommit(oldAutoCommit); } catch (SQLException ignored) {}
        }
    }

    /**
     * inserts a new order row with status {@code 'confirmed'}.
     * capacity was already verified by the caller, so the booking is confirmed immediately.
     *
     * @param req the validated booking request
     * @return the auto-generated order ID, or {@code -1} if no key was returned
     * @throws RuntimeException wrapping the {@link SQLException} so the caller can surface the DB message
     */
    public int createOrder(BookingRequest req) {
        if (connection == null) return -1;
        String orderTypeValue = toDbOrderType(req.getOrderType());
        System.out.printf(
            "[SQL INSERT] orders → visitor_id='%s' park_id=%d date='%s' time='%s'" +
            " num_visitors=%d order_type='%s' status='confirmed' email='%s'%n",
            req.getVisitorId(), req.getParkId(), req.getVisitDate(), req.getVisitTime(),
            req.getNumVisitors(), orderTypeValue, req.getEmail());
        try {
            PreparedStatement st = connection.prepareStatement(
                "INSERT INTO orders " +
                "(visitor_id, park_id, visit_date, visit_time, num_visitors, order_type, status, email) " +
                "VALUES (?, ?, ?, ?, ?, ?, 'confirmed', ?)",
                Statement.RETURN_GENERATED_KEYS);
            st.setString(1, req.getVisitorId());
            st.setInt(2,    req.getParkId());
            st.setString(3, req.getVisitDate());
            st.setString(4, req.getVisitTime());
            st.setInt(5,    req.getNumVisitors());
            st.setString(6, orderTypeValue);
            st.setString(7, req.getEmail());
            st.executeUpdate();
            ResultSet keys = st.getGeneratedKeys();
            if (keys.next()) return keys.getInt(1);
        } catch (SQLException e) {
            System.out.println("createOrder error: " + e.getMessage());
            throw new RuntimeException("Database error: " + e.getMessage(), e);
        }
        return -1;
    }

    /**
     * adds a visitor to the {@code waiting_list} table for a fully-booked slot.
     *
     * @param req the booking request describing the desired slot
     * @return the auto-generated waiting-list row ID, or {@code -1} if no key was returned
     * @throws RuntimeException wrapping the {@link SQLException} so the caller can surface the DB message
     */
    public int addToWaitingList(BookingRequest req) {
        if (connection == null) return -1;
        String orderTypeValue = toDbOrderType(req.getOrderType());
        System.out.printf(
            "[SQL INSERT] waiting_list → visitor_id='%s' park_id=%d date='%s' time='%s'" +
            " num_visitors=%d order_type='%s'%n",
            req.getVisitorId(), req.getParkId(), req.getVisitDate(), req.getVisitTime(),
            req.getNumVisitors(), orderTypeValue);
        try {
            PreparedStatement st = connection.prepareStatement(
                "INSERT INTO waiting_list " +
                "(visitor_id, park_id, visit_date, visit_time, num_visitors, order_type) " +
                "VALUES (?, ?, ?, ?, ?, ?)",
                Statement.RETURN_GENERATED_KEYS);
            st.setString(1, req.getVisitorId());
            st.setInt(2,    req.getParkId());
            st.setString(3, req.getVisitDate());
            st.setString(4, req.getVisitTime());
            st.setInt(5,    req.getNumVisitors());
            st.setString(6, orderTypeValue);
            st.executeUpdate();
            ResultSet keys = st.getGeneratedKeys();
            if (keys.next()) return keys.getInt(1);
        } catch (SQLException e) {
            System.out.println("addToWaitingList error: " + e.getMessage());
            throw new RuntimeException("Database error: " + e.getMessage(), e);
        }
        return -1;
    }

    // ── Order management ─────────────────────────────────────────────────────

    /**
     * returns all orders for a visitor, newest first.
     * {@code order_type} and {@code status} are uppercased so display code doesn't have to normalise them.
     *
     * @param visitorId the visitor's ID number
     * @return a list of {@link OrderDetail} objects; empty if none found or on error
     */
    public List<OrderDetail> getOrdersByVisitor(String visitorId) {
        List<OrderDetail> list = new ArrayList<>();
        if (connection == null) return list;
        try {
            PreparedStatement st = connection.prepareStatement(
                "SELECT o.id, p.name AS park_name, o.visitor_id, " +
                "       DATE_FORMAT(o.visit_date, '%Y-%m-%d')    AS visit_date, " +
                "       TIME_FORMAT(o.visit_time, '%H:%i')        AS visit_time, " +
                "       o.num_visitors, " +
                "       CASE UPPER(o.order_type) WHEN 'INDIVIDUAL' THEN 'SOLO' WHEN 'SOLO' THEN 'SOLO' WHEN 'FAMILY' THEN 'FAMILY' WHEN 'GROUP' THEN 'GROUP' ELSE UPPER(o.order_type) END AS order_type, " +
                "       UPPER(o.status)      AS status, " +
                "       o.email, " +
                "       DATE_FORMAT(o.created_at, '%Y-%m-%d %H:%i') AS created_at " +
                "FROM orders o " +
                "JOIN parks p ON p.id = o.park_id " +
                "WHERE o.visitor_id = ? " +
                "ORDER BY o.visit_date DESC, o.visit_time DESC");
            st.setString(1, visitorId);
            ResultSet rs = st.executeQuery();
            while (rs.next()) {
                list.add(new OrderDetail(
                    rs.getInt("id"),
                    rs.getString("park_name"),
                    rs.getString("visitor_id"),
                    rs.getString("visit_date"),
                    rs.getString("visit_time"),
                    rs.getInt("num_visitors"),
                    rs.getString("order_type"),
                    rs.getString("status"),
                    rs.getString("email"),
                    rs.getString("created_at")
                ));
            }
        } catch (SQLException e) {
            System.out.println("getOrdersByVisitor error: " + e.getMessage());
        }
        return list;
    }

    /**
     * fetches a single order's display and contact details by ID.
     * used to obtain the recipient email and visit details for notification emails.
     *
     * @param orderId the order's primary-key ID
     * @return an {@link OrderDetail}, or {@code null} if the order doesn't exist or on DB error
     */
    public OrderDetail getOrderById(int orderId) {
        if (connection == null) return null;
        try {
            PreparedStatement st = connection.prepareStatement(
                "SELECT o.id, p.name AS park_name, o.visitor_id, " +
                "       DATE_FORMAT(o.visit_date, '%Y-%m-%d')    AS visit_date, " +
                "       TIME_FORMAT(o.visit_time, '%H:%i')        AS visit_time, " +
                "       o.num_visitors, " +
                "       CASE UPPER(o.order_type) WHEN 'INDIVIDUAL' THEN 'SOLO' WHEN 'SOLO' THEN 'SOLO' WHEN 'FAMILY' THEN 'FAMILY' WHEN 'GROUP' THEN 'GROUP' ELSE UPPER(o.order_type) END AS order_type, " +
                "       UPPER(o.status)     AS status, " +
                "       o.email, " +
                "       DATE_FORMAT(o.created_at, '%Y-%m-%d %H:%i') AS created_at " +
                "FROM orders o JOIN parks p ON p.id = o.park_id " +
                "WHERE o.id = ?");
            st.setInt(1, orderId);
            ResultSet rs = st.executeQuery();
            if (rs.next()) {
                return new OrderDetail(
                    rs.getInt("id"),            rs.getString("park_name"),
                    rs.getString("visitor_id"), rs.getString("visit_date"),
                    rs.getString("visit_time"), rs.getInt("num_visitors"),
                    rs.getString("order_type"), rs.getString("status"),
                    rs.getString("email"),      rs.getString("created_at"));
            }
        } catch (SQLException e) {
            System.out.println("getOrderById error: " + e.getMessage());
        }
        return null;
    }

    /**
     * cancels an order and notifies the first waiting-list entry for the freed slot.
     * only pending/confirmed orders may be cancelled — completed/already-cancelled rows are rejected.
     *
     * <p>cancellation frees up capacity: the slot is excluded from
     * {@link #getAvailableSpots} so a new booking can take the vacated position.
     * the freed slot is then offered to the earliest waiting-list entry for the same
     * park/date/time via {@link #promoteFromWaitingList}; if someone is promoted, their
     * email is returned in the {@link CancelResult}.
     *
     * @param orderId the primary-key ID of the order to cancel
     * @return a {@link CancelResult} indicating success or failure; on success,
     *         {@link CancelResult#getNotifiedEmail()} holds the promoted visitor's email or {@code null}
     */
    public CancelResult cancelOrder(int orderId) {
        if (connection == null) return new CancelResult(false, null);

        // capture the slot details before changing status so we can offer it to the waiting list
        int    parkId = -1;
        Date   visitDate = null;
        Time   visitTime = null;
        try {
            PreparedStatement sel = connection.prepareStatement(
                "SELECT park_id, visit_date, visit_time FROM orders WHERE id = ?");
            sel.setInt(1, orderId);
            ResultSet rs = sel.executeQuery();
            if (rs.next()) {
                parkId    = rs.getInt("park_id");
                visitDate = rs.getDate("visit_date");
                visitTime = rs.getTime("visit_time");
            }
        } catch (SQLException e) {
            System.out.println("cancelOrder (slot lookup) error: " + e.getMessage());
        }

        // WHERE restricts to cancellable statuses — any other status returns 0 updated rows
        try {
            PreparedStatement st = connection.prepareStatement(
                "UPDATE orders SET status = 'cancelled' " +
                "WHERE id = ? AND status IN ('pending', 'confirmed')");
            st.setInt(1, orderId);
            if (st.executeUpdate() == 0)
                return new CancelResult(false, null); // already cancelled or completed
        } catch (SQLException e) {
            System.out.println("cancelOrder error: " + e.getMessage());
            return new CancelResult(false, null);
        }

        // the slot is now free — promote the earliest matching waiting-list entry (if any)
        String notifiedEmail = null;
        if (parkId > 0 && visitDate != null && visitTime != null) {
            notifiedEmail = promoteFromWaitingList(parkId, visitDate, visitTime);
        } else {
            System.out.println("cancelOrder: skipping waiting-list promotion — " +
                "slot details unavailable for order #" + orderId +
                " (slot lookup may have failed before the cancel UPDATE)");
        }

        return new CancelResult(true, notifiedEmail);
    }

    /**
     * offers a freed slot to the earliest waiting-list entry for the same park, date, and
     * time. on a match it creates a confirmed order for that visitor, removes them from the
     * waiting list, emails them a real notification, and writes a server simulation log line.
     *
     * @param parkId    the park whose slot opened up
     * @param visitDate the freed slot's date
     * @param visitTime the freed slot's time
     * @return the promoted visitor's email address, or {@code null} if nobody was waiting
     */
    private synchronized String promoteFromWaitingList(int parkId, Date visitDate, Time visitTime) {
        if (connection == null) return null;

        String dateStr = new java.text.SimpleDateFormat("yyyy-MM-dd").format(visitDate);
        String timeStr = new java.text.SimpleDateFormat("HH:mm").format(visitTime);

        // Check available capacity once — it is the same for every candidate in this queue.
        // getAvailableSpots already excludes the just-cancelled order because cancelOrder()
        // changed its status to 'cancelled' before calling us.
        int available = getAvailableSpots(parkId, dateStr, timeStr);
        if (available <= 0) return null;   // park is still full after the cancellation

        // Walk the queue in arrival order and pick the first entry that fits.
        String visitorId  = null;
        String dbOrderType = null;
        int    wlId        = -1;
        int    numVisitors = 0;
        try {
            PreparedStatement st = connection.prepareStatement(
                "SELECT id, visitor_id, num_visitors, order_type " +
                "FROM waiting_list " +
                "WHERE park_id = ? AND visit_date = ? AND visit_time = ? " +
                "ORDER BY created_at ASC, id ASC");
            st.setInt(1, parkId);
            st.setDate(2, visitDate);
            st.setTime(3, visitTime);
            ResultSet rs = st.executeQuery();
            while (rs.next()) {
                int candidateSize = rs.getInt("num_visitors");
                if (candidateSize <= available) {
                    wlId        = rs.getInt("id");
                    visitorId   = rs.getString("visitor_id");
                    numVisitors = candidateSize;
                    dbOrderType = rs.getString("order_type");
                    break;   // first fitting entry found
                }
            }
        } catch (SQLException e) {
            System.out.println("promoteFromWaitingList (lookup) error: " + e.getMessage());
            return null;
        }
        if (wlId == -1) return null;   // no entry in the queue fits within available capacity

        String email = getVisitorEmail(visitorId);

        // create the confirmed order for the promoted visitor
        int newOrderId;
        try {
            PreparedStatement ins = connection.prepareStatement(
                "INSERT INTO orders " +
                "(visitor_id, park_id, visit_date, visit_time, num_visitors, order_type, status, email) " +
                "VALUES (?, ?, ?, ?, ?, ?, 'confirmed', ?)",
                Statement.RETURN_GENERATED_KEYS);
            ins.setString(1, visitorId);
            ins.setInt(2,    parkId);
            ins.setDate(3,   visitDate);
            ins.setTime(4,   visitTime);
            ins.setInt(5,    numVisitors);
            ins.setString(6, dbOrderType);
            ins.setString(7, email);
            ins.executeUpdate();
            ResultSet keys = ins.getGeneratedKeys();
            newOrderId = keys.next() ? keys.getInt(1) : -1;
        } catch (SQLException e) {
            System.out.println("promoteFromWaitingList (insert) error: " + e.getMessage());
            return null;   // leave the visitor on the waiting list if the order couldn't be created
        }

        // remove them from the waiting list now that they hold a confirmed order
        try {
            PreparedStatement del = connection.prepareStatement(
                "DELETE FROM waiting_list WHERE id = ?");
            del.setInt(1, wlId);
            del.executeUpdate();
        } catch (SQLException e) {
            System.out.println("promoteFromWaitingList (delete) error: " + e.getMessage());
        }

        // resolve the park name for the notification
        String parkName = "your selected park";
        try {
            PreparedStatement pn = connection.prepareStatement("SELECT name FROM parks WHERE id = ?");
            pn.setInt(1, parkId);
            ResultSet rs = pn.executeQuery();
            if (rs.next()) parkName = rs.getString("name");
        } catch (SQLException e) {
            System.out.println("promoteFromWaitingList (park name) error: " + e.getMessage());
        }

        // server simulation log line
        gui.ServerPortFrameController.logSimulation(String.format(
            "🎟️ Waiting-list promotion → Order #%d confirmed for visitor %s at %s on %s %s%s",
            newOrderId, visitorId, parkName, dateStr, timeStr,
            (email.isEmpty() ? " (no email on file)" : " — notified " + email)));

        // real email notification (no-op if no address on file)
        if (!email.isEmpty()) {
            EmailService.sendEmail(email,
                "Good news! A spot opened up for your GoNature visit 🌿",
                "Hello,\n\n" +
                "Great news — a spot just opened up for the visit you were waiting for, and we've " +
                "automatically confirmed a booking for you!\n\n" +
                "Order #: " + newOrderId + "\n" +
                "Park: "    + parkName + "\n" +
                "Date: "    + dateStr + "\n" +
                "Time: "    + timeStr + "\n" +
                "Visitors: " + numVisitors + "\n\n" +
                "Please confirm this booking within 1 hour to keep your spot. If we don't hear " +
                "from you, the slot will be released to the next visitor in line.\n\n" +
                "See you in the park!\n\n" +
                "— GoNature Parks");
        }

        return email.isEmpty() ? null : email;
    }

    /**
     * marks a PENDING order as CONFIRMED — used when a visitor confirms an upcoming visit.
     * only rows currently {@code 'pending'} are changed; already-confirmed, cancelled, or
     * completed orders are left untouched.
     *
     * @param orderId the primary-key ID of the order to confirm
     * @return {@code true} if a pending row was updated to confirmed; {@code false} otherwise
     */
    public boolean confirmOrder(int orderId) {
        if (connection == null) return false;
        try (PreparedStatement st = connection.prepareStatement(
                "UPDATE orders SET status = 'confirmed' " +
                "WHERE id = ? AND status = 'pending'")) {
            st.setInt(1, orderId);
            return st.executeUpdate() > 0;
        } catch (SQLException e) {
            System.out.println("confirmOrder error: " + e.getMessage());
            return false;
        }
    }

    // ── Authentication ───────────────────────────────────────────────────────

    /**
     * looks up the ID in guides first (role = {@code "GUIDE"}), then in visitors.
     * for visitors, a LEFT JOIN against {@code subscribers} determines whether the role
     * is {@code "SUBSCRIBER"} (Family Member Club) or plain {@code "VISITOR"}.
     * this makes the ID serve as both a discount identifier and a reservation key.
     *
     * @param idNumber the visitor's national ID number
     * @return a {@link LoginResult} with role {@code "GUIDE"}, {@code "SUBSCRIBER"}, or
     *         {@code "VISITOR"}, or {@code null} if not found
     */
    public LoginResult loginVisitor(String idNumber) {
        if (connection == null) return null;

        // guides take priority — they have their own entry-pricing rules
        try {
            PreparedStatement st = connection.prepareStatement(
                "SELECT first_name, last_name, email FROM guides WHERE id_number = ?");
            st.setString(1, idNumber);
            ResultSet rs = st.executeQuery();
            if (rs.next())
                return new LoginResult("GUIDE",
                    rs.getString("first_name"), rs.getString("last_name"),
                    rs.getString("email"), idNumber, null);
        } catch (SQLException e) {
            System.out.println("Guide lookup error: " + e.getMessage());
        }

        // single query: join subscribers so we can assign SUBSCRIBER role in one round-trip
        try {
            PreparedStatement st = connection.prepareStatement(
                "SELECT v.first_name, v.last_name, v.email, " +
                "       (s.id_number IS NOT NULL) AS is_subscriber " +
                "FROM   visitors v " +
                "LEFT JOIN subscribers s ON s.id_number = v.id_number " +
                "WHERE  v.id_number = ?");
            st.setString(1, idNumber);
            ResultSet rs = st.executeQuery();
            if (rs.next()) {
                String role = rs.getBoolean("is_subscriber") ? "SUBSCRIBER" : "VISITOR";
                return new LoginResult(role,
                    rs.getString("first_name"), rs.getString("last_name"),
                    rs.getString("email"), idNumber, null);
            }
        } catch (SQLException e) {
            System.out.println("Visitor lookup error: " + e.getMessage());
        }
        return null;
    }

    /**
     * maps a UI display type (SOLO / FAMILY / GROUP) to its canonical DB value
     * (individual / family / group). Handles legacy lowercase values too.
     */
    private static String toDbOrderType(String displayType) {
        if (displayType == null) return "individual";
        switch (displayType.toUpperCase()) {
            case "SOLO":       return "individual";
            case "FAMILY":     return "family";
            case "GROUP":      return "group";
            default:           return displayType.toLowerCase();
        }
    }

    /**
     * checks whether a visitor ID belongs to a Family Member Club subscriber.
     * used at check-in to determine walk-in discount eligibility.
     *
     * @param visitorId the visitor's national ID number
     * @return {@code true} if the ID appears in the {@code subscribers} table
     */
    public boolean isRegisteredSubscriber(String visitorId) {
        if (connection == null) return false;
        try (PreparedStatement st = connection.prepareStatement(
                "SELECT 1 FROM subscribers WHERE id_number = ? LIMIT 1")) {
            st.setString(1, visitorId);
            try (ResultSet rs = st.executeQuery()) { return rs.next(); }
        } catch (SQLException e) {
            System.out.println("isRegisteredSubscriber error: " + e.getMessage());
            return false;
        }
    }

    /**
     * authenticates a staff login. the DB role string is normalised to an app constant via {@link #mapRole}.
     *
     * @param username the staff username
     * @param password the plain-text password
     * @return a {@link LoginResult} with the mapped role and park affiliation,
     *         or {@code null} if the credentials don't match
     */
    public LoginResult loginUser(String username, String password) {
        loginUserFailReason = null;
        if (connection == null) return null;
        try {
            PreparedStatement st = connection.prepareStatement(
                "SELECT first_name, last_name, email, password, role, park_id " +
                "FROM users WHERE username = ?");
            st.setString(1, username);
            ResultSet rs = st.executeQuery();
            if (!rs.next()) {
                loginUserFailReason = "ID not registered";
                return null;
            }
            if (!rs.getString("password").equals(password)) {
                loginUserFailReason = "Incorrect password";
                return null;
            }

            Integer parkId = (rs.getObject("park_id") == null) ? null : rs.getInt("park_id");
            return new LoginResult(
                mapRole(rs.getString("role")),
                rs.getString("first_name"),
                rs.getString("last_name"),
                rs.getString("email"),
                username,
                parkId);
        } catch (SQLException e) {
            System.out.println("User login error: " + e.getMessage());
        }
        return null;
    }

    /**
     * converts a DB role string to the app's canonical role constant.
     * the DB uses short lowercase names; the app uses {@code SCREAMING_SNAKE_CASE}.
     *
     * @param dbRole the role string stored in the database
     * @return the corresponding application role constant
     */
    private String mapRole(String dbRole) {
        switch (dbRole) {
            case "department_manager": return "DEPARTMENT_MANAGER";
            case "park_manager":       return "PARK_MANAGER";
            case "employee":           return "PARK_EMPLOYEE";
            case "service":            return "SERVICE_REP";
            default:                   return dbRole.toUpperCase();
        }
    }

    /**
     * group bookings are only allowed for registered guides — this check enforces that rule.
     *
     * @param visitorId the visitor's national ID number
     * @return {@code true} if the ID exists in the guides table; {@code false} otherwise
     */
    public boolean isRegisteredGuide(String visitorId) {
        if (connection == null) return false;
        try (PreparedStatement st = connection.prepareStatement(
                "SELECT 1 FROM guides WHERE id_number = ? LIMIT 1")) {
            st.setString(1, visitorId);
            try (ResultSet rs = st.executeQuery()) { return rs.next(); }
        } catch (SQLException e) {
            System.out.println("isRegisteredGuide error: " + e.getMessage());
            return false;
        }
    }

    /**
     * walk-in GROUP visits require a guide who has already been approved by the department.
     * pre-booked GROUP orders are NOT gated by this method — they were approved at booking time.
     *
     * @param visitorId the visitor's national ID number
     * @return {@code true} if the ID exists in guides with {@code is_approved = 1}
     */
    public boolean isApprovedGuide(String visitorId) {
        if (connection == null) return false;
        try (PreparedStatement st = connection.prepareStatement(
                "SELECT 1 FROM guides WHERE id_number = ? AND is_approved = 1 LIMIT 1")) {
            st.setString(1, visitorId);
            try (ResultSet rs = st.executeQuery()) { return rs.next(); }
        } catch (SQLException e) {
            System.out.println("isApprovedGuide error: " + e.getMessage());
            return false;
        }
    }

    // ── Entry control ─────────────────────────────────────────────────────────

    /**
     * looks up today's confirmed/pending booking for a visitor without committing it.
     * used by the FIND_TODAY_BOOKING pre-booked check-in flow.
     */
    public OrderDetail getTodayBooking(String visitorId, int parkId) {
        if (connection == null) return null;
        try {
            PreparedStatement st = connection.prepareStatement(
                "SELECT o.id, o.visitor_id, o.num_visitors, o.order_type, " +
                "       o.visit_date, o.visit_time, o.status, o.email, o.created_at, " +
                "       p.name AS park_name " +
                "FROM orders o JOIN parks p ON p.id = o.park_id " +
                "WHERE o.visitor_id=? AND o.park_id=? AND o.visit_date=CURDATE() " +
                "  AND o.status IN ('pending','confirmed') " +
                "ORDER BY o.created_at ASC LIMIT 1");
            st.setString(1, visitorId);
            st.setInt(2, parkId);
            ResultSet rs = st.executeQuery();
            if (!rs.next()) return null;
            String rawOT = rs.getString("order_type");
            String raw   = (rawOT != null) ? rawOT.toUpperCase() : "INDIVIDUAL";
            String oType = "INDIVIDUAL".equals(raw) ? "SOLO" : raw;
            return new OrderDetail(
                rs.getInt("id"),
                rs.getString("park_name"),
                rs.getString("visitor_id"),
                rs.getString("visit_date"),
                rs.getString("visit_time"),
                rs.getInt("num_visitors"),
                oType,
                rs.getString("status").toUpperCase(),
                rs.getString("email"),
                rs.getString("created_at"));
        } catch (SQLException e) {
            System.out.println("getTodayBooking error: " + e.getMessage());
            return null;
        }
    }

    /**
     * checks a visitor in at the gate. tries in order: already-inside guard, pre-booked match,
     * then walk-in capacity check. pricing is delegated to {@link #calculateEntryPrice}.
     *
     * @param req the check-in request (visitor ID, park ID, order type, visitor count)
     * @return an {@link EntryResult}; {@code isSuccess() == false} with a message on any rejection
     */
    public EntryResult checkInVisitor(EntryCheckRequest req) {
        if (connection == null)
            return new EntryResult(false, false, -1, "", req.getVisitorId(), 0, "", 0,
                "We can't reach the database right now. Please try again in a moment.");

        String visitorId = req.getVisitorId().trim();
        int    parkId    = req.getParkId();

        // reject double-entry before anything else
        try {
            PreparedStatement st = connection.prepareStatement(
                "SELECT id FROM orders " +
                "WHERE visitor_id=? AND park_id=? AND visit_date=CURDATE() " +
                "  AND status='completed' AND entry_time IS NOT NULL AND exit_time IS NULL " +
                "LIMIT 1");
            st.setString(1, visitorId);
            st.setInt(2, parkId);
            ResultSet rs = st.executeQuery();
            if (rs.next())
                return new EntryResult(false, false, rs.getInt("id"), "", visitorId, 0, "", 0,
                    "Visitor " + visitorId + " is already checked in at this park today. "
                    + "Please register their exit before checking them in again.");
        } catch (SQLException e) {
            System.out.println("checkInVisitor (already-in check) error: " + e.getMessage());
        }

        // pre-booked visitors take priority over walk-ins
        try {
            PreparedStatement st = connection.prepareStatement(
                "SELECT o.id, o.num_visitors, o.order_type, p.name AS park_name, p.full_price " +
                "FROM orders o JOIN parks p ON p.id = o.park_id " +
                "WHERE o.visitor_id=? AND o.park_id=? AND o.visit_date=CURDATE() " +
                "  AND o.status IN ('pending', 'confirmed') " +
                "ORDER BY o.created_at ASC LIMIT 1");
            st.setString(1, visitorId);
            st.setInt(2, parkId);
            ResultSet rs = st.executeQuery();

            if (rs.next()) {
                int    orderId         = rs.getInt("id");
                int    numVis          = rs.getInt("num_visitors");
                // map DB value to display name (SOLO / FAMILY / GROUP)
                String oTypeRawVal     = rs.getString("order_type");
                String oTypeRaw        = (oTypeRawVal != null) ? oTypeRawVal.toUpperCase() : "INDIVIDUAL";
                String oType           = "INDIVIDUAL".equals(oTypeRaw) ? "SOLO" : oTypeRaw;
                String parkName     = rs.getString("park_name");
                double fullPrice    = rs.getDouble("full_price");
                boolean isGuide     = isRegisteredGuide(visitorId);
                boolean isSubscriber = isRegisteredSubscriber(visitorId);
                String  entryDate   = java.time.LocalDate.now().toString();
                double total        = calculateEntryPrice(parkId, entryDate, true, oType, numVis, fullPrice, isGuide, isSubscriber);
                String note         = buildEntryPricingNote(parkId, entryDate, true, oType, numVis, isGuide, isSubscriber);

                PreparedStatement upd = connection.prepareStatement(
                    "UPDATE orders SET status='completed', entry_time=NOW() WHERE id=?");
                upd.setInt(1, orderId);
                upd.executeUpdate();

                return new EntryResult(true, true, orderId, parkName, visitorId,
                    numVis, oType, total, note);
            }
        } catch (SQLException e) {
            System.out.println("checkInVisitor (booking lookup) error: " + e.getMessage());
            return new EntryResult(false, false, -1, "", visitorId, 0, "", 0,
                "Something went wrong while looking up the booking. Please try again.");
        }

        // no booking found — treat as walk-in
        int    numVis        = req.getNumVisitors();
        String wDisplayType  = req.getOrderType().toUpperCase(); // SOLO / FAMILY / GROUP (for display + pricing)
        String wType         = toDbOrderType(req.getOrderType()); // individual / family / group (for DB insert)

        // walk-in GROUP requires an approved guide as the lead visitor
        if ("GROUP".equals(wDisplayType) && !isApprovedGuide(visitorId))
            return new EntryResult(false, false, -1, "", visitorId, 0, "", 0,
                "GROUP visit requires an approved guide. This visitor is not a registered guide.");

        String parkName  = "";
        double fullPrice = 0;

        try {
            PreparedStatement st = connection.prepareStatement(
                "SELECT name, full_price FROM parks WHERE id=?");
            st.setInt(1, parkId);
            ResultSet rs = st.executeQuery();
            if (!rs.next())
                return new EntryResult(false, false, -1, "", visitorId, 0, "", 0,
                    "We couldn't find that park. Please check the selection and try again.");
            parkName  = rs.getString("name");
            fullPrice = rs.getDouble("full_price");
        } catch (SQLException e) {
            System.out.println("checkInVisitor (park lookup) error: " + e.getMessage());
            return new EntryResult(false, false, -1, "", visitorId, 0, "", 0,
                "Something went wrong while looking up the park. Please try again.");
        }

        String curTime  = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm"));
        String today    = java.time.LocalDate.now().toString();
        int    available = getAvailableSpots(parkId, today, curTime);

        if (available < 0)
            return new EntryResult(false, false, -1, parkName, visitorId, numVis,
                wDisplayType, 0, "Could not check park capacity. Please try again.");

        if (available < numVis)
            return new EntryResult(false, false, -1, parkName, visitorId, numVis,
                wDisplayType, 0,
                "Not enough space. Park has " + available + " spot(s) remaining.");

        // walk-in order is completed immediately — no pre-existing pending status
        System.out.printf(
            "[SQL INSERT] orders (walk-in) → visitor_id='%s' park_id=%d" +
            " num_visitors=%d order_type='%s' status='completed'%n",
            visitorId, parkId, numVis, wType);
        try {
            PreparedStatement st = connection.prepareStatement(
                "INSERT INTO orders " +
                "(visitor_id, park_id, visit_date, visit_time, num_visitors, order_type, status, email, entry_time) " +
                "VALUES (?, ?, CURDATE(), CURTIME(), ?, ?, 'completed', '', NOW())",
                Statement.RETURN_GENERATED_KEYS);
            st.setString(1, visitorId);
            st.setInt(2,    parkId);
            st.setInt(3,    numVis);
            st.setString(4, wType);
            st.executeUpdate();
            ResultSet keys = st.getGeneratedKeys();
            int orderId = keys.next() ? keys.getInt(1) : -1;

            boolean isGuide      = isRegisteredGuide(visitorId);
            boolean isSubscriber = isRegisteredSubscriber(visitorId);
            double total = calculateEntryPrice(parkId, today, false, wDisplayType, numVis, fullPrice, isGuide, isSubscriber);
            String note  = buildEntryPricingNote(parkId, today, false, wDisplayType, numVis, isGuide, isSubscriber);
            return new EntryResult(true, false, orderId, parkName, visitorId,
                numVis, wDisplayType, total, note);

        } catch (SQLException e) {
            System.out.println("checkInVisitor (walk-in insert) error: " + e.getMessage());
            return new EntryResult(false, false, -1, parkName, visitorId, 0, "", 0,
                "Could not register walk-in. Please try again.");
        }
    }

    /**
     * computes the total entry price.
     *
     * <p>Pricing rules (applied in order):
     * <ol>
     *   <li><b>GROUP by guide</b> — guide enters free; paying visitors receive 25% advance
     *       discount (pre-booked) or 10% walk-in discount. Guide pricing is not affected by
     *       subscriber status — guide path returns immediately.</li>
     *   <li><b>Base discount</b> — pre-booked visitors receive 15%; walk-in visitors pay full
     *       price (multiplier = 1.0).</li>
     *   <li><b>Subscriber stacking</b> — if the visitor is a Family Member Club subscriber,
     *       an additional 10% discount is applied on top of whatever base rate was calculated
     *       in step 2 (i.e. multiplied by 0.90). Examples:
     *       <ul>
     *         <li>Pre-booked subscriber: 0.85 × 0.90 = 0.765 (23.5% total discount)</li>
     *         <li>Walk-in subscriber:    1.00 × 0.90 = 0.90  (10% total discount)</li>
     *       </ul>
     *   </li>
     * </ol>
     *
     * @param prebooked    {@code true} for a confirmed pre-booking; {@code false} for walk-in
     * @param orderType    the visit type in uppercase ({@code "SOLO"}, {@code "FAMILY"}, or {@code "GROUP"})
     * @param numVisitors  total visitors including any guide
     * @param fullPrice    the park's per-person full price
     * @param isGuide      {@code true} if led by a registered guide
     * @param isSubscriber {@code true} if the visitor holds a Family Member Club subscription
     * @return the total price to charge
     */
    private double calculateEntryPrice(int parkId, String date, boolean prebooked,
                                       String orderType, int numVisitors, double fullPrice,
                                       boolean isGuide, boolean isSubscriber) {
        double subtotal;

        // Guide-group pricing is fixed and does not stack with the subscriber discount.
        if ("GROUP".equals(orderType.toUpperCase()) && isGuide) {
            int paying = Math.max(0, numVisitors - 1);
            subtotal = prebooked
                ? paying * fullPrice * 0.75   // guide free + 25% advance discount
                : paying * fullPrice * 0.90;  // guide free + 10% walk-in discount
        } else {
            // Step 1 — base multiplier: 15% off for pre-booked, full price for walk-in.
            double baseMultiplier = prebooked ? 0.85 : 1.00;
            // Step 2 — subscriber stacks an additional 10% on top of whatever base applies.
            double subscriberMultiplier = isSubscriber ? 0.90 : 1.00;
            subtotal = numVisitors * fullPrice * baseMultiplier * subscriberMultiplier;
        }

        // Step 3 — promotional discount: applied AFTER all standard discounts. The largest
        // active promotion for this park on this date wins (getBestActivePromotion orders by
        // discount desc); multiple promotions never stack.
        double promoMultiplier = 1.0 - promoFraction(parkId, date);
        return subtotal * promoMultiplier;
    }

    /**
     * returns the best active promotion's discount as a fraction in {@code [0, 0.5]}
     * for a park on a date, or {@code 0.0} if no promotion applies.
     *
     * @param parkId the park to look up
     * @param date   the date to test, {@code yyyy-MM-dd}
     * @return the discount fraction (e.g. {@code 0.10} for a 10%-off promotion)
     */
    private double promoFraction(int parkId, String date) {
        Promotion best = getBestActivePromotion(parkId, date);
        if (best == null) return 0.0;
        // Clamp defensively so a bad row can never invert or over-discount the price.
        return Math.max(0.0, Math.min(0.5, best.getDiscountPercent() / 100.0));
    }

    /**
     * quotes the indicative price for a pre-booked order so the booking-confirmation
     * screen can show the visitor the price before and after any active promotion.
     *
     * <p>uses the same pre-booked discount logic as the gate ({@link #calculateEntryPrice}),
     * evaluated for the visit date so the preview matches what the visitor will actually pay.
     *
     * @param parkId      the park being booked
     * @param visitDate   the visit date, {@code yyyy-MM-dd} (promotion window is tested against this)
     * @param orderType   the display order type ({@code SOLO} / {@code FAMILY} / {@code GROUP})
     * @param numVisitors the number of visitors
     * @param visitorId   the booker's ID (used to detect guide / subscriber status)
     * @return {@code [basePrice, finalPrice]} — {@code basePrice} before promotion,
     *         {@code finalPrice} after the best active promotion
     */
    public double[] quotePrebookedPrice(int parkId, String visitDate, String orderType,
                                        int numVisitors, String visitorId) {
        Park p = getParkById(parkId);
        double fullPrice = (p != null) ? p.getFullPrice() : 0.0;
        boolean isGuide      = isRegisteredGuide(visitorId);
        boolean isSubscriber = isRegisteredSubscriber(visitorId);

        double finalPrice = calculateEntryPrice(parkId, visitDate, true, orderType,
                                                numVisitors, fullPrice, isGuide, isSubscriber);
        double frac = promoFraction(parkId, visitDate);
        double base = (frac < 1.0) ? finalPrice / (1.0 - frac) : finalPrice;
        return new double[] { base, finalPrice };
    }

    /**
     * builds a human-readable pricing note for the entry result card.
     * called server-side so the note accurately reflects guide and subscriber status.
     * shows each discount component separately so the visitor can see exactly how the
     * total was calculated.
     */
    private String buildEntryPricingNote(int parkId, String date, boolean prebooked,
                                         String orderType, int numVisitors,
                                         boolean isGuide, boolean isSubscriber) {
        StringBuilder note = new StringBuilder();

        // Guide-group note — subscriber discount does not apply here.
        if ("GROUP".equals(orderType.toUpperCase()) && isGuide) {
            int paying = Math.max(0, numVisitors - 1);
            note.append(prebooked
                ? "Pre-booked group: guide enters free. " + paying + " paying visitor(s) — 25% advance discount."
                : "Walk-in group: guide enters free. " + paying + " paying visitor(s) — 10% walk-in discount.");
        } else {
            // Build the note piece by piece so every active component is visible.
            if (prebooked) {
                note.append("Pre-booked: 15% advance-booking discount");
            } else {
                note.append("Walk-in: full price");
            }
            if (isSubscriber) {
                note.append(" + 10% Family Member Club discount");
                if (prebooked)
                    note.append(" (23.5% total discount)");
            }
            note.append(".");
        }

        // Append the promotion line whenever one is active for this park/date.
        Promotion promo = getBestActivePromotion(parkId, date);
        if (promo != null) {
            note.append(String.format("  🎉 Promotion applied: %.0f%% off — %s.",
                promo.getDiscountPercent(), promo.getDescription()));
        }
        return note.toString();
    }

    /**
     * records a visitor's exit by stamping {@code exit_time = NOW()} on their most recent open entry.
     *
     * @param visitorId the visitor's ID number
     * @param parkId    the park's primary-key ID
     * @return {@code true} if an active entry was found and updated; {@code false} otherwise
     */
    public boolean registerExit(String visitorId, int parkId) {
        if (connection == null) return false;
        try (PreparedStatement st = connection.prepareStatement(
                "UPDATE orders SET exit_time=NOW() " +
                "WHERE visitor_id=? AND park_id=? AND visit_date=CURDATE() " +
                "  AND status='completed' AND entry_time IS NOT NULL AND exit_time IS NULL " +
                "ORDER BY entry_time DESC LIMIT 1")) {
            st.setString(1, visitorId.trim());
            st.setInt(2, parkId);
            return st.executeUpdate() > 0;
        } catch (SQLException e) {
            System.out.println("registerExit error: " + e.getMessage());
            return false;
        }
    }

    // ── Visitor name lookup ───────────────────────────────────────────────────

    /**
     * returns the visitor's full name ({@code "First Last"}) for use in notification emails.
     * checks the {@code guides} table first (guides may book group visits), then {@code visitors}.
     * falls back to the raw ID string if neither table has the record or on DB error.
     *
     * @param visitorId the visitor's national ID number
     * @return the full name, or the bare ID as a safe fallback
     */
    public String getVisitorDisplayName(String visitorId) {
        if (connection == null || visitorId == null) return visitorId;
        // Guides first (they log in with the same ID but are in a different table).
        try {
            PreparedStatement st = connection.prepareStatement(
                "SELECT first_name, last_name FROM guides WHERE id_number = ? LIMIT 1");
            st.setString(1, visitorId);
            ResultSet rs = st.executeQuery();
            if (rs.next())
                return rs.getString("first_name") + " " + rs.getString("last_name");
        } catch (SQLException e) {
            System.out.println("getVisitorDisplayName (guides) error: " + e.getMessage());
        }
        // Regular visitors.
        try {
            PreparedStatement st = connection.prepareStatement(
                "SELECT first_name, last_name FROM visitors WHERE id_number = ? LIMIT 1");
            st.setString(1, visitorId);
            ResultSet rs = st.executeQuery();
            if (rs.next())
                return rs.getString("first_name") + " " + rs.getString("last_name");
        } catch (SQLException e) {
            System.out.println("getVisitorDisplayName (visitors) error: " + e.getMessage());
        }
        return visitorId; // safe fallback — email is still sent, just without the name
    }

    // ── Reports ───────────────────────────────────────────────────────────────

    /**
     * visitor report: total visitors per day per order-type for the given calendar month.
     * each row has a {@code dayLabel} for the chart X-axis and a {@code visitDate} for sort ordering.
     *
     * @param parkId the park to filter by, or {@code null} for all parks
     * @param year   the four-digit calendar year
     * @param month  the month number 1–12
     * @return a list of {@link ReportVisitorRow} objects ordered by date and order type;
     *         empty on error or no data
     */
    public List<ReportVisitorRow> getVisitorReport(Integer parkId, int year, int month) {
        List<ReportVisitorRow> list = new ArrayList<>();
        if (connection == null) return list;
        try {
            String sql =
                "SELECT DATE_FORMAT(o.visit_date,'%m/%d') AS day_label, " +
                "       DATE_FORMAT(o.visit_date,'%Y-%m-%d') AS visit_date, " +
                "       CASE UPPER(o.order_type) WHEN 'INDIVIDUAL' THEN 'SOLO' WHEN 'SOLO' THEN 'SOLO' WHEN 'FAMILY' THEN 'FAMILY' WHEN 'GROUP' THEN 'GROUP' ELSE UPPER(o.order_type) END AS order_type, " +
                "       SUM(o.num_visitors) AS total_visitors " +
                "FROM orders o " +
                "WHERE o.status IN ('confirmed','completed') " +
                "  AND YEAR(o.visit_date) = ? AND MONTH(o.visit_date) = ? " +
                (parkId != null ? "  AND o.park_id = ? " : "") +
                "GROUP BY o.visit_date, o.order_type " +
                "ORDER BY o.visit_date ASC, o.order_type ASC";
            PreparedStatement st = connection.prepareStatement(sql);
            st.setInt(1, year);
            st.setInt(2, month);
            if (parkId != null) st.setInt(3, parkId);
            ResultSet rs = st.executeQuery();
            while (rs.next()) {
                list.add(new ReportVisitorRow(
                    rs.getString("day_label"),
                    rs.getString("visit_date"),
                    rs.getString("order_type"),
                    rs.getInt("total_visitors")
                ));
            }
        } catch (SQLException e) {
            System.out.println("getVisitorReport error: " + e.getMessage());
        }
        return list;
    }

    /**
     * cancellation report: all {@code 'cancelled'} and {@code 'no_show'} orders whose
     * planned visit date falls in the given calendar month, newest first.
     *
     * @param parkId the park to filter by, or {@code null} for all parks
     * @param year   the four-digit calendar year
     * @param month  the month number 1–12
     * @return a list of {@link ReportCancelRow} objects; empty on error or no data
     */
    public List<ReportCancelRow> getCancelReport(Integer parkId, int year, int month) {
        List<ReportCancelRow> list = new ArrayList<>();
        if (connection == null) return list;
        try {
            String sql =
                "SELECT o.id, p.name AS park_name, o.visitor_id, " +
                "       DATE_FORMAT(o.visit_date,'%Y-%m-%d') AS visit_date, " +
                "       TIME_FORMAT(o.visit_time,'%H:%i')    AS visit_time, " +
                "       CASE UPPER(o.order_type) WHEN 'INDIVIDUAL' THEN 'SOLO' WHEN 'SOLO' THEN 'SOLO' WHEN 'FAMILY' THEN 'FAMILY' WHEN 'GROUP' THEN 'GROUP' ELSE UPPER(o.order_type) END AS order_type, " +
                "       UPPER(o.status)     AS status," +
                "       o.num_visitors, " +
                "       DATE_FORMAT(o.created_at,'%Y-%m-%d %H:%i') AS created_at " +
                "FROM orders o JOIN parks p ON p.id = o.park_id " +
                "WHERE o.status IN ('cancelled','no_show') " +
                "  AND YEAR(o.visit_date) = ? AND MONTH(o.visit_date) = ? " +
                (parkId != null ? "  AND o.park_id = ? " : "") +
                "ORDER BY o.created_at DESC";
            PreparedStatement st = connection.prepareStatement(sql);
            st.setInt(1, year);
            st.setInt(2, month);
            if (parkId != null) st.setInt(3, parkId);
            ResultSet rs = st.executeQuery();
            while (rs.next()) {
                list.add(new ReportCancelRow(
                    rs.getInt("id"),
                    rs.getString("park_name"),
                    rs.getString("visitor_id"),
                    rs.getString("visit_date"),
                    rs.getString("visit_time"),
                    rs.getString("order_type"),
                    rs.getString("status"),
                    rs.getInt("num_visitors"),
                    rs.getString("created_at")
                ));
            }
        } catch (SQLException e) {
            System.out.println("getCancelReport error: " + e.getMessage());
        }
        return list;
    }

    /**
     * usage report: average visitors per hour slot per day for the given calendar month,
     * across all parks.  the client divides by capacity × 100 to get percentage-of-capacity
     * for the line chart.
     *
     * @param year  the four-digit calendar year
     * @param month the month number 1–12
     * @return a list of {@link ReportUsageRow} objects ordered by park name then hour slot;
     *         empty on error or no data
     */
    public List<ReportUsageRow> getUsageReport(int year, int month) {
        List<ReportUsageRow> list = new ArrayList<>();
        if (connection == null) return list;
        try {
            PreparedStatement st = connection.prepareStatement(
                "SELECT p.name AS park_name, " +
                "       TIME_FORMAT(o.visit_time,'%H:00') AS hour_slot, " +
                "       p.capacity, " +
                "       ROUND(SUM(o.num_visitors) " +
                "             / GREATEST(COUNT(DISTINCT o.visit_date), 1), 1) AS avg_per_day " +
                "FROM orders o JOIN parks p ON p.id = o.park_id " +
                "WHERE o.status IN ('confirmed','completed') " +
                "  AND YEAR(o.visit_date) = ? AND MONTH(o.visit_date) = ? " +
                "GROUP BY p.id, p.name, p.capacity, TIME_FORMAT(o.visit_time,'%H:00') " +
                "ORDER BY p.name ASC, hour_slot ASC");
            st.setInt(1, year);
            st.setInt(2, month);
            ResultSet rs = st.executeQuery();
            while (rs.next()) {
                list.add(new ReportUsageRow(
                    rs.getString("park_name"),
                    rs.getString("hour_slot"),
                    rs.getInt("capacity"),
                    rs.getDouble("avg_per_day")
                ));
            }
        } catch (SQLException e) {
            System.out.println("getUsageReport error: " + e.getMessage());
        }
        return list;
    }

    // ── Reminder service ──────────────────────────────────────────────────────

    /**
     * returns all pending/confirmed orders for tomorrow — used by {@link ReminderService}
     * at startup to decide who needs a reminder notification.
     *
     * @return a list of {@link ReminderOrderInfo} sorted by visit time; empty if none or on error
     */
    public List<ReminderOrderInfo> getTomorrowOrdersForReminder() {
        List<ReminderOrderInfo> list = new ArrayList<>();
        if (connection == null) return list;
        try {
            PreparedStatement st = connection.prepareStatement(
                "SELECT o.id, " +
                "       COALESCE(o.email, '') AS email, " +
                "       DATE_FORMAT(o.visit_date, '%Y-%m-%d') AS visit_date, " +
                "       TIME_FORMAT(o.visit_time, '%H:%i')    AS visit_time, " +
                "       p.name AS park_name " +
                "FROM orders o " +
                "JOIN parks p ON p.id = o.park_id " +
                "WHERE o.visit_date = DATE_ADD(CURDATE(), INTERVAL 1 DAY) " +
                "  AND o.status IN ('pending', 'confirmed') " +
                "ORDER BY o.visit_time ASC");
            ResultSet rs = st.executeQuery();
            while (rs.next()) {
                list.add(new ReminderOrderInfo(
                    rs.getInt("id"),
                    rs.getString("email"),
                    rs.getString("visit_date"),
                    rs.getString("visit_time"),
                    rs.getString("park_name")
                ));
            }
        } catch (SQLException e) {
            System.out.println("getTomorrowOrdersForReminder error: " + e.getMessage());
        }
        return list;
    }

    // ── Registration ─────────────────────────────────────────────────────────

    /**
     * registers a new visitor, and optionally a subscriber record if the request asks for one.
     * rejects duplicate IDs upfront to give a cleaner error than a DB unique-key violation.
     *
     * @param req the validated registration request from the client
     * @return a {@link RegisterResult} with {@code success = true} on success;
     *         {@code success = false} with a descriptive message on any error
     */
    public RegisterResult registerVisitor(RegisterRequest req) {
        if (connection == null)
            return new RegisterResult(false,
                "We can't reach the database right now. Please try again in a moment.", null);

        // ── Duplicate-ID checks ─────────────────────────────────────────────────
        // Check the visitors table first (most common path).
        try {
            PreparedStatement st = connection.prepareStatement(
                "SELECT 1 FROM visitors WHERE id_number = ? LIMIT 1");
            st.setString(1, req.getIdNumber());
            if (st.executeQuery().next())
                return new RegisterResult(false,
                    "An account with this ID already exists. Please log in instead.", null);
        } catch (SQLException e) {
            System.out.println("registerVisitor (visitor-check) error: " + e.getMessage());
            return new RegisterResult(false,
                "Something went wrong while creating your account. Please try again.", null);
        }

        // Also guard against a guide ID being re-used as a visitor account.
        try {
            PreparedStatement gst = connection.prepareStatement(
                "SELECT 1 FROM guides WHERE id_number = ? LIMIT 1");
            gst.setString(1, req.getIdNumber());
            if (gst.executeQuery().next())
                return new RegisterResult(false,
                    "This ID is already registered as a guide. " +
                    "Please use your guide credentials to log in.", null);
        } catch (SQLException e) {
            // If the guides check fails for any reason, log and continue — it's a
            // best-effort guard; the unique constraint on visitors.id_number is the
            // definitive safeguard.
            System.out.println("registerVisitor (guide-check) error: " + e.getMessage());
        }

        try {
            PreparedStatement st = connection.prepareStatement(
                "INSERT INTO visitors (id_number, first_name, last_name, email, phone) " +
                "VALUES (?, ?, ?, ?, ?)");
            st.setString(1, req.getIdNumber());
            st.setString(2, req.getFirstName());
            st.setString(3, req.getLastName());
            st.setString(4, req.getEmail());
            st.setString(5, req.getPhone());
            st.executeUpdate();
        } catch (SQLException e) {
            // A duplicate-key violation means the SELECT check passed but two concurrent
            // registrations raced, or the ID is in a table the check didn't cover.
            // Surface a user-friendly message rather than the raw SQL error.
            boolean isDuplicate =
                (e instanceof java.sql.SQLIntegrityConstraintViolationException)
                || (e.getMessage() != null && e.getMessage().toLowerCase().contains("duplicate"));
            if (isDuplicate) {
                return new RegisterResult(false,
                    "An account with this ID already exists. Please log in instead.", null);
            }
            System.out.println("registerVisitor (insert) error: " + e.getMessage());
            return new RegisterResult(false,
                "Could not save your account. Please try again.", null);
        }

        if (req.isSubscriber()) {
            try {
                PreparedStatement st = connection.prepareStatement(
                    "INSERT INTO subscribers " +
                    "(id_number, first_name, last_name, email, phone, credit_card, family_size) " +
                    "VALUES (?, ?, ?, ?, ?, ?, ?)",
                    Statement.RETURN_GENERATED_KEYS);
                st.setString(1, req.getIdNumber());
                st.setString(2, req.getFirstName());
                st.setString(3, req.getLastName());
                st.setString(4, req.getEmail());
                st.setString(5, req.getPhone() != null ? req.getPhone() : "");
                st.setString(6, req.getCreditCard() != null ? req.getCreditCard() : "");
                st.setInt(7,    req.getFamilySize());
                st.executeUpdate();
                ResultSet keys = st.getGeneratedKeys();
                int subId = keys.next() ? keys.getInt(1) : -1;
                return new RegisterResult(true,
                    "Account created! Welcome to GoNature.",
                    subId > 0 ? subId : null);
            } catch (SQLException e) {
                System.out.println("registerVisitor (subscriber) error: " + e.getMessage());
                // visitor was saved; subscriber step failed — still return partial success
                return new RegisterResult(true,
                    "Account created, but subscriber registration failed: " + e.getMessage(),
                    null);
            }
        }

        return new RegisterResult(true, "Account created! Welcome to GoNature.", null);
    }

    /**
     * updates the visit date, visitor count, and visit type of a live order.
     * the {@code WHERE} clause restricts to pending/confirmed so completed orders can't be modified.
     * GROUP orders additionally require the order's {@code visitor_id} to be an approved guide.
     *
     * @param orderId     the order's primary-key ID
     * @param newDate     the new visit date in {@code yyyy-MM-dd} format
     * @param newVisitors the new number of visitors
     * @param orderType   display visit type: {@code "SOLO"}, {@code "FAMILY"}, or {@code "GROUP"}
     * @return {@code null} on success; a human-readable error message on failure
     */
    public String updateOrder(int orderId, String newDate, int newVisitors, String orderType) {
        if (connection == null) return "We can't reach the database right now. Please try again in a moment.";
        try {
            // Fetch the order's current status and visitor_id so we can validate before updating.
            PreparedStatement chk = connection.prepareStatement(
                "SELECT status, visitor_id FROM orders WHERE id=?");
            chk.setInt(1, orderId);
            ResultSet rs = chk.executeQuery();
            if (!rs.next())
                return "We couldn't find order #" + orderId + ". Please check the number and try again.";
            String currentStatus = rs.getString("status");
            String visitorId     = rs.getString("visitor_id");

            if ("completed".equalsIgnoreCase(currentStatus) ||
                "cancelled".equalsIgnoreCase(currentStatus) ||
                "no_show".equalsIgnoreCase(currentStatus)) {
                return "Cannot update a " + currentStatus + " order. " +
                       "Only pending or confirmed orders can be modified.";
            }

            // GROUP type is only valid when the booking visitor is an approved guide.
            if ("GROUP".equalsIgnoreCase(orderType)) {
                PreparedStatement guideChk = connection.prepareStatement(
                    "SELECT 1 FROM guides WHERE id_number = ? AND is_approved = 1 LIMIT 1");
                guideChk.setString(1, visitorId);
                if (!guideChk.executeQuery().next())
                    return "This order can't be changed to GROUP because visitor " + visitorId +
                           " isn't a registered, approved guide.";
            }

            PreparedStatement st;
            if (orderType == null || orderType.trim().isEmpty()) {
                // Caller did not supply a type (e.g. ServiceRep screen) — leave order_type unchanged.
                st = connection.prepareStatement(
                    "UPDATE orders SET visit_date=?, num_visitors=? " +
                    "WHERE id=? AND status IN ('pending','confirmed')");
                st.setString(1, newDate);
                st.setInt(2,    newVisitors);
                st.setInt(3,    orderId);
            } else {
                String dbType = toDbOrderType(orderType);
                st = connection.prepareStatement(
                    "UPDATE orders SET visit_date=?, num_visitors=?, order_type=? " +
                    "WHERE id=? AND status IN ('pending','confirmed')");
                st.setString(1, newDate);
                st.setInt(2,    newVisitors);
                st.setString(3, dbType);
                st.setInt(4,    orderId);
            }
            if (st.executeUpdate() != 1)
                return "Update failed. The order may have changed status — please refresh and try again.";
            return null; // success
        } catch (SQLException e) {
            System.out.println("updateOrder error: " + e.getMessage());
            return "Something went wrong on our end. Please try again.";
        }
    }

    // ── All live orders (Management Dashboard) ───────────────────────────────

    /**
     * returns every order from the live {@code orders} table, newest first.
     *
     * @return a list of {@link OrderDetail}; empty on error or no data
     */
    public List<OrderDetail> getAllLiveOrders() {
        List<OrderDetail> list = new ArrayList<>();
        if (connection == null) return list;
        try {
            PreparedStatement st = connection.prepareStatement(
                "SELECT o.id, p.name AS park_name, o.visitor_id, " +
                "       DATE_FORMAT(o.visit_date, '%Y-%m-%d')    AS visit_date, " +
                "       TIME_FORMAT(o.visit_time, '%H:%i')        AS visit_time, " +
                "       o.num_visitors, " +
                "       CASE UPPER(o.order_type) WHEN 'INDIVIDUAL' THEN 'SOLO' WHEN 'SOLO' THEN 'SOLO' WHEN 'FAMILY' THEN 'FAMILY' WHEN 'GROUP' THEN 'GROUP' ELSE UPPER(o.order_type) END AS order_type, " +
                "       UPPER(o.status)     AS status," +
                "       o.email, " +
                "       DATE_FORMAT(o.created_at, '%Y-%m-%d %H:%i') AS created_at " +
                "FROM orders o JOIN parks p ON p.id = o.park_id " +
                "ORDER BY o.created_at DESC");
            ResultSet rs = st.executeQuery();
            while (rs.next()) {
                list.add(new OrderDetail(
                    rs.getInt("id"),          rs.getString("park_name"),
                    rs.getString("visitor_id"), rs.getString("visit_date"),
                    rs.getString("visit_time"), rs.getInt("num_visitors"),
                    rs.getString("order_type"), rs.getString("status"),
                    rs.getString("email"),      rs.getString("created_at")));
            }
        } catch (SQLException e) {
            System.out.println("getAllLiveOrders error: " + e.getMessage());
        }
        return list;
    }

    // ── Today's reservations (Employee screen) ───────────────────────────────

    /**
     * returns all orders scheduled for today at the given park, sorted by visit time.
     *
     * @param parkId the park's primary-key ID
     * @return a list of {@link OrderDetail}; empty on error or no data
     */
    public List<OrderDetail> getTodayOrders(int parkId) {
        List<OrderDetail> list = new ArrayList<>();
        if (connection == null) return list;
        try {
            PreparedStatement st = connection.prepareStatement(
                "SELECT o.id, p.name AS park_name, o.visitor_id, " +
                "       DATE_FORMAT(o.visit_date, '%Y-%m-%d')    AS visit_date, " +
                "       TIME_FORMAT(o.visit_time, '%H:%i')        AS visit_time, " +
                "       o.num_visitors, " +
                "       CASE UPPER(o.order_type) WHEN 'INDIVIDUAL' THEN 'SOLO' WHEN 'SOLO' THEN 'SOLO' WHEN 'FAMILY' THEN 'FAMILY' WHEN 'GROUP' THEN 'GROUP' ELSE UPPER(o.order_type) END AS order_type, " +
                "       UPPER(o.status)     AS status," +
                "       o.email, " +
                "       DATE_FORMAT(o.created_at, '%Y-%m-%d %H:%i') AS created_at " +
                "FROM orders o JOIN parks p ON p.id = o.park_id " +
                "WHERE o.park_id = ? AND o.visit_date = CURDATE() " +
                "ORDER BY o.visit_time, o.id");
            st.setInt(1, parkId);
            ResultSet rs = st.executeQuery();
            while (rs.next()) {
                list.add(new OrderDetail(
                    rs.getInt("id"),          rs.getString("park_name"),
                    rs.getString("visitor_id"), rs.getString("visit_date"),
                    rs.getString("visit_time"), rs.getInt("num_visitors"),
                    rs.getString("order_type"), rs.getString("status"),
                    rs.getString("email"),      rs.getString("created_at")));
            }
        } catch (SQLException e) {
            System.out.println("getTodayOrders error: " + e.getMessage());
        }
        return list;
    }

    /**
     * returns how many visitors are currently inside the park right now — i.e. visitors
     * who have been checked in ({@code entry_time} set) but have not yet registered an exit
     * ({@code exit_time} still null), for today's date.
     *
     * @param parkId the park's primary-key ID
     * @return the total number of visitors currently inside, or {@code 0} on DB error
     */
    public int getCurrentOccupancy(int parkId) {
        if (connection == null) return 0;
        try (PreparedStatement st = connection.prepareStatement(
                "SELECT COALESCE(SUM(num_visitors), 0) AS inside " +
                "FROM orders " +
                "WHERE park_id = ? AND visit_date = CURDATE() " +
                "  AND status = 'completed' AND entry_time IS NOT NULL AND exit_time IS NULL")) {
            st.setInt(1, parkId);
            try (ResultSet rs = st.executeQuery()) {
                if (rs.next()) return rs.getInt("inside");
            }
            return 0;
        } catch (SQLException e) {
            System.out.println("getCurrentOccupancy error: " + e.getMessage());
            return 0;
        }
    }

    // ── Waiting list (Employee screen) ───────────────────────────────────────

    /**
     * returns active waiting-list entries for the park, oldest request first.
     * status is hard-coded to {@code "WAITING"} and email is resolved from the visitor
     * tables, since the {@code waiting_list} table itself has no status or email column.
     * promoted visitors are removed from the table outright, so every remaining row is active.
     *
     * @param parkId the park's primary-key ID
     * @return a list of {@link OrderDetail} with status {@code "WAITING"}; empty on error
     */
    public List<OrderDetail> getWaitingList(int parkId) {
        List<OrderDetail> list = new ArrayList<>();
        if (connection == null) return list;
        try {
            PreparedStatement st = connection.prepareStatement(
                "SELECT wl.id, p.name AS park_name, wl.visitor_id, " +
                "       DATE_FORMAT(wl.visit_date, '%Y-%m-%d')    AS visit_date, " +
                "       TIME_FORMAT(wl.visit_time, '%H:%i')        AS visit_time, " +
                "       wl.num_visitors, " +
                "       CASE UPPER(wl.order_type) WHEN 'INDIVIDUAL' THEN 'SOLO' WHEN 'SOLO' THEN 'SOLO' WHEN 'FAMILY' THEN 'FAMILY' WHEN 'GROUP' THEN 'GROUP' ELSE UPPER(wl.order_type) END AS order_type, " +
                "       'WAITING'            AS status, " +
                "       DATE_FORMAT(wl.created_at, '%Y-%m-%d %H:%i') AS created_at " +
                "FROM waiting_list wl JOIN parks p ON p.id = wl.park_id " +
                "WHERE wl.park_id = ? " +
                "ORDER BY wl.visit_date, wl.visit_time, wl.created_at");
            st.setInt(1, parkId);
            ResultSet rs = st.executeQuery();
            while (rs.next()) {
                String visitorId = rs.getString("visitor_id");
                list.add(new OrderDetail(
                    rs.getInt("id"),          rs.getString("park_name"),
                    visitorId,                  rs.getString("visit_date"),
                    rs.getString("visit_time"), rs.getInt("num_visitors"),
                    rs.getString("order_type"), rs.getString("status"),
                    getVisitorEmail(visitorId), rs.getString("created_at")));
            }
        } catch (SQLException e) {
            System.out.println("getWaitingList error: " + e.getMessage());
        }
        return list;
    }

    /**
     * resolves a person's contact email from their government ID, checking the
     * visitors, subscribers, and guides tables in turn.
     *
     * @param visitorId the government ID number
     * @return the email address, or an empty string if none is on file
     */
    private String getVisitorEmail(String visitorId) {
        if (connection == null || visitorId == null) return "";
        String[] queries = {
            "SELECT email FROM visitors    WHERE id_number = ?",
            "SELECT email FROM subscribers WHERE id_number = ?",
            "SELECT email FROM guides      WHERE id_number = ?"
        };
        for (String q : queries) {
            try (PreparedStatement st = connection.prepareStatement(q)) {
                st.setString(1, visitorId);
                try (ResultSet rs = st.executeQuery()) {
                    if (rs.next()) {
                        String email = rs.getString("email");
                        if (email != null && !email.trim().isEmpty()) return email.trim();
                    }
                }
            } catch (SQLException e) {
                System.out.println("getVisitorEmail error: " + e.getMessage());
            }
        }
        return "";
    }

    // ── Guide management ──────────────────────────────────────────────────────

    /**
     * registers a new guide with {@code is_approved = 0} (pending manager approval).
     *
     * @param req the registration details from the service representative
     * @return {@code null} on success; a human-readable error message on failure
     */
    public String registerGuide(RegisterGuideRequest req) {
        if (connection == null) return "We can't reach the database right now. Please try again in a moment.";
        try {
            PreparedStatement chk = connection.prepareStatement(
                "SELECT 1 FROM guides WHERE id_number = ? LIMIT 1");
            chk.setString(1, req.getIdNumber());
            if (chk.executeQuery().next())
                return "A guide with this ID number is already registered.";

            PreparedStatement st = connection.prepareStatement(
                "INSERT INTO guides (id_number, first_name, last_name, email, phone, is_approved) " +
                "VALUES (?, ?, ?, ?, ?, 0)");
            st.setString(1, req.getIdNumber());
            st.setString(2, req.getFirstName());
            st.setString(3, req.getLastName());
            st.setString(4, req.getEmail());
            st.setString(5, req.getPhone());
            st.executeUpdate();
            return null; // success
        } catch (SQLException e) {
            System.out.println("registerGuide error: " + e.getMessage());
            return "Something went wrong on our end. Please try again.";
        }
    }

    /**
     * returns all rows in the {@code guides} table — pending first, then alphabetically.
     *
     * @return list of {@link GuideDetail}; empty on error or no data
     */
    public List<GuideDetail> getAllGuides() {
        List<GuideDetail> list = new ArrayList<>();
        if (connection == null) return list;
        try {
            PreparedStatement st = connection.prepareStatement(
                "SELECT id_number, first_name, last_name, email, phone, is_approved " +
                "FROM guides ORDER BY is_approved ASC, last_name ASC");
            ResultSet rs = st.executeQuery();
            while (rs.next()) {
                list.add(new GuideDetail(
                    rs.getString("id_number"),
                    rs.getString("first_name"),
                    rs.getString("last_name"),
                    rs.getString("email"),
                    rs.getString("phone"),
                    rs.getInt("is_approved")));
            }
        } catch (SQLException e) {
            System.out.println("getAllGuides error: " + e.getMessage());
        }
        return list;
    }

    /**
     * approves a guide by setting {@code is_approved = 1}.
     *
     * @param idNumber the guide's government ID
     * @return {@code null} on success; a human-readable error message on failure
     */
    public String approveGuide(String idNumber) {
        if (connection == null) return "We can't reach the database right now. Please try again in a moment.";
        try {
            PreparedStatement st = connection.prepareStatement(
                "UPDATE guides SET is_approved = 1 WHERE id_number = ?");
            st.setString(1, idNumber);
            if (st.executeUpdate() == 0)
                return "We couldn't find a guide with ID " + idNumber + ".";
            return null;
        } catch (SQLException e) {
            System.out.println("approveGuide error: " + e.getMessage());
            return "Something went wrong on our end. Please try again.";
        }
    }

    /**
     * rejects (deletes) a guide row from the {@code guides} table.
     *
     * @param idNumber the guide's government ID
     * @return {@code null} on success; a human-readable error message on failure
     */
    public String rejectGuide(String idNumber) {
        if (connection == null) return "We can't reach the database right now. Please try again in a moment.";
        try {
            PreparedStatement st = connection.prepareStatement(
                "DELETE FROM guides WHERE id_number = ?");
            st.setString(1, idNumber);
            if (st.executeUpdate() == 0)
                return "We couldn't find a guide with ID " + idNumber + ".";
            return null;
        } catch (SQLException e) {
            System.out.println("rejectGuide error: " + e.getMessage());
            return "Something went wrong on our end. Please try again.";
        }
    }

    // ── Park settings approval workflow ───────────────────────────────────────

    /**
     * inserts a pending park-settings change request into {@code park_settings_requests}.
     * the current park values are NOT modified; the department manager must approve first.
     *
     * @param req the proposed settings from the park manager
     * @return {@code null} on success; a human-readable error message on failure
     */
    public String submitParkSettingsRequest(ParkSettingsRequest req) {
        if (connection == null) return "We can't reach the database right now. Please try again in a moment.";
        try {
            PreparedStatement st = connection.prepareStatement(
                "INSERT INTO park_settings_requests " +
                "(park_id, requested_by, capacity, max_orders, visit_duration_hours, full_price) " +
                "VALUES (?, ?, ?, ?, ?, ?)");
            st.setInt(1,    req.getParkId());
            st.setString(2, req.getRequestedBy());
            st.setInt(3,    req.getCapacity());
            st.setInt(4,    req.getMaxOrders());
            st.setInt(5,    req.getVisitDurationHours());
            st.setDouble(6, req.getFullPrice());
            st.executeUpdate();
            return null; // success
        } catch (SQLException e) {
            System.out.println("submitParkSettingsRequest error: " + e.getMessage());
            return "Something went wrong on our end. Please try again.";
        }
    }

    /**
     * returns all pending park-settings requests, newest first,
     * joined with the parks table so the park name is included.
     *
     * @return list of {@link ParkSettingsRequest}; empty on error or no pending rows
     */
    public List<ParkSettingsRequest> getPendingSettingsRequests() {
        List<ParkSettingsRequest> list = new ArrayList<>();
        if (connection == null) return list;
        try {
            PreparedStatement st = connection.prepareStatement(
                "SELECT r.id, r.park_id, p.name AS park_name, r.requested_by, " +
                "       r.capacity, r.max_orders, r.visit_duration_hours, r.full_price, " +
                "       r.status, DATE_FORMAT(r.requested_at, '%Y-%m-%d %H:%i') AS requested_at " +
                "FROM park_settings_requests r " +
                "JOIN parks p ON p.id = r.park_id " +
                "WHERE r.status = 'pending' " +
                "ORDER BY r.requested_at DESC");
            ResultSet rs = st.executeQuery();
            while (rs.next()) {
                list.add(new ParkSettingsRequest(
                    rs.getInt("id"),
                    rs.getInt("park_id"),
                    rs.getString("park_name"),
                    rs.getString("requested_by"),
                    rs.getInt("capacity"),
                    rs.getInt("max_orders"),
                    rs.getInt("visit_duration_hours"),
                    rs.getDouble("full_price"),
                    rs.getString("status"),
                    rs.getString("requested_at")));
            }
        } catch (SQLException e) {
            System.out.println("getPendingSettingsRequests error: " + e.getMessage());
        }
        return list;
    }

    /**
     * approves a pending settings request: applies the proposed values to the
     * {@code parks} table and marks the request as {@code 'approved'}.
     * both updates run in a single transaction so they stay in sync.
     *
     * @param requestId the primary key of the {@code park_settings_requests} row
     * @return {@code null} on success; a human-readable error message on failure
     */
    public String approveSettingsRequest(int requestId) {
        if (connection == null) return "We can't reach the database right now. Please try again in a moment.";
        try {
            // Fetch the request row first so we have the values to apply.
            PreparedStatement fetch = connection.prepareStatement(
                "SELECT park_id, capacity, max_orders, visit_duration_hours, full_price, status " +
                "FROM park_settings_requests WHERE id = ?");
            fetch.setInt(1, requestId);
            ResultSet rs = fetch.executeQuery();
            if (!rs.next())
                return "Request #" + requestId + " not found.";
            if (!"pending".equalsIgnoreCase(rs.getString("status")))
                return "Request #" + requestId + " is no longer pending.";

            int    parkId   = rs.getInt("park_id");
            int    cap      = rs.getInt("capacity");
            int    maxOrd   = rs.getInt("max_orders");
            int    dur      = rs.getInt("visit_duration_hours");
            double price    = rs.getDouble("full_price");

            connection.setAutoCommit(false);
            try {
                // Apply values to the live parks table.
                PreparedStatement upPark = connection.prepareStatement(
                    "UPDATE parks SET capacity=?, max_orders=?, visit_duration_hours=?, full_price=? " +
                    "WHERE id=?");
                upPark.setInt(1, cap);
                upPark.setInt(2, maxOrd);
                upPark.setInt(3, dur);
                upPark.setDouble(4, price);
                upPark.setInt(5, parkId);
                upPark.executeUpdate();

                // Mark the request approved with the review timestamp.
                PreparedStatement upReq = connection.prepareStatement(
                    "UPDATE park_settings_requests " +
                    "SET status='approved', reviewed_at=NOW() WHERE id=?");
                upReq.setInt(1, requestId);
                upReq.executeUpdate();

                connection.commit();
            } catch (SQLException ex) {
                connection.rollback();
                throw ex;
            } finally {
                connection.setAutoCommit(true);
            }
            return null; // success
        } catch (SQLException e) {
            System.out.println("approveSettingsRequest error: " + e.getMessage());
            return "Something went wrong on our end. Please try again.";
        }
    }

    /**
     * rejects a pending settings request by setting its status to {@code 'rejected'}.
     * the live park values are not touched.
     *
     * @param requestId the primary key of the {@code park_settings_requests} row
     * @return {@code null} on success; a human-readable error message on failure
     */
    public String rejectSettingsRequest(int requestId) {
        if (connection == null) return "We can't reach the database right now. Please try again in a moment.";
        try {
            PreparedStatement chk = connection.prepareStatement(
                "SELECT status FROM park_settings_requests WHERE id = ?");
            chk.setInt(1, requestId);
            ResultSet rs = chk.executeQuery();
            if (!rs.next())
                return "Request #" + requestId + " not found.";
            if (!"pending".equalsIgnoreCase(rs.getString("status")))
                return "Request #" + requestId + " is no longer pending.";

            PreparedStatement st = connection.prepareStatement(
                "UPDATE park_settings_requests " +
                "SET status='rejected', reviewed_at=NOW() WHERE id=?");
            st.setInt(1, requestId);
            st.executeUpdate();
            return null; // success
        } catch (SQLException e) {
            System.out.println("rejectSettingsRequest error: " + e.getMessage());
            return "Something went wrong on our end. Please try again.";
        }
    }

    // ── Promotions approval workflow ──────────────────────────────────────────

    /**
     * inserts a new promotion into {@code promotions} with status {@code PENDING}.
     * the promotion does not affect pricing until a department manager approves it.
     *
     * @param promo the proposed promotion from the park manager
     * @return {@code null} on success; a human-readable error message on failure
     */
    public String submitPromotion(Promotion promo) {
        if (connection == null) return "We can't reach the database right now. Please try again in a moment.";
        try (PreparedStatement st = connection.prepareStatement(
                "INSERT INTO promotions " +
                "(park_id, description, discount_percent, start_date, end_date, status, submitted_by) " +
                "VALUES (?, ?, ?, ?, ?, 'PENDING', ?)")) {
            st.setInt(1,    promo.getParkId());
            st.setString(2, promo.getDescription());
            st.setDouble(3, promo.getDiscountPercent());
            st.setString(4, promo.getStartDate());
            st.setString(5, promo.getEndDate());
            st.setString(6, promo.getSubmittedBy());
            st.executeUpdate();
            return null; // success
        } catch (SQLException e) {
            System.out.println("submitPromotion error: " + e.getMessage());
            return "Something went wrong on our end. Please try again.";
        }
    }

    /**
     * returns all promotions for one park (any status), newest first.
     * used by the park manager's Promotions screen to show their own history.
     *
     * @param parkId the park whose promotions to list
     * @return list of {@link Promotion}; empty on error or none
     */
    public List<Promotion> getPromotionsForPark(int parkId) {
        List<Promotion> list = new ArrayList<>();
        if (connection == null) return list;
        try (PreparedStatement st = connection.prepareStatement(
                "SELECT pr.id, pr.park_id, p.name AS park_name, pr.description, " +
                "       pr.discount_percent, " +
                "       DATE_FORMAT(pr.start_date, '%Y-%m-%d') AS start_date, " +
                "       DATE_FORMAT(pr.end_date,   '%Y-%m-%d') AS end_date, " +
                "       pr.status, pr.submitted_by " +
                "FROM promotions pr JOIN parks p ON p.id = pr.park_id " +
                "WHERE pr.park_id = ? " +
                "ORDER BY pr.created_at DESC")) {
            st.setInt(1, parkId);
            try (ResultSet rs = st.executeQuery()) {
                while (rs.next()) list.add(readPromotion(rs));
            }
        } catch (SQLException e) {
            System.out.println("getPromotionsForPark error: " + e.getMessage());
        }
        return list;
    }

    /**
     * returns all {@code PENDING} promotions across every park, newest first.
     * used by the department manager's Pending Promotions panel.
     *
     * @return list of {@link Promotion}; empty on error or none pending
     */
    public List<Promotion> getPendingPromotions() {
        List<Promotion> list = new ArrayList<>();
        if (connection == null) return list;
        try (PreparedStatement st = connection.prepareStatement(
                "SELECT pr.id, pr.park_id, p.name AS park_name, pr.description, " +
                "       pr.discount_percent, " +
                "       DATE_FORMAT(pr.start_date, '%Y-%m-%d') AS start_date, " +
                "       DATE_FORMAT(pr.end_date,   '%Y-%m-%d') AS end_date, " +
                "       pr.status, pr.submitted_by " +
                "FROM promotions pr JOIN parks p ON p.id = pr.park_id " +
                "WHERE pr.status = 'PENDING' " +
                "ORDER BY pr.created_at DESC")) {
            try (ResultSet rs = st.executeQuery()) {
                while (rs.next()) list.add(readPromotion(rs));
            }
        } catch (SQLException e) {
            System.out.println("getPendingPromotions error: " + e.getMessage());
        }
        return list;
    }

    /**
     * returns the promotions that are live for a given park on a given date —
     * status {@code ACTIVE} and {@code date} within {@code [start_date, end_date]}.
     * ordered by discount descending so element 0 is the best offer.
     *
     * @param parkId the park to look up
     * @param date   the date to test, {@code yyyy-MM-dd}
     * @return list of active {@link Promotion}; empty on error or none active
     */
    public List<Promotion> getActivePromotions(int parkId, String date) {
        List<Promotion> list = new ArrayList<>();
        if (connection == null) return list;
        try (PreparedStatement st = connection.prepareStatement(
                "SELECT pr.id, pr.park_id, p.name AS park_name, pr.description, " +
                "       pr.discount_percent, " +
                "       DATE_FORMAT(pr.start_date, '%Y-%m-%d') AS start_date, " +
                "       DATE_FORMAT(pr.end_date,   '%Y-%m-%d') AS end_date, " +
                "       pr.status, pr.submitted_by " +
                "FROM promotions pr JOIN parks p ON p.id = pr.park_id " +
                "WHERE pr.park_id = ? AND pr.status = 'ACTIVE' " +
                "  AND ? BETWEEN pr.start_date AND pr.end_date " +
                "ORDER BY pr.discount_percent DESC")) {
            st.setInt(1, parkId);
            st.setString(2, date);
            try (ResultSet rs = st.executeQuery()) {
                while (rs.next()) list.add(readPromotion(rs));
            }
        } catch (SQLException e) {
            System.out.println("getActivePromotions error: " + e.getMessage());
        }
        return list;
    }

    /**
     * returns the single best (largest-discount) active promotion for a park on a date,
     * or {@code null} if none applies. wraps {@link #getActivePromotions}.
     *
     * @param parkId the park to look up
     * @param date   the date to test, {@code yyyy-MM-dd}
     * @return the best {@link Promotion}, or {@code null}
     */
    public Promotion getBestActivePromotion(int parkId, String date) {
        List<Promotion> active = getActivePromotions(parkId, date);
        return active.isEmpty() ? null : active.get(0);
    }

    /**
     * approves a pending promotion by setting its status to {@code ACTIVE}.
     *
     * @param promoId the primary key of the {@code promotions} row
     * @return {@code null} on success; a human-readable error message on failure
     */
    public String approvePromotion(int promoId) {
        return setPromotionStatus(promoId, "ACTIVE");
    }

    /**
     * rejects a pending promotion by setting its status to {@code REJECTED}.
     *
     * @param promoId the primary key of the {@code promotions} row
     * @return {@code null} on success; a human-readable error message on failure
     */
    public String rejectPromotion(int promoId) {
        return setPromotionStatus(promoId, "REJECTED");
    }

    /**
     * shared helper for {@link #approvePromotion}/{@link #rejectPromotion}: validates the row
     * is still PENDING, then flips its status.
     */
    private String setPromotionStatus(int promoId, String newStatus) {
        if (connection == null) return "We can't reach the database right now. Please try again in a moment.";
        try {
            try (PreparedStatement chk = connection.prepareStatement(
                    "SELECT status FROM promotions WHERE id = ?")) {
                chk.setInt(1, promoId);
                try (ResultSet rs = chk.executeQuery()) {
                    if (!rs.next())
                        return "Promotion #" + promoId + " not found.";
                    if (!"PENDING".equalsIgnoreCase(rs.getString("status")))
                        return "Promotion #" + promoId + " is no longer pending.";
                }
            }
            try (PreparedStatement st = connection.prepareStatement(
                    "UPDATE promotions SET status = ? WHERE id = ?")) {
                st.setString(1, newStatus);
                st.setInt(2, promoId);
                st.executeUpdate();
            }
            return null; // success
        } catch (SQLException e) {
            System.out.println("setPromotionStatus error: " + e.getMessage());
            return "Something went wrong on our end. Please try again.";
        }
    }

    /** maps the current row of a promotions result set (with park_name) to a {@link Promotion}. */
    private Promotion readPromotion(ResultSet rs) throws SQLException {
        return new Promotion(
            rs.getInt("id"),
            rs.getInt("park_id"),
            rs.getString("park_name"),
            rs.getString("description"),
            rs.getDouble("discount_percent"),
            rs.getString("start_date"),
            rs.getString("end_date"),
            rs.getString("status"),
            rs.getString("submitted_by"));
    }
}
