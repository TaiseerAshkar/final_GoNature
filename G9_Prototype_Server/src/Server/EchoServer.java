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
import logic.GuideDetail;
import logic.ParkSettingsRequest;
import logic.Promotion;
import logic.RegisterGuideRequest;
import logic.RegisterRequest;
import logic.RegisterResult;
import logic.ReportCancelRow;
import logic.ReportRequest;
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
 *   <li>{@code FIND_TODAY_BOOKING} → {@code BOOKING_FOUND} / {@code BOOKING_NOT_FOUND}</li>
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
     * Tracks every session key (visitor ID or staff username) that is currently
     * logged in across all active connections. Using a ConcurrentHashMap-backed set
     * so reads and writes from multiple OCSF reader threads are race-free without
     * explicit synchronisation on every check.
     *
     * <p>The corresponding per-connection key is stored in
     * {@code client.setInfo("sessionKey", …)} at login time so that
     * {@link #clientDisconnected} and {@link #clientException} can look it up and
     * remove it without needing to know whether the user was a visitor or staff member.
     */
    private static final java.util.Set<String> activeSessions =
        java.util.Collections.newSetFromMap(new java.util.concurrent.ConcurrentHashMap<>());

    /**
     * Per-park mutex objects used to serialise concurrent BOOK_ORDER requests for the
     * same park at the JVM level.  A new sentinel {@code Object} is created lazily on
     * first access via {@code computeIfAbsent} and is never removed, so the same
     * reference is reused for the lifetime of the server.
     *
     * <p>Combined with {@link DBController#checkAndBook}'s {@code SELECT … FOR UPDATE}
     * transaction this provides two independent layers of protection against
     * double-booking the last available slot.
     */
    private static final java.util.concurrent.ConcurrentHashMap<Integer, Object> parkLocks =
        new java.util.concurrent.ConcurrentHashMap<>();

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
                    if (result != null) {
                        // add() returns false if the key was already present — atomic check-and-add
                        if (!activeSessions.add(idNumber)) {
                            client.sendToClient(new Message("LOGIN_FAIL",
                                "This account is already logged in on another device."));
                            break;
                        }
                        client.setInfo("sessionKey", idNumber);
                        client.sendToClient(new Message("LOGIN_SUCCESS", result));
                    } else {
                        client.sendToClient(new Message("LOGIN_FAIL",
                            "That ID number isn't registered. Please check it, or create an account to get started."));
                    }
                    break;
                }

                case "LOGIN_USER": {
                    if (!(incoming.getData() instanceof String[])
                            || ((String[]) incoming.getData()).length < 2) {
                        client.sendToClient(new Message("LOGIN_FAIL",
                            "We couldn't read your login details. Please try again."));
                        break;
                    }
                    String[] creds = (String[]) incoming.getData();
                    String username = creds[0];
                    DBController db = DBController.getInstance();
                    LoginResult result = db.loginUser(username, creds[1]);
                    if (result != null) {
                        // add() returns false if the key was already present — atomic check-and-add
                        if (!activeSessions.add(username)) {
                            client.sendToClient(new Message("LOGIN_FAIL",
                                "This account is already logged in on another device."));
                            break;
                        }
                        client.setInfo("sessionKey", username);
                        client.sendToClient(new Message("LOGIN_SUCCESS", result));
                    } else {
                        client.sendToClient(new Message("LOGIN_FAIL",
                            db.loginUserFailReason != null ? db.loginUserFailReason
                                : "We couldn't sign you in. Please check your username and password and try again."));
                    }
                    break;
                }

                case "GET_PARKS": {
                    List<Park> parks = DBController.getInstance().getParks();
                    client.sendToClient(new Message("PARKS_LIST", parks));
                    break;
                }

                case "BOOK_ORDER": {
                    if (!(incoming.getData() instanceof BookingRequest)) {
                        client.sendToClient(new Message("BOOKING_ERROR",
                            "We couldn't read your booking request. Please try again."));
                        break;
                    }
                    BookingRequest req = (BookingRequest) incoming.getData();
                    String validationError = validateBookingRequest(req);
                    if (validationError != null) {
                        client.sendToClient(new Message("BOOKING_ERROR", validationError));
                        break;
                    }

                    // Acquire the per-park JVM lock before entering the DB transaction.
                    // This is the first of two concurrency layers: it serialises all booking
                    // attempts for the same park on this server so that at most one thread
                    // runs checkAndBook() for a given park at any time.  The second layer is
                    // the SELECT ... FOR UPDATE inside checkAndBook() itself.
                    Object parkLock = parkLocks.computeIfAbsent(req.getParkId(), id -> new Object());
                    int orderId = -1;
                    synchronized (parkLock) {
                        try {
                            orderId = DBController.getInstance().checkAndBook(req);
                        } catch (java.sql.SQLException e) {
                            System.out.println("[BOOK_ORDER] checkAndBook SQL error: " + e.getMessage());
                            client.sendToClient(new Message("BOOKING_ERROR",
                                "We couldn't complete your booking. Please try again."));
                            break;
                        }
                        if (orderId == 0) {
                            client.sendToClient(new Message("BOOKING_FULL", null));
                            break;
                        }
                        if (orderId < 0) {
                            client.sendToClient(new Message("BOOKING_ERROR",
                                "We couldn't complete your booking. Please try again."));
                            break;
                        }
                    }
                    // Lock released — booking is committed in the DB.
                    // All remaining work (response + email) is outside the critical section.

                    // Price preview for the confirmation screen: base price before any promotion,
                    // final price after the best active promotion for the visit date.
                    double[] quote = DBController.getInstance().quotePrebookedPrice(
                        req.getParkId(), req.getVisitDate(), req.getOrderType(),
                        req.getNumVisitors(), req.getVisitorId());
                    logic.Promotion bookPromo = DBController.getInstance()
                        .getBestActivePromotion(req.getParkId(), req.getVisitDate());
                    double bookPromoPct  = (bookPromo != null) ? bookPromo.getDiscountPercent() : 0.0;
                    String bookPromoDesc = (bookPromo != null) ? bookPromo.getDescription() : "";

                    client.sendToClient(new Message("BOOKING_CONFIRMED",
                        new BookingResult(orderId, "CONFIRMED", req.getParkName(),
                            req.getVisitDate(), req.getVisitTime(),
                            req.getNumVisitors(), req.getOrderType(), req.getEmail(),
                            quote[0], quote[1], bookPromoPct, bookPromoDesc)));

                    // ── Booking confirmation notification — fires for EVERY successful booking ──
                    // Look up the visitor's name for a personalised email.
                    String bookingVisitorName =
                        DBController.getInstance().getVisitorDisplayName(req.getVisitorId());

                    ServerPortFrameController.logSimulation(
                        "📧 Booking confirmation → " + req.getEmail() +
                        "  |  " + req.getParkName() +
                        "  |  " + req.getVisitDate() + " at " + req.getVisitTime() +
                        "  |  Order #" + orderId);
                    System.out.println("[EchoServer] Sending booking-confirmation email"
                        + "  to=" + req.getEmail()
                        + "  order=" + orderId
                        + "  visitor=" + bookingVisitorName);
                    EmailService.sendEmail(
                        req.getEmail(),
                        "Your GoNature Booking is Confirmed! 🌿",
                        "Hello " + bookingVisitorName + ",\n\n" +
                        "Great news — your visit to " + req.getParkName() + " is confirmed!\n\n" +
                        "─────────────────────────────\n" +
                        "  Order #:   " + orderId + "\n" +
                        "  Park:      " + req.getParkName() + "\n" +
                        "  Date:      " + req.getVisitDate() + "\n" +
                        "  Time:      " + req.getVisitTime() + "\n" +
                        "  Visitors:  " + req.getNumVisitors() + "\n" +
                        "  Type:      " + req.getOrderType() + "\n" +
                        "─────────────────────────────\n\n" +
                        "Please arrive 15 minutes before your scheduled time and\n" +
                        "present your Order # " + orderId + " at the entrance.\n\n" +
                        "We look forward to welcoming you!\n\n" +
                        "— The GoNature Team 🌿");

                    // ── Day-before reminder — only fires when the visit is tomorrow ──
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
                        client.sendToClient(new Message("BOOKING_ERROR",
                            "We couldn't read your booking request. Please try again."));
                        break;
                    }
                    BookingRequest req = (BookingRequest) incoming.getData();
                    int waitId;
                    try {
                        waitId = DBController.getInstance().addToWaitingList(req);
                    } catch (RuntimeException e) {
                        System.out.println("addToWaitingList error: " + e.getMessage());
                        client.sendToClient(new Message("BOOKING_ERROR",
                            "We couldn't add you to the waiting list. Please try again."));
                        break;
                    }
                    if (waitId < 0) {
                        client.sendToClient(new Message("BOOKING_ERROR",
                            "We couldn't add you to the waiting list. Please try again."));
                        break;
                    }
                    client.sendToClient(new Message("WAITING_LIST_CONFIRMED",
                        new BookingResult(waitId, "WAITING", req.getParkName(),
                            req.getVisitDate(), req.getVisitTime(),
                            req.getNumVisitors(), req.getOrderType(), req.getEmail())));
                    EmailService.sendEmail(req.getEmail(),
                        "GoNature — Added to Waiting List",
                        "Hello,\n\n" +
                        "You've been added to the waiting list for " + req.getParkName() + ".\n\n" +
                        "Park: " + req.getParkName() + "\n" +
                        "Date: " + req.getVisitDate() + " at " + req.getVisitTime() + "\n" +
                        "Visitors: " + req.getNumVisitors() + "\n\n" +
                        "We'll email you right away if a spot opens up.\n\n" +
                        "— GoNature Parks");
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
                        client.sendToClient(new Message("CANCEL_FAIL", "We couldn't identify that order. Please refresh and try again."));
                        break;
                    }
                    int orderId = (Integer) incoming.getData();
                    if (orderId <= 0) {
                        client.sendToClient(new Message("CANCEL_FAIL", "We couldn't identify that order. Please refresh and try again."));
                        break;
                    }
                    // capture contact details before cancelling so we can email the visitor
                    OrderDetail cancelInfo = DBController.getInstance().getOrderById(orderId);
                    CancelResult result = DBController.getInstance().cancelOrder(orderId);
                    if (result.isSuccess()) {
                        client.sendToClient(new Message("CANCEL_SUCCESS", result));
                        // remove any pending reminder for this order from the simulation log
                        ServerPortFrameController.removeReminderForOrder(orderId);
                        if (cancelInfo != null && cancelInfo.getEmail() != null) {
                            EmailService.sendEmail(cancelInfo.getEmail(),
                                "GoNature — Booking Cancelled (Order #" + orderId + ")",
                                "Hello,\n\n" +
                                "Your booking has been cancelled.\n\n" +
                                "Order #: " + orderId + "\n" +
                                "Park: " + cancelInfo.getParkName() + "\n" +
                                "Date: " + cancelInfo.getVisitDate() + " at " + cancelInfo.getVisitTime() + "\n" +
                                "Visitors: " + cancelInfo.getNumVisitors() + "\n\n" +
                                "The slot has been released and is now available for other visitors.\n\n" +
                                "— GoNature Parks");
                        }
                    } else {
                        client.sendToClient(new Message("CANCEL_FAIL",
                            "Cannot cancel this order. It may already be cancelled or completed."));
                    }
                    break;
                }

                case "CHECK_IN_VISITOR": {
                    if (!(incoming.getData() instanceof EntryCheckRequest)) {
                        client.sendToClient(new Message("ENTRY_DENIED",
                            "We couldn't read that check-in request. Please try again."));
                        break;
                    }
                    EntryCheckRequest req = (EntryCheckRequest) incoming.getData();
                    if (req.getVisitorId() == null || req.getVisitorId().trim().isEmpty()) {
                        client.sendToClient(new Message("ENTRY_DENIED", "Visitor ID is required."));
                        break;
                    }
                    if (req.getParkId() <= 0) {
                        client.sendToClient(new Message("ENTRY_DENIED", "Please select a valid park."));
                        break;
                    }
                    // client validates too, but enforce on the server to be safe
                    String ot = req.getOrderType() != null
                                ? req.getOrderType().toUpperCase() : "";
                    int    nv = req.getNumVisitors();
                    if ("SOLO".equals(ot) && nv != 1) {
                        client.sendToClient(new Message("ENTRY_DENIED",
                            "A SOLO check-in must have exactly 1 visitor."));
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

                case "FIND_TODAY_BOOKING": {
                    if (!(incoming.getData() instanceof EntryCheckRequest)) {
                        client.sendToClient(new Message("BOOKING_NOT_FOUND", "Invalid request."));
                        break;
                    }
                    EntryCheckRequest fbReq = (EntryCheckRequest) incoming.getData();
                    OrderDetail found = DBController.getInstance()
                        .getTodayBooking(fbReq.getVisitorId().trim(), fbReq.getParkId());
                    if (found != null) {
                        client.sendToClient(new Message("BOOKING_FOUND", found));
                    } else {
                        client.sendToClient(new Message("BOOKING_NOT_FOUND",
                            "No confirmed booking found for visitor " + fbReq.getVisitorId()
                            + " at this park today."));
                    }
                    break;
                }

                case "REGISTER_EXIT": {
                    if (!(incoming.getData() instanceof ExitRequest)) {
                        client.sendToClient(new Message("EXIT_FAIL",
                            "We couldn't read that exit request. Please try again."));
                        break;
                    }
                    ExitRequest exitReq = (ExitRequest) incoming.getData();
                    if (exitReq.getVisitorId() == null || exitReq.getVisitorId().trim().isEmpty()) {
                        client.sendToClient(new Message("EXIT_FAIL", "Visitor ID is required."));
                        break;
                    }
                    if (exitReq.getParkId() <= 0) {
                        client.sendToClient(new Message("EXIT_FAIL", "Please select a valid park."));
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
                    ReportRequest vReq = (incoming.getData() instanceof ReportRequest)
                        ? (ReportRequest) incoming.getData() : null;
                    int vYear  = (vReq != null) ? vReq.getYear()
                                                : LocalDate.now().getYear();
                    int vMonth = (vReq != null) ? vReq.getMonth()
                                                : LocalDate.now().getMonthValue();
                    Integer vParkId = (vReq != null && vReq.getParkId() > 0)
                        ? vReq.getParkId() : null;
                    List<ReportVisitorRow> vReport =
                        DBController.getInstance().getVisitorReport(vParkId, vYear, vMonth);
                    client.sendToClient(new Message("VISITOR_REPORT_DATA", vReport));
                    break;
                }

                case "GET_CANCEL_REPORT": {
                    ReportRequest cReq = (incoming.getData() instanceof ReportRequest)
                        ? (ReportRequest) incoming.getData() : null;
                    int cYear  = (cReq != null) ? cReq.getYear()
                                                : LocalDate.now().getYear();
                    int cMonth = (cReq != null) ? cReq.getMonth()
                                                : LocalDate.now().getMonthValue();
                    Integer cParkId = (cReq != null && cReq.getParkId() > 0)
                        ? cReq.getParkId() : null;
                    List<ReportCancelRow> cReport =
                        DBController.getInstance().getCancelReport(cParkId, cYear, cMonth);
                    client.sendToClient(new Message("CANCEL_REPORT_DATA", cReport));
                    break;
                }

                case "GET_USAGE_REPORT": {
                    ReportRequest uReq = (incoming.getData() instanceof ReportRequest)
                        ? (ReportRequest) incoming.getData() : null;
                    int uYear  = (uReq != null) ? uReq.getYear()
                                                : LocalDate.now().getYear();
                    int uMonth = (uReq != null) ? uReq.getMonth()
                                                : LocalDate.now().getMonthValue();
                    List<ReportUsageRow> uReport =
                        DBController.getInstance().getUsageReport(uYear, uMonth);
                    client.sendToClient(new Message("USAGE_REPORT_DATA", uReport));
                    break;
                }

                case "REGISTER_VISITOR": {
                    if (!(incoming.getData() instanceof RegisterRequest)) {
                        client.sendToClient(new Message("REGISTER_FAIL",
                            "We couldn't read your registration details. Please try again."));
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

                        // ── Welcome email ──────────────────────────────────────────────────
                        String visitorFullName = req.getFirstName() + " " + req.getLastName();
                        StringBuilder welcomeBody = new StringBuilder();
                        welcomeBody.append("Hello ").append(visitorFullName).append(",\n\n");
                        welcomeBody.append("Welcome to GoNature! Your account has been ")
                                   .append("created successfully.\n\n");
                        welcomeBody.append("─────────────────────────────\n");
                        welcomeBody.append("  Your ID:  ").append(req.getIdNumber()).append("\n");
                        if (regResult.getSubscriberId() != null) {
                            welcomeBody.append("  Subscriber ID:  ")
                                       .append(regResult.getSubscriberId()).append("\n");
                        }
                        welcomeBody.append("─────────────────────────────\n\n");
                        if (req.isSubscriber()) {
                            welcomeBody.append("As a Family Member Club subscriber, you enjoy a ")
                                       .append("10% discount on all bookings!\n");
                            if (regResult.getSubscriberId() != null) {
                                welcomeBody.append("Please save your Subscriber ID ")
                                           .append(regResult.getSubscriberId())
                                           .append(" — you'll need it when booking.\n");
                            }
                            welcomeBody.append("\n");
                        }
                        welcomeBody.append("Log in any time with your ID number to explore ")
                                   .append("our parks and book your next visit.\n\n");
                        welcomeBody.append("See you in the parks!\n\n");
                        welcomeBody.append("— The GoNature Team 🌿");

                        System.out.println("[EchoServer] Sending welcome email"
                            + "  to=" + req.getEmail()
                            + "  visitor=" + visitorFullName
                            + "  subscriber=" + req.isSubscriber());
                        EmailService.sendEmail(
                            req.getEmail(),
                            "Welcome to GoNature! 🌿",
                            welcomeBody.toString());
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
                    if (!(incoming.getData() instanceof Order)) {
                        client.sendToClient(new Message("UPDATE_FAIL",
                            "We couldn't read the order details. Please try again."));
                        break;
                    }
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
                        client.sendToClient(new Message("PARK_SETTINGS_ERROR", "Please select a valid park."));
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
                            "We couldn't read the park settings. Please try again."));
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
                            "We couldn't save the park settings. Please try again."));
                    }
                    break;
                }

                case "CONFIRM_VISIT": {
                    if (!(incoming.getData() instanceof Integer)) {
                        client.sendToClient(new Message("CONFIRM_VISIT_FAIL", "We couldn't identify that order. Please refresh and try again."));
                        break;
                    }
                    int orderId = (Integer) incoming.getData();
                    if (orderId <= 0) {
                        client.sendToClient(new Message("CONFIRM_VISIT_FAIL", "We couldn't identify that order. Please refresh and try again."));
                        break;
                    }
                    ReminderService.getInstance().confirmVisit(orderId);
                    // also flip the order's status from pending -> confirmed in the DB so it
                    // shows as CONFIRMED in the visitor's My Orders (no-op if already confirmed)
                    DBController.getInstance().confirmOrder(orderId);
                    client.sendToClient(new Message("CONFIRM_VISIT_OK", orderId));
                    break;
                }

                case "LOGOUT": {
                    // Free the session slot so the same account can log in elsewhere.
                    // The socket stays open — the client may show the login screen again
                    // on the same connection without a full reconnect.
                    String sessionKey = (String) client.getInfo("sessionKey");
                    if (sessionKey != null) {
                        activeSessions.remove(sessionKey);
                        client.setInfo("sessionKey", null);
                    }
                    break;
                }

                // ── Park settings approval workflow ──────────────────────────────────

                case "SUBMIT_PARK_SETTINGS": {
                    if (!(incoming.getData() instanceof ParkSettingsRequest)) {
                        client.sendToClient(new Message("PARK_SETTINGS_SUBMIT_FAIL",
                            "We couldn't read your settings request. Please try again."));
                        break;
                    }
                    ParkSettingsRequest req = (ParkSettingsRequest) incoming.getData();
                    // Re-validate the proposed values server-side (mirrors ParkSettingsController rules).
                    if (req.getCapacity() < 1 || req.getCapacity() > 10000) {
                        client.sendToClient(new Message("PARK_SETTINGS_SUBMIT_FAIL",
                            "Capacity must be between 1 and 10 000.")); break;
                    }
                    if (req.getMaxOrders() < 1 || req.getMaxOrders() > req.getCapacity()) {
                        client.sendToClient(new Message("PARK_SETTINGS_SUBMIT_FAIL",
                            "Max orders must be between 1 and the proposed capacity ("
                            + req.getCapacity() + ").")); break;
                    }
                    if (req.getVisitDurationHours() < 1 || req.getVisitDurationHours() > 24) {
                        client.sendToClient(new Message("PARK_SETTINGS_SUBMIT_FAIL",
                            "Visit duration must be between 1 and 24 hours.")); break;
                    }
                    if (req.getFullPrice() <= 0 || req.getFullPrice() > 9999.99) {
                        client.sendToClient(new Message("PARK_SETTINGS_SUBMIT_FAIL",
                            "Full price must be greater than 0 and at most 9 999.99.")); break;
                    }
                    String submitErr = DBController.getInstance().submitParkSettingsRequest(req);
                    if (submitErr == null)
                        client.sendToClient(new Message("PARK_SETTINGS_SUBMITTED",
                            "Settings change submitted for department manager approval."));
                    else
                        client.sendToClient(new Message("PARK_SETTINGS_SUBMIT_FAIL", submitErr));
                    break;
                }

                case "GET_PENDING_SETTINGS": {
                    List<ParkSettingsRequest> pending =
                        DBController.getInstance().getPendingSettingsRequests();
                    client.sendToClient(new Message("PENDING_SETTINGS_LIST", pending));
                    break;
                }

                case "APPROVE_SETTINGS": {
                    if (!(incoming.getData() instanceof Integer)) {
                        client.sendToClient(new Message("SETTINGS_APPROVE_FAIL",
                            "We couldn't identify that request. Please refresh and try again.")); break;
                    }
                    int reqId = (Integer) incoming.getData();
                    String approveErr = DBController.getInstance().approveSettingsRequest(reqId);
                    if (approveErr == null)
                        client.sendToClient(new Message("SETTINGS_APPROVE_SUCCESS", reqId));
                    else
                        client.sendToClient(new Message("SETTINGS_APPROVE_FAIL", approveErr));
                    break;
                }

                case "REJECT_SETTINGS": {
                    if (!(incoming.getData() instanceof Integer)) {
                        client.sendToClient(new Message("SETTINGS_REJECT_FAIL",
                            "We couldn't identify that request. Please refresh and try again.")); break;
                    }
                    int reqId = (Integer) incoming.getData();
                    String rejectErr = DBController.getInstance().rejectSettingsRequest(reqId);
                    if (rejectErr == null)
                        client.sendToClient(new Message("SETTINGS_REJECT_SUCCESS", reqId));
                    else
                        client.sendToClient(new Message("SETTINGS_REJECT_FAIL", rejectErr));
                    break;
                }

                // ── Promotions approval workflow ─────────────────────────────────────

                case "SUBMIT_PROMOTION": {
                    if (!(incoming.getData() instanceof Promotion)) {
                        client.sendToClient(new Message("PROMOTION_SUBMIT_FAIL",
                            "We couldn't read your promotion. Please try again."));
                        break;
                    }
                    Promotion promo = (Promotion) incoming.getData();
                    // Re-validate server-side (mirrors PromotionsController rules).
                    if (promo.getDescription() == null || promo.getDescription().trim().isEmpty()) {
                        client.sendToClient(new Message("PROMOTION_SUBMIT_FAIL",
                            "Please enter a promotion description.")); break;
                    }
                    if (promo.getDiscountPercent() <= 0 || promo.getDiscountPercent() > 50) {
                        client.sendToClient(new Message("PROMOTION_SUBMIT_FAIL",
                            "Discount must be between 1 and 50 percent.")); break;
                    }
                    if (promo.getStartDate() == null || promo.getStartDate().isEmpty()
                            || promo.getEndDate() == null || promo.getEndDate().isEmpty()) {
                        client.sendToClient(new Message("PROMOTION_SUBMIT_FAIL",
                            "Please choose both a start and an end date.")); break;
                    }
                    if (promo.getEndDate().compareTo(promo.getStartDate()) < 0) {
                        client.sendToClient(new Message("PROMOTION_SUBMIT_FAIL",
                            "End date must be on or after the start date.")); break;
                    }
                    String promoErr = DBController.getInstance().submitPromotion(promo);
                    if (promoErr == null)
                        client.sendToClient(new Message("PROMOTION_SUBMITTED",
                            "Promotion submitted for department manager approval."));
                    else
                        client.sendToClient(new Message("PROMOTION_SUBMIT_FAIL", promoErr));
                    break;
                }

                case "GET_PROMOTIONS_FOR_PARK": {
                    if (!(incoming.getData() instanceof Integer)) {
                        client.sendToClient(new Message("PROMOTIONS_FOR_PARK_LIST",
                            new java.util.ArrayList<Promotion>()));
                        break;
                    }
                    int pkId = (Integer) incoming.getData();
                    List<Promotion> mine = DBController.getInstance().getPromotionsForPark(pkId);
                    client.sendToClient(new Message("PROMOTIONS_FOR_PARK_LIST", mine));
                    break;
                }

                case "GET_PENDING_PROMOTIONS": {
                    List<Promotion> pendingPromos =
                        DBController.getInstance().getPendingPromotions();
                    client.sendToClient(new Message("PENDING_PROMOTIONS_LIST", pendingPromos));
                    break;
                }

                case "GET_ACTIVE_PROMOTIONS": {
                    if (!(incoming.getData() instanceof Integer)) {
                        client.sendToClient(new Message("ACTIVE_PROMOTIONS_LIST",
                            new java.util.ArrayList<Promotion>()));
                        break;
                    }
                    int activeParkId = (Integer) incoming.getData();
                    String todayStr = LocalDate.now().toString();
                    List<Promotion> active =
                        DBController.getInstance().getActivePromotions(activeParkId, todayStr);
                    client.sendToClient(new Message("ACTIVE_PROMOTIONS_LIST", active));
                    break;
                }

                case "APPROVE_PROMOTION": {
                    if (!(incoming.getData() instanceof Integer)) {
                        client.sendToClient(new Message("PROMOTION_APPROVE_FAIL",
                            "We couldn't identify that promotion. Please refresh and try again.")); break;
                    }
                    int promoId = (Integer) incoming.getData();
                    String approveErr = DBController.getInstance().approvePromotion(promoId);
                    if (approveErr == null)
                        client.sendToClient(new Message("PROMOTION_APPROVE_SUCCESS", promoId));
                    else
                        client.sendToClient(new Message("PROMOTION_APPROVE_FAIL", approveErr));
                    break;
                }

                case "REJECT_PROMOTION": {
                    if (!(incoming.getData() instanceof Integer)) {
                        client.sendToClient(new Message("PROMOTION_REJECT_FAIL",
                            "We couldn't identify that promotion. Please refresh and try again.")); break;
                    }
                    int promoId = (Integer) incoming.getData();
                    String rejectPromoErr = DBController.getInstance().rejectPromotion(promoId);
                    if (rejectPromoErr == null)
                        client.sendToClient(new Message("PROMOTION_REJECT_SUCCESS", promoId));
                    else
                        client.sendToClient(new Message("PROMOTION_REJECT_FAIL", rejectPromoErr));
                    break;
                }

                // ── Guide management ─────────────────────────────────────────────────

                case "REGISTER_GUIDE": {
                    if (!(incoming.getData() instanceof RegisterGuideRequest)) {
                        client.sendToClient(new Message("GUIDE_REGISTER_FAIL",
                            "We couldn't read the guide's details. Please try again."));
                        break;
                    }
                    RegisterGuideRequest req = (RegisterGuideRequest) incoming.getData();
                    String err = DBController.getInstance().registerGuide(req);
                    if (err == null)
                        client.sendToClient(new Message("GUIDE_REGISTER_SUCCESS",
                            "Guide registered — pending department manager approval."));
                    else
                        client.sendToClient(new Message("GUIDE_REGISTER_FAIL", err));
                    break;
                }

                case "GET_ALL_GUIDES": {
                    List<GuideDetail> guides = DBController.getInstance().getAllGuides();
                    client.sendToClient(new Message("GUIDES_LIST", guides));
                    break;
                }

                case "APPROVE_GUIDE": {
                    if (!(incoming.getData() instanceof String)) {
                        client.sendToClient(new Message("GUIDE_APPROVE_FAIL",
                            "We couldn't identify that guide. Please refresh and try again."));
                        break;
                    }
                    String approveId  = (String) incoming.getData();
                    String approveErr = DBController.getInstance().approveGuide(approveId);
                    if (approveErr == null)
                        client.sendToClient(new Message("GUIDE_APPROVE_SUCCESS", approveId));
                    else
                        client.sendToClient(new Message("GUIDE_APPROVE_FAIL", approveErr));
                    break;
                }

                case "REJECT_GUIDE": {
                    if (!(incoming.getData() instanceof String)) {
                        client.sendToClient(new Message("GUIDE_REJECT_FAIL",
                            "We couldn't identify that guide. Please refresh and try again."));
                        break;
                    }
                    String rejectId  = (String) incoming.getData();
                    String rejectErr = DBController.getInstance().rejectGuide(rejectId);
                    if (rejectErr == null)
                        client.sendToClient(new Message("GUIDE_REJECT_SUCCESS", rejectId));
                    else
                        client.sendToClient(new Message("GUIDE_REJECT_FAIL", rejectErr));
                    break;
                }

                // ── Live capacity / occupancy lookups ────────────────────────────────

                case "GET_PARK_OCCUPANCY": {
                    if (!(incoming.getData() instanceof Integer)) {
                        client.sendToClient(new Message("PARK_OCCUPANCY", 0));
                        break;
                    }
                    int parkId = (Integer) incoming.getData();
                    int inside = DBController.getInstance().getCurrentOccupancy(parkId);
                    client.sendToClient(new Message("PARK_OCCUPANCY", inside));
                    break;
                }

                case "GET_AVAILABLE_SPOTS": {
                    // payload is String[]{ parkId, visitDate, visitTime }
                    if (!(incoming.getData() instanceof String[])) {
                        client.sendToClient(new Message("AVAILABLE_SPOTS", -1));
                        break;
                    }
                    String[] p = (String[]) incoming.getData();
                    if (p.length < 3) {
                        client.sendToClient(new Message("AVAILABLE_SPOTS", -1));
                        break;
                    }
                    int available;
                    try {
                        available = DBController.getInstance()
                            .getAvailableSpots(Integer.parseInt(p[0]), p[1], p[2]);
                    } catch (NumberFormatException e) {
                        available = -1;
                    }
                    client.sendToClient(new Message("AVAILABLE_SPOTS", available));
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
     * <ul>
     *   <li>SOLO   — exactly 1 visitor, any role.</li>
     *   <li>FAMILY — 2–15 visitors, any role; receives 15% advance-booking discount.</li>
     *   <li>GROUP  — 2–15 visitors for regular visitors/subscribers, or 2–16 total for
     *               registered guides (guide is included in the count but enters free).</li>
     * </ul>
     *
     * @param req the booking request to validate
     * @return a human-readable error string if invalid; {@code null} if all checks pass
     */
    private String validateBookingRequest(BookingRequest req) {
        if (req.getVisitorId() == null || req.getVisitorId().trim().isEmpty())
            return "Visitor ID is required.";
        if (req.getParkId() <= 0)
            return "Please select a park for your visit.";
        if (req.getVisitDate() == null || req.getVisitDate().trim().isEmpty())
            return "Visit date is required.";
        try {
            LocalDate date = LocalDate.parse(req.getVisitDate());
            if (!date.isAfter(LocalDate.now()))
                return "Visit date must be a future date.";
        } catch (DateTimeParseException e) {
            return "Please enter the visit date as YYYY-MM-DD (for example, 2026-07-15).";
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
                    return "SOLO bookings allow exactly 1 visitor.";
                break;
            case "FAMILY":
                // Any role may book a family visit; 2–15 visitors, 15% advance discount applies.
                if (req.getNumVisitors() < 2 || req.getNumVisitors() > 15)
                    return "FAMILY bookings require 2–15 visitors.";
                break;
            case "GROUP":
                // Guides count themselves in numVisitors (they enter free); max 15 paying guests
                // means a guide-led group may have up to 16 total. Regular visitors max out at 15.
                boolean isGuide = DBController.getInstance()
                    .isRegisteredGuide(req.getVisitorId());
                int maxGroup = isGuide ? 16 : 15;
                if (req.getNumVisitors() < 2 || req.getNumVisitors() > maxGroup)
                    return isGuide
                        ? "Guide GROUP bookings require 2–16 visitors total (guide enters free)."
                        : "GROUP bookings require 2–15 visitors.";
                break;
            default:
                return "Please choose a visit type: SOLO, FAMILY, or GROUP.";
        }
        String email = req.getEmail();
        if (email == null || email.trim().isEmpty())
            return "Please enter an email address so we can send your confirmation.";
        if (!email.matches("^[\\w._%+\\-]+@[\\w.\\-]+\\.[a-zA-Z]{2,}$"))
            return "Please enter a valid email address (for example, name@example.com).";
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
     * marks the client's row as "Disconnected" and frees their session slot so the
     * same account can log in again from another client.
     * synchronised against concurrent disconnect events.
     *
     * @param client the client that disconnected
     */
    @Override
    protected synchronized void clientDisconnected(ConnectionToClient client) {
        // Release the session slot — must happen whether the user explicitly logged
        // out or just closed the window, so the account is not permanently locked out.
        String sessionKey = (String) client.getInfo("sessionKey");
        if (sessionKey != null) activeSessions.remove(sessionKey);

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
        // Same cleanup as a clean disconnect — free the session slot.
        String sessionKey = (String) client.getInfo("sessionKey");
        if (sessionKey != null) activeSessions.remove(sessionKey);

        String ip = (String) client.getInfo("ip");
        if (ip == null) return;
        System.out.println("Client lost connection | IP: " + ip);
        ServerPortFrameController.markClientDisconnected(ip);
    }
}
