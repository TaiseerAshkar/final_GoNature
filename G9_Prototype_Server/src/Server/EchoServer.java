package Server;

import logic.BookingRequest;
import logic.BookingResult;
import logic.CancelResult;
import logic.EntryCheckRequest;
import logic.EntryResult;
import logic.ExitRequest;
import logic.LoginResult;
import logic.Message;
import logic.Order;
import logic.OrderDetail;
import logic.Park;
import logic.RegisterRequest;
import logic.RegisterResult;
import logic.ReportCancelRow;
import logic.ReportUsageRow;
import logic.ReportVisitorRow;
import ocsf.server.AbstractServer;
import ocsf.server.ConnectionToClient;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.List;
import gui.ServerPortFrameController;

/**
 * OCSF server that dispatches incoming {@link Message} objects to the appropriate
 * {@link DBController} methods and sends back typed response messages.
 *
 * <p>a single instance is created by {@link ServerUI}; all connections share the same
 * {@link DBController} singleton. handled message types (incoming → outgoing):
 * <ul>
 *   <li>{@code LOGIN_VISITOR}     → {@code LOGIN_SUCCESS} / {@code LOGIN_FAIL}</li>
 *   <li>{@code LOGIN_USER}        → {@code LOGIN_SUCCESS} / {@code LOGIN_FAIL}</li>
 *   <li>{@code GET_PARKS}         → {@code PARKS_LIST}</li>
 *   <li>{@code BOOK_ORDER}        → {@code BOOKING_CONFIRMED} / {@code BOOKING_FULL} / {@code BOOKING_ERROR}</li>
 *   <li>{@code JOIN_WAITING_LIST} → {@code WAITING_LIST_CONFIRMED} / {@code BOOKING_ERROR}</li>
 *   <li>{@code GET_MY_ORDERS}     → {@code MY_ORDERS_LIST}</li>
 *   <li>{@code CANCEL_ORDER}      → {@code CANCEL_SUCCESS} / {@code CANCEL_FAIL}</li>
 *   <li>{@code CHECK_IN_VISITOR}  → {@code ENTRY_APPROVED} / {@code ENTRY_DENIED}</li>
 *   <li>{@code REGISTER_EXIT}     → {@code EXIT_SUCCESS} / {@code EXIT_FAIL}</li>
 *   <li>{@code GET_VISITOR_REPORT}→ {@code VISITOR_REPORT_DATA}</li>
 *   <li>{@code GET_CANCEL_REPORT} → {@code CANCEL_REPORT_DATA}</li>
 *   <li>{@code GET_USAGE_REPORT}  → {@code USAGE_REPORT_DATA}</li>
 *   <li>{@code UPDATE_ORDER}      → {@code UPDATE_SUCCESS} / {@code UPDATE_FAIL}</li>
 * </ul>
 */
public class EchoServer extends AbstractServer {

    /**
     * @param port the port number to listen on
     */
    public EchoServer(int port) {
        super(port);
    }

    /**
     * dispatches an incoming message to the correct handler.
     * non-{@link Message} objects are silently ignored. exceptions are printed to stderr
     * rather than propagated so a bad request can't crash the server.
     *
     * @param msg    the object received from the client
     * @param client the connection handle used to send the response
     */
    @Override
    public void handleMessageFromClient(Object msg, ConnectionToClient client) {
        System.out.println("Message received from client: " + client);

        if (!(msg instanceof Message)) return;
        Message incoming = (Message) msg;

        try {
            switch (incoming.getType()) {

                case "LOGIN_VISITOR": {
                    String idNumber = (String) incoming.getData();
                    LoginResult result = DBController.getInstance().loginVisitor(idNumber);
                    if (result != null)
                        client.sendToClient(new Message("LOGIN_SUCCESS", result));
                    else
                        client.sendToClient(new Message("LOGIN_FAIL", "ID not registered"));
                    break;
                }

                case "LOGIN_USER": {
                    String[] creds = (String[]) incoming.getData();
                    DBController db = DBController.getInstance();
                    LoginResult result = db.loginUser(creds[0], creds[1]);
                    if (result != null)
                        client.sendToClient(new Message("LOGIN_SUCCESS", result));
                    else
                        client.sendToClient(new Message("LOGIN_FAIL",
                            db.loginUserFailReason != null ? db.loginUserFailReason : "Login failed."));
                    break;
                }

                case "GET_PARKS": {
                    List<Park> parks = DBController.getInstance().getParks();
                    client.sendToClient(new Message("PARKS_LIST", parks));
                    break;
                }

                case "BOOK_ORDER": {
                    if (!(incoming.getData() instanceof BookingRequest)) {
                        client.sendToClient(new Message("BOOKING_ERROR", "Invalid booking data."));
                        break;
                    }
                    BookingRequest req = (BookingRequest) incoming.getData();
                    String validationError = validateBookingRequest(req);
                    if (validationError != null) {
                        client.sendToClient(new Message("BOOKING_ERROR", validationError));
                        break;
                    }
                    int available = DBController.getInstance()
                        .getAvailableSpots(req.getParkId(), req.getVisitDate(), req.getVisitTime());
                    if (available < 0) {
                        client.sendToClient(new Message("BOOKING_ERROR",
                            "Could not check park capacity. Please try again."));
                        break;
                    }
                    if (available < req.getNumVisitors()) {
                        client.sendToClient(new Message("BOOKING_FULL", null));
                        break;
                    }
                    int orderId;
                    try {
                        orderId = DBController.getInstance().createOrder(req);
                    } catch (RuntimeException e) {
                        client.sendToClient(new Message("BOOKING_ERROR", e.getMessage()));
                        break;
                    }
                    if (orderId < 0) {
                        client.sendToClient(new Message("BOOKING_ERROR",
                            "Failed to create booking. Please try again."));
                        break;
                    }
                    client.sendToClient(new Message("BOOKING_CONFIRMED",
                        new BookingResult(orderId, "CONFIRMED", req.getParkName(),
                            req.getVisitDate(), req.getVisitTime(),
                            req.getNumVisitors(), req.getOrderType(), req.getEmail())));
                    // instant notification if the visit is tomorrow — 5 seconds later flip to "sent"
                    String tomorrow = LocalDate.now().plusDays(1)
                        .format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd"));
                    if (req.getVisitDate().equals(tomorrow)) {
                        ServerPortFrameController.logSimulation(
                            "📧 Reminder queued → " + req.getEmail() +
                            "  |  " + req.getParkName() +
                            "  |  " + req.getVisitDate() + " at " + req.getVisitTime() +
                            "  |  Order #" + orderId);
                        ServerPortFrameController.addPendingReminder(orderId);
                        // after 5 seconds, flip "queued" → "sent" in the simulation log
                        final int    fId   = orderId;
                        final String fMail = req.getEmail();
                        final String fPark = req.getParkName();
                        final String fDate = req.getVisitDate();
                        final String fTime = req.getVisitTime();
                        java.util.concurrent.Executors
                            .newSingleThreadScheduledExecutor(r -> {
                                Thread t = new Thread(r, "reminder-upgrade-" + fId);
                                t.setDaemon(true);
                                return t;
                            })
                            .schedule(
                                () -> ServerPortFrameController.upgradeReminderToSent(
                                          fId, fMail, fPark, fDate, fTime),
                                5, java.util.concurrent.TimeUnit.SECONDS);
                    }
                    break;
                }

                case "JOIN_WAITING_LIST": {
                    if (!(incoming.getData() instanceof BookingRequest)) {
                        client.sendToClient(new Message("BOOKING_ERROR", "Invalid booking data."));
                        break;
                    }
                    BookingRequest req = (BookingRequest) incoming.getData();
                    int waitId;
                    try {
                        waitId = DBController.getInstance().addToWaitingList(req);
                    } catch (RuntimeException e) {
                        client.sendToClient(new Message("BOOKING_ERROR", e.getMessage()));
                        break;
                    }
                    if (waitId < 0) {
                        client.sendToClient(new Message("BOOKING_ERROR",
                            "Failed to join waiting list. Please try again."));
                        break;
                    }
                    client.sendToClient(new Message("WAITING_LIST_CONFIRMED",
                        new BookingResult(waitId, "WAITING", req.getParkName(),
                            req.getVisitDate(), req.getVisitTime(),
                            req.getNumVisitors(), req.getOrderType(), req.getEmail())));
                    break;
                }

                case "GET_MY_ORDERS": {
                    String visitorId = (String) incoming.getData();
                    if (visitorId == null || visitorId.trim().isEmpty()) {
                        client.sendToClient(new Message("MY_ORDERS_LIST", new java.util.ArrayList<>()));
                        break;
                    }
                    List<OrderDetail> myOrders =
                        DBController.getInstance().getOrdersByVisitor(visitorId.trim());
                    client.sendToClient(new Message("MY_ORDERS_LIST", myOrders));
                    break;
                }

                case "CANCEL_ORDER": {
                    if (!(incoming.getData() instanceof Integer)) {
                        client.sendToClient(new Message("CANCEL_FAIL", "Invalid order ID."));
                        break;
                    }
                    int orderId = (Integer) incoming.getData();
                    if (orderId <= 0) {
                        client.sendToClient(new Message("CANCEL_FAIL", "Invalid order ID."));
                        break;
                    }
                    CancelResult result = DBController.getInstance().cancelOrder(orderId);
                    if (result.isSuccess()) {
                        client.sendToClient(new Message("CANCEL_SUCCESS", result));
                        // remove any pending reminder for this order from the simulation log
                        ServerPortFrameController.removeReminderForOrder(orderId);
                        // capacity is NOT freed — no waiting-list notification is sent
                    } else {
                        client.sendToClient(new Message("CANCEL_FAIL",
                            "Cannot cancel this order. It may already be cancelled or completed."));
                    }
                    break;
                }

                case "CHECK_IN_VISITOR": {
                    if (!(incoming.getData() instanceof EntryCheckRequest)) {
                        client.sendToClient(new Message("ENTRY_DENIED", "Invalid check-in request."));
                        break;
                    }
                    EntryCheckRequest req = (EntryCheckRequest) incoming.getData();
                    if (req.getVisitorId() == null || req.getVisitorId().trim().isEmpty()) {
                        client.sendToClient(new Message("ENTRY_DENIED", "Visitor ID is required."));
                        break;
                    }
                    if (req.getParkId() <= 0) {
                        client.sendToClient(new Message("ENTRY_DENIED", "Invalid park ID."));
                        break;
                    }
                    // client validates too, but enforce on the server to be safe
                    String ot = req.getOrderType() != null
                                ? req.getOrderType().toUpperCase() : "";
                    int    nv = req.getNumVisitors();
                    if ("SOLO".equals(ot) && nv != 1) {
                        client.sendToClient(new Message("ENTRY_DENIED",
                            "Solo check-in requires exactly 1 visitor."));
                        break;
                    }
                    if ("GROUP".equals(ot)) {
                        // guides (who enter free) may present with up to 15 guests + themselves = 16 total;
                        // regular visitors and subscribers are capped at 15
                        boolean isGuideCI = DBController.getInstance()
                            .isRegisteredGuide(req.getVisitorId());
                        int maxCI = isGuideCI ? 16 : 15;
                        if (nv < 2 || nv > maxCI) {
                            client.sendToClient(new Message("ENTRY_DENIED",
                                "Group check-in requires 2–" + maxCI + " visitors (got " + nv + ")."
                                + (isGuideCI ? " Guide is included in the count." : "")));
                            break;
                        }
                    }
                    EntryResult entryResult = DBController.getInstance().checkInVisitor(req);
                    if (entryResult.isSuccess()) {
                        client.sendToClient(new Message("ENTRY_APPROVED", entryResult));
                    } else {
                        client.sendToClient(new Message("ENTRY_DENIED", entryResult.getMessage()));
                    }
                    break;
                }

                case "REGISTER_EXIT": {
                    if (!(incoming.getData() instanceof ExitRequest)) {
                        client.sendToClient(new Message("EXIT_FAIL", "Invalid exit request."));
                        break;
                    }
                    ExitRequest exitReq = (ExitRequest) incoming.getData();
                    if (exitReq.getVisitorId() == null || exitReq.getVisitorId().trim().isEmpty()) {
                        client.sendToClient(new Message("EXIT_FAIL", "Visitor ID is required."));
                        break;
                    }
                    if (exitReq.getParkId() <= 0) {
                        client.sendToClient(new Message("EXIT_FAIL", "Invalid park ID."));
                        break;
                    }
                    boolean exited = DBController.getInstance()
                        .registerExit(exitReq.getVisitorId(), exitReq.getParkId());
                    if (exited) {
                        client.sendToClient(new Message("EXIT_SUCCESS",
                            "Exit registered for visitor " + exitReq.getVisitorId() + "."));
                    } else {
                        client.sendToClient(new Message("EXIT_FAIL",
                            "No active entry found for visitor " + exitReq.getVisitorId() +
                            " at this park today."));
                    }
                    break;
                }

                case "GET_VISITOR_REPORT": {
                    Integer parkId = null;
                    if (incoming.getData() instanceof Integer) {
                        int pid = (Integer) incoming.getData();
                        if (pid > 0) parkId = pid;
                    }
                    List<ReportVisitorRow> vReport =
                        DBController.getInstance().getVisitorReport(parkId);
                    client.sendToClient(new Message("VISITOR_REPORT_DATA", vReport));
                    break;
                }

                case "GET_CANCEL_REPORT": {
                    Integer parkId = null;
                    if (incoming.getData() instanceof Integer) {
                        int pid = (Integer) incoming.getData();
                        if (pid > 0) parkId = pid;
                    }
                    List<ReportCancelRow> cReport =
                        DBController.getInstance().getCancelReport(parkId);
                    client.sendToClient(new Message("CANCEL_REPORT_DATA", cReport));
                    break;
                }

                case "GET_USAGE_REPORT": {
                    List<ReportUsageRow> uReport =
                        DBController.getInstance().getUsageReport();
                    client.sendToClient(new Message("USAGE_REPORT_DATA", uReport));
                    break;
                }

                case "REGISTER_VISITOR": {
                    if (!(incoming.getData() instanceof RegisterRequest)) {
                        client.sendToClient(new Message("REGISTER_FAIL", "Invalid registration data."));
                        break;
                    }
                    RegisterRequest req = (RegisterRequest) incoming.getData();
                    // client validates too, but enforce on the server to be safe
                    if (req.getIdNumber() == null || !req.getIdNumber().matches("\\d{5,15}")) {
                        client.sendToClient(new Message("REGISTER_FAIL",
                            "ID number must be 5–15 digits."));
                        break;
                    }
                    if (req.getFirstName() == null || req.getFirstName().trim().isEmpty() ||
                        req.getLastName()  == null || req.getLastName().trim().isEmpty()) {
                        client.sendToClient(new Message("REGISTER_FAIL",
                            "First and last name are required."));
                        break;
                    }
                    if (req.getEmail() == null ||
                        !req.getEmail().matches("^[\\w._%+\\-]+@[\\w.\\-]+\\.[a-zA-Z]{2,}$")) {
                        client.sendToClient(new Message("REGISTER_FAIL",
                            "A valid email address is required."));
                        break;
                    }
                    if (req.getPhone() == null || !req.getPhone().matches("\\d{9,15}")) {
                        client.sendToClient(new Message("REGISTER_FAIL",
                            "Phone number must be 9–15 digits."));
                        break;
                    }
                    if (req.isSubscriber() && (req.getFamilySize() < 1 || req.getFamilySize() > 10)) {
                        client.sendToClient(new Message("REGISTER_FAIL",
                            "Family size must be between 1 and 10."));
                        break;
                    }
                    RegisterResult regResult = DBController.getInstance().registerVisitor(req);
                    if (regResult.isSuccess()) {
                        client.sendToClient(new Message("REGISTER_SUCCESS", regResult));
                    } else {
                        client.sendToClient(new Message("REGISTER_FAIL", regResult.getMessage()));
                    }
                    break;
                }

                case "GET_LIVE_ORDERS": {
                    List<OrderDetail> liveOrders = DBController.getInstance().getAllLiveOrders();
                    client.sendToClient(new Message("LIVE_ORDERS_LIST", liveOrders));
                    break;
                }

                case "GET_TODAY_ORDERS": {
                    if (!(incoming.getData() instanceof Integer)) {
                        client.sendToClient(new Message("TODAY_ORDERS_LIST",
                            new java.util.ArrayList<>()));
                        break;
                    }
                    int parkId = (Integer) incoming.getData();
                    List<OrderDetail> todayOrders =
                        DBController.getInstance().getTodayOrders(parkId);
                    client.sendToClient(new Message("TODAY_ORDERS_LIST", todayOrders));
                    break;
                }

                case "GET_WAITING_LIST": {
                    if (!(incoming.getData() instanceof Integer)) {
                        client.sendToClient(new Message("WAITING_LIST_DATA",
                            new java.util.ArrayList<>()));
                        break;
                    }
                    int parkId = (Integer) incoming.getData();
                    List<OrderDetail> waitingEntries =
                        DBController.getInstance().getWaitingList(parkId);
                    client.sendToClient(new Message("WAITING_LIST_DATA", waitingEntries));
                    break;
                }

                case "UPDATE_ORDER": {
                    Order o = (Order) incoming.getData();
                    String updateErr = DBController.getInstance()
                        .updateOrder(o.getOrderNumber(), o.getOrderDate(),
                                     o.getNumberOfVisitors(), o.getOrderType());
                    if (updateErr == null)
                        client.sendToClient(new Message("UPDATE_SUCCESS", null));
                    else
                        client.sendToClient(new Message("UPDATE_FAIL", updateErr));
                    break;
                }

                case "GET_PARK_SETTINGS": {
                    if (!(incoming.getData() instanceof Integer)) {
                        client.sendToClient(new Message("PARK_SETTINGS_ERROR", "Invalid park ID."));
                        break;
                    }
                    int parkId = (Integer) incoming.getData();
                    Park park = DBController.getInstance().getParkById(parkId);
                    if (park != null) {
                        client.sendToClient(new Message("PARK_SETTINGS_DATA", park));
                    } else {
                        client.sendToClient(new Message("PARK_SETTINGS_ERROR",
                            "Park not found (ID " + parkId + ")."));
                    }
                    break;
                }

                case "UPDATE_PARK_SETTINGS": {
                    if (!(incoming.getData() instanceof Park)) {
                        client.sendToClient(new Message("PARK_SETTINGS_UPDATE_FAIL",
                            "Invalid park data."));
                        break;
                    }
                    Park p = (Park) incoming.getData();
                    if (p.getCapacity() < 1 || p.getCapacity() > 10000) {
                        client.sendToClient(new Message("PARK_SETTINGS_UPDATE_FAIL",
                            "Capacity must be between 1 and 10 000.")); break;
                    }
                    if (p.getMaxOrders() < 1 || p.getMaxOrders() > p.getCapacity()) {
                        client.sendToClient(new Message("PARK_SETTINGS_UPDATE_FAIL",
                            "Max bookable orders must be between 1 and the current capacity ("
                            + p.getCapacity() + ").")); break;
                    }
                    if (p.getVisitDurationHours() < 1 || p.getVisitDurationHours() > 24) {
                        client.sendToClient(new Message("PARK_SETTINGS_UPDATE_FAIL",
                            "Visit duration must be between 1 and 24 hours.")); break;
                    }
                    if (p.getFullPrice() <= 0 || p.getFullPrice() > 9999.99) {
                        client.sendToClient(new Message("PARK_SETTINGS_UPDATE_FAIL",
                            "Full price must be greater than 0 and at most 9 999.99.")); break;
                    }
                    boolean ok = DBController.getInstance().updateParkSettings(
                        p.getId(), p.getCapacity(), p.getMaxOrders(),
                        p.getVisitDurationHours(), p.getFullPrice());
                    if (ok) {
                        Park updated = DBController.getInstance().getParkById(p.getId());
                        client.sendToClient(new Message("PARK_SETTINGS_UPDATED",
                            updated != null ? updated : p));
                    } else {
                        client.sendToClient(new Message("PARK_SETTINGS_UPDATE_FAIL",
                            "Failed to save settings. Please try again."));
                    }
                    break;
                }

                case "CONFIRM_VISIT": {
                    if (!(incoming.getData() instanceof Integer)) {
                        client.sendToClient(new Message("CONFIRM_VISIT_FAIL", "Invalid order ID."));
                        break;
                    }
                    int orderId = (Integer) incoming.getData();
                    if (orderId <= 0) {
                        client.sendToClient(new Message("CONFIRM_VISIT_FAIL", "Invalid order ID."));
                        break;
                    }
                    ReminderService.getInstance().confirmVisit(orderId);
                    client.sendToClient(new Message("CONFIRM_VISIT_OK", orderId));
                    break;
                }

                case "LOGOUT": {
                    // socket stays open — the user may log back in on the same connection.
                    // "Disconnected" only appears when the socket actually closes (clientDisconnected / clientException).
                    break;
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * opens the DB connection and triggers the startup reminder check for tomorrow's orders.
     */
    @Override
    protected void serverStarted() {
        System.out.println("Server listening on port " + getPort());
        DBController db = DBController.getInstance();
        db.connect();
        ReminderService.getInstance().runStartupCheck(db);
    }

    /** logs the shutdown event to stdout. */
    @Override
    protected void serverStopped() {
        System.out.println("Server stopped.");
    }

    /**
     * validates a booking request before persisting. enforces visit-type limits:
     * SOLO = exactly 1 visitor (any role); GROUP = 2–15 for regular visitors/subscribers,
     * or 2–16 total for registered guides (guide is included in the count but enters free).
     *
     * @param req the booking request to validate
     * @return a human-readable error string if invalid; {@code null} if all checks pass
     */
    private String validateBookingRequest(BookingRequest req) {
        if (req.getVisitorId() == null || req.getVisitorId().trim().isEmpty())
            return "Visitor ID is required.";
        if (req.getParkId() <= 0)
            return "Invalid park selection.";
        if (req.getVisitDate() == null || req.getVisitDate().trim().isEmpty())
            return "Visit date is required.";
        try {
            LocalDate date = LocalDate.parse(req.getVisitDate());
            if (!date.isAfter(LocalDate.now()))
                return "Visit date must be a future date.";
        } catch (DateTimeParseException e) {
            return "Invalid date format. Expected yyyy-MM-dd.";
        }
        if (req.getVisitTime() == null || req.getVisitTime().trim().isEmpty())
            return "Visit time is required.";
        if (req.getNumVisitors() < 1)
            return "At least 1 visitor is required.";
        String type = req.getOrderType();
        if (type == null) return "Visit type is required.";
        switch (type) {
            case "SOLO":
                if (req.getNumVisitors() != 1)
                    return "Solo bookings allow exactly 1 visitor.";
                break;
            case "GROUP":
                // guides count themselves in numVisitors (they enter free); max 15 paying guests
                // means a guide-led group may have up to 16 total. regular visitors max out at 15.
                boolean isGuide = DBController.getInstance()
                    .isRegisteredGuide(req.getVisitorId());
                int maxGroup = isGuide ? 16 : 15;
                if (req.getNumVisitors() < 2 || req.getNumVisitors() > maxGroup)
                    return isGuide
                        ? "Guide group bookings require 2–16 visitors total (guide enters free)."
                        : "Group bookings require 2–15 visitors.";
                break;
            default:
                return "Invalid visit type. Must be SOLO or GROUP.";
        }
        String email = req.getEmail();
        if (email == null || email.trim().isEmpty())
            return "Email address is required.";
        if (!email.matches("^[\\w._%+\\-]+@[\\w.\\-]+\\.[a-zA-Z]{2,}$"))
            return "Invalid email address format.";
        return null;
    }

    /**
     * caches the client IP so it's still readable after the socket closes, then
     * adds a row to the connected-clients table in the server UI.
     *
     * @param client the newly connected client
     */
    @Override
    protected void clientConnected(ConnectionToClient client) {
        String ip   = client.getInetAddress().getHostAddress();
        String host = client.getInetAddress().getHostName();
        client.setInfo("ip", ip);   // cache now — the socket may be null by the time disconnect fires
        System.out.println("Client connected | IP: " + ip + " | Host: " + host);
        ServerPortFrameController.addClient(ip, host);
    }

    /**
     * marks the client's row as "Disconnected" — keeps the row so the operator can
     * see who has been connected. synchronised against concurrent disconnect events.
     *
     * @param client the client that disconnected
     */
    @Override
    protected synchronized void clientDisconnected(ConnectionToClient client) {
        String ip = (String) client.getInfo("ip");
        if (ip == null) return;
        System.out.println("Client disconnected | IP: " + ip);
        ServerPortFrameController.markClientDisconnected(ip);
    }

    /**
     * called on network drop or client crash — behaves identically to
     * {@link #clientDisconnected(ConnectionToClient)}.
     *
     * @param client    the client whose connection was lost
     * @param exception the exception that caused the disconnect
     */
    @Override
    protected synchronized void clientException(ConnectionToClient client, Throwable exception) {
        String ip = (String) client.getInfo("ip");
        if (ip == null) return;
        System.out.println("Client lost connection | IP: " + ip);
        ServerPortFrameController.markClientDisconnected(ip);
    }
}
