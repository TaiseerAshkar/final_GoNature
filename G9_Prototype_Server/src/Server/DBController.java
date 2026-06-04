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
        try {
            PreparedStatement st = connection.prepareStatement(
                "SELECT id, name, capacity, max_orders, visit_duration_hours, full_price " +
                "FROM parks WHERE id = ?");
            st.setInt(1, parkId);
            ResultSet rs = st.executeQuery();
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
        if (connection == null) return -1;
        try {
            PreparedStatement st = connection.prepareStatement(
                "SELECT p.capacity - COALESCE(SUM(o.num_visitors), 0) AS available " +
                "FROM parks p " +
                "LEFT JOIN orders o " +
                "       ON  o.park_id = p.id " +
                "       AND o.visit_date = ? " +
                "       AND o.status NOT IN ('no_show') " +
                "       AND TIME(o.visit_time) < ADDTIME(TIME(?), SEC_TO_TIME(p.visit_duration_hours * 3600)) " +
                "       AND ADDTIME(TIME(o.visit_time), SEC_TO_TIME(p.visit_duration_hours * 3600)) > TIME(?) " +
                "WHERE p.id = ? " +
                "GROUP BY p.id, p.capacity");
            st.setString(1, visitDate);
            st.setString(2, visitTime);
            st.setString(3, visitTime);
            st.setInt(4, parkId);
            ResultSet rs = st.executeQuery();
            if (rs.next()) return rs.getInt("available");
            // No rows means park ID doesn't exist
            return -1;
        } catch (SQLException e) {
            System.out.println("getAvailableSpots error: " + e.getMessage());
            return -1;
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
     * cancels an order and notifies the first waiting-list entry for the freed slot.
     * only pending/confirmed orders may be cancelled — completed/already-cancelled rows are rejected.
     *
     * <p>cancellation does NOT free up capacity: the slot stays counted in
     * {@link #getAvailableSpots} so no new booking can take the vacated position.
     * no waiting-list notification is sent; cancellation is a record-keeping action only.
     *
     * @param orderId the primary-key ID of the order to cancel
     * @return a {@link CancelResult} indicating success or failure
     */
    public CancelResult cancelOrder(int orderId) {
        if (connection == null) return new CancelResult(false, null);

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

        // capacity slot is NOT freed — no waiting-list query needed
        return new CancelResult(true, null);
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
        try {
            PreparedStatement st = connection.prepareStatement(
                "SELECT 1 FROM subscribers WHERE id_number = ? LIMIT 1");
            st.setString(1, visitorId);
            return st.executeQuery().next();
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
        try {
            PreparedStatement st = connection.prepareStatement(
                "SELECT 1 FROM guides WHERE id_number = ? LIMIT 1");
            st.setString(1, visitorId);
            ResultSet rs = st.executeQuery();
            return rs.next();
        } catch (SQLException e) {
            System.out.println("isRegisteredGuide error: " + e.getMessage());
            return false;
        }
    }

    // ── Entry control ─────────────────────────────────────────────────────────

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
                "Database not connected.");

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
                    "Visitor " + visitorId + " is already checked in at this park today.");
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
                int    orderId      = rs.getInt("id");
                int    numVis       = rs.getInt("num_visitors");
                // map DB value to display name (SOLO / FAMILY / GROUP)
                String oTypeRaw     = rs.getString("order_type").toUpperCase();
                String oType        = "INDIVIDUAL".equals(oTypeRaw) ? "SOLO" : oTypeRaw;
                String parkName     = rs.getString("park_name");
                double fullPrice    = rs.getDouble("full_price");
                boolean isGuide     = isRegisteredGuide(visitorId);
                boolean isSubscriber = isRegisteredSubscriber(visitorId);
                double total        = calculateEntryPrice(true, oType, numVis, fullPrice, isGuide, isSubscriber);
                String note         = buildEntryPricingNote(true, oType, numVis, isGuide, isSubscriber);

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
                "Database error during booking lookup.");
        }

        // no booking found — treat as walk-in
        int    numVis        = req.getNumVisitors();
        String wDisplayType  = req.getOrderType().toUpperCase(); // SOLO / FAMILY / GROUP (for display + pricing)
        String wType         = toDbOrderType(req.getOrderType()); // individual / family / group (for DB insert)
        String parkName  = "";
        double fullPrice = 0;

        try {
            PreparedStatement st = connection.prepareStatement(
                "SELECT name, full_price FROM parks WHERE id=?");
            st.setInt(1, parkId);
            ResultSet rs = st.executeQuery();
            if (!rs.next())
                return new EntryResult(false, false, -1, "", visitorId, 0, "", 0,
                    "Park not found.");
            parkName  = rs.getString("name");
            fullPrice = rs.getDouble("full_price");
        } catch (SQLException e) {
            System.out.println("checkInVisitor (park lookup) error: " + e.getMessage());
            return new EntryResult(false, false, -1, "", visitorId, 0, "", 0,
                "Database error during park lookup.");
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
            double total = calculateEntryPrice(false, wDisplayType, numVis, fullPrice, isGuide, isSubscriber);
            String note  = buildEntryPricingNote(false, wDisplayType, numVis, isGuide, isSubscriber);
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
     * <p>Pricing rules:
     * <ul>
     *   <li>GROUP by guide — guide enters free; others: 25% advance discount (pre-booked)
     *       or 10% walk-in discount.</li>
     *   <li>Any visitor, pre-booked — 15% advance-booking discount.</li>
     *   <li>Subscriber (Family Member Club), walk-in — 15% membership discount
     *       (the tangible benefit of subscribing).</li>
     *   <li>Regular visitor, walk-in — full price.</li>
     * </ul>
     *
     * @param prebooked    {@code true} for a confirmed pre-booking; {@code false} for walk-in
     * @param orderType    the visit type in uppercase ({@code "SOLO"} or {@code "GROUP"})
     * @param numVisitors  total visitors including any guide
     * @param fullPrice    the park's per-person full price
     * @param isGuide      {@code true} if led by a registered guide
     * @param isSubscriber {@code true} if the visitor holds a Family Member Club subscription
     * @return the total price to charge
     */
    private double calculateEntryPrice(boolean prebooked, String orderType,
                                       int numVisitors, double fullPrice,
                                       boolean isGuide, boolean isSubscriber) {
        if ("GROUP".equals(orderType.toUpperCase()) && isGuide) {
            int paying = Math.max(0, numVisitors - 1);
            return prebooked
                ? paying * fullPrice * 0.75   // guide free + 25% advance discount
                : paying * fullPrice * 0.90;  // guide free + 10% walk-in discount
        }
        if (prebooked)
            return numVisitors * fullPrice * 0.85;  // 15% advance discount for everyone
        if (isSubscriber)
            return numVisitors * fullPrice * 0.85;  // 15% membership walk-in benefit
        return numVisitors * fullPrice;             // full price for regular walk-in
    }

    /**
     * builds a human-readable pricing note for the entry result card.
     * called server-side so the note accurately reflects guide and subscriber status.
     */
    private String buildEntryPricingNote(boolean prebooked, String orderType,
                                         int numVisitors, boolean isGuide, boolean isSubscriber) {
        if ("GROUP".equals(orderType.toUpperCase()) && isGuide) {
            int paying = Math.max(0, numVisitors - 1);
            return prebooked
                ? "Pre-booked group: guide enters free. " + paying + " paying visitor(s) — 25% advance discount."
                : "Walk-in group: guide enters free. " + paying + " paying visitor(s) — 10% walk-in discount.";
        }
        if (prebooked)
            return "Pre-booked: 15% advance-booking discount applied.";
        if (isSubscriber)
            return "Walk-in: Family Member Club — 15% subscriber discount applied.";
        return "Walk-in: full price (no advance booking discount).";
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
        try {
            PreparedStatement st = connection.prepareStatement(
                "UPDATE orders SET exit_time=NOW() " +
                "WHERE visitor_id=? AND park_id=? AND visit_date=CURDATE() " +
                "  AND status='completed' AND entry_time IS NOT NULL AND exit_time IS NULL " +
                "ORDER BY entry_time DESC LIMIT 1");
            st.setString(1, visitorId.trim());
            st.setInt(2, parkId);
            return st.executeUpdate() > 0;
        } catch (SQLException e) {
            System.out.println("registerExit error: " + e.getMessage());
            return false;
        }
    }

    // ── Reports ───────────────────────────────────────────────────────────────

    /**
     * visitor report: total visitors per day per order-type for the last 30 days.
     * each row has a {@code dayLabel} for the chart X-axis and a {@code visitDate} for sort ordering.
     *
     * @param parkId the park to filter by, or {@code null} for all parks
     * @return a list of {@link ReportVisitorRow} objects ordered by date and order type;
     *         empty on error or no data
     */
    public List<ReportVisitorRow> getVisitorReport(Integer parkId) {
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
                "  AND o.visit_date BETWEEN DATE_SUB(CURDATE(), INTERVAL 29 DAY) AND CURDATE() " +
                (parkId != null ? "  AND o.park_id = ? " : "") +
                "GROUP BY o.visit_date, o.order_type " +
                "ORDER BY o.visit_date ASC, o.order_type ASC";
            PreparedStatement st = connection.prepareStatement(sql);
            if (parkId != null) st.setInt(1, parkId);
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
     * cancellation report: all {@code 'cancelled'} and {@code 'no_show'} orders, newest first.
     *
     * @param parkId the park to filter by, or {@code null} for all parks
     * @return a list of {@link ReportCancelRow} objects; empty on error or no data
     */
    public List<ReportCancelRow> getCancelReport(Integer parkId) {
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
                (parkId != null ? "  AND o.park_id = ? " : "") +
                "ORDER BY o.created_at DESC";
            PreparedStatement st = connection.prepareStatement(sql);
            if (parkId != null) st.setInt(1, parkId);
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
     * usage report: average visitors per hour slot per day over the last 30 days, across all parks.
     * the client divides by capacity × 100 to get percentage-of-capacity for the line chart.
     *
     * @return a list of {@link ReportUsageRow} objects ordered by park name then hour slot;
     *         empty on error or no data
     */
    public List<ReportUsageRow> getUsageReport() {
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
                "  AND o.visit_date BETWEEN DATE_SUB(CURDATE(), INTERVAL 29 DAY) AND CURDATE() " +
                "GROUP BY p.id, p.name, p.capacity, TIME_FORMAT(o.visit_time,'%H:00') " +
                "ORDER BY p.name ASC, hour_slot ASC");
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
            return new RegisterResult(false, "Database not connected.", null);

        // check for duplicate ID before inserting so the error message is user-friendly
        try {
            PreparedStatement st = connection.prepareStatement(
                "SELECT 1 FROM visitors WHERE id_number = ? LIMIT 1");
            st.setString(1, req.getIdNumber());
            if (st.executeQuery().next())
                return new RegisterResult(false,
                    "An account with this ID already exists. Please log in instead.", null);
        } catch (SQLException e) {
            System.out.println("registerVisitor (check) error: " + e.getMessage());
            return new RegisterResult(false, "Database error during registration.", null);
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
        if (connection == null) return "Database connection unavailable.";
        try {
            // Fetch the order's current status and visitor_id so we can validate before updating.
            PreparedStatement chk = connection.prepareStatement(
                "SELECT status, visitor_id FROM orders WHERE id=?");
            chk.setInt(1, orderId);
            ResultSet rs = chk.executeQuery();
            if (!rs.next())
                return "Order #" + orderId + " not found.";
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
                    return "Cannot set type to GROUP: visitor " + visitorId +
                           " is not a registered approved guide.";
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
            return "Database error: " + e.getMessage();
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

    // ── Waiting list (Employee screen) ───────────────────────────────────────

    /**
     * returns active waiting-list entries for the park, oldest request first.
     * status is hard-coded to {@code "WAITING"} since waiting_list rows don't use the orders status column.
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
                "       wl.email, " +
                "       DATE_FORMAT(wl.created_at, '%Y-%m-%d %H:%i') AS created_at " +
                "FROM waiting_list wl JOIN parks p ON p.id = wl.park_id " +
                "WHERE wl.park_id = ? AND wl.status = 'waiting' " +
                "ORDER BY wl.visit_date, wl.visit_time, wl.created_at");
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
            System.out.println("getWaitingList error: " + e.getMessage());
        }
        return list;
    }
}
