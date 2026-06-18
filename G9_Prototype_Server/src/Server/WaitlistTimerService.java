package Server;

import gui.ServerPortFrameController;
import javafx.application.Platform;
import javafx.scene.control.Alert;
import javafx.scene.control.DialogPane;

import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * enforces the 1-hour confirmation window for waiting-list promotions.
 *
 * <p>when {@link DBController#promoteFromWaitingList} promotes a visitor, it creates a HELD
 * (pending) order, marks the waiting_list row {@code NOTIFIED} with a {@code confirmation_deadline}
 * one hour out, and calls {@link #schedulePromotionTimeout(int)} here. two independent mechanisms
 * then guarantee the slot is released if the visitor never confirms:
 *
 * <ul>
 *   <li>a precise one-shot timer scheduled exactly 1 hour after the promotion, and</li>
 *   <li>a recurring sweep every {@value #SWEEP_INTERVAL_MINUTES} minutes that catches any
 *       expired entry, including promotions whose one-shot timer was lost to a server restart.</li>
 * </ul>
 *
 * <p>both paths funnel through {@link DBController#expireWaitlistEntry}, which is idempotent:
 * if the visitor confirmed in the meantime the held order is no longer pending, so nothing is
 * cancelled. on a genuine expiry the held order is cancelled, the visitor is notified (email +
 * server "Simulation" popup), and the freed slot is promoted to the next person in line.
 *
 * <p>singleton; call {@link #start(DBController)} once at server start. the executor thread is a
 * daemon so it never blocks JVM shutdown, and all UI work is marshalled onto the FX thread.
 */
public class WaitlistTimerService {

    /** Hours a promoted visitor has to confirm before the held slot is released. */
    private static final long CONFIRM_WINDOW_HOURS = 1;

    /** How often the safety-net sweep runs to catch expired confirmation windows. */
    private static final long SWEEP_INTERVAL_MINUTES = 5;

    private static WaitlistTimerService instance;

    private DBController db;

    private final ScheduledExecutorService scheduler =
        Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "waitlist-timer");
            t.setDaemon(true);
            return t;
        });

    /** use {@link #getInstance()}, not new. */
    private WaitlistTimerService() {}

    /**
     * returns (or creates) the singleton.
     *
     * @return the singleton instance; never {@code null}
     */
    public static synchronized WaitlistTimerService getInstance() {
        if (instance == null) instance = new WaitlistTimerService();
        return instance;
    }

    /**
     * begins the recurring expiry sweep. safe to call once at server startup.
     *
     * @param db the database controller used to query and release expired promotions
     */
    public void start(DBController db) {
        this.db = db;
        // first sweep shortly after start (catches entries left expired by a restart),
        // then every SWEEP_INTERVAL_MINUTES thereafter.
        scheduler.scheduleAtFixedRate(this::sweepExpired,
            1, SWEEP_INTERVAL_MINUTES * 60, TimeUnit.SECONDS);
        log("⏳ Waitlist timer service started — confirmation window " + CONFIRM_WINDOW_HOURS
            + "h, sweeping every " + SWEEP_INTERVAL_MINUTES + " min.");
    }

    /**
     * schedules the precise one-shot expiry check for a single promotion, exactly
     * {@value #CONFIRM_WINDOW_HOURS} hour(s) from now.
     *
     * @param waitlistId the waiting_list row id that was just promoted to NOTIFIED
     */
    public void schedulePromotionTimeout(int waitlistId) {
        scheduler.schedule(() -> expireOne(waitlistId), CONFIRM_WINDOW_HOURS, TimeUnit.HOURS);
    }

    /** queries all expired NOTIFIED entries and releases each one. */
    private void sweepExpired() {
        if (db == null) return;
        try {
            List<DBController.PromotedEntry> expired = db.getExpiredNotifiedEntries();
            if (expired.isEmpty()) return;
            log("=== Waitlist sweep — " + expired.size()
                + " expired confirmation window(s) ===");
            for (DBController.PromotedEntry e : expired) release(e);
        } catch (Exception ex) {
            log("Waitlist sweep error: " + ex.getMessage());
        }
    }

    /** one-shot path: re-reads the entry so it only acts if still NOTIFIED. */
    private void expireOne(int waitlistId) {
        if (db == null) return;
        DBController.PromotedEntry e = db.getNotifiedEntryById(waitlistId);
        if (e == null) return;   // already confirmed or released; nothing to do
        release(e);
    }

    /**
     * releases one expired promotion through the idempotent DB method, then, if the slot was
     * actually released, notifies the visitor with an email and a server "Simulation" popup.
     *
     * @param e the expired promoted entry
     */
    private void release(DBController.PromotedEntry e) {
        boolean released = db.expireWaitlistEntry(e);
        if (!released) {
            log("ℹ️  Waitlist entry #" + e.wlId
                + " already confirmed/handled — expiry skipped.");
            return;
        }
        String recipient = e.email.isEmpty() ? e.visitorId : e.email;
        String message = "Simulation - Your waitlist spot for " + e.parkName
            + " on " + e.visitDateStr + " has expired. Notification sent to " + recipient;

        log("⌛ " + message);
        if (!e.email.isEmpty()) {
            EmailService.sendEmail(e.email,
                "GoNature — Your waitlist spot has expired",
                "Hello,\n\n" +
                "Your waitlist spot for " + e.parkName + " on " + e.visitDateStr
                + " at " + e.visitTimeStr + " has expired because it wasn't confirmed within "
                + CONFIRM_WINDOW_HOURS + " hour.\n\n" +
                "The spot has been offered to the next visitor in line. You're welcome to book "
                + "again any time on GoNature.\n\n" +
                "— GoNature Parks");
        }
        showSimulationPopup(
            "Simulation — Waitlist Spot Expired",
            "Notification sent to:  " + recipient,
            "Waitlist spot expired\n\n"
            + "Park:   " + e.parkName    + "\n"
            + "Date:   " + e.visitDateStr + "\n"
            + "Time:   " + e.visitTimeStr  + "\n\n"
            + "The 1-hour confirmation window passed without a response.\n"
            + "The held booking has been cancelled and the next visitor in line has been notified.");
    }

    /**
     * shows a server-side "Simulation" notification popup styled with the GoNature server
     * theme (pine/gold header strip, parchment background, green OK button). safe to call
     * from any thread; marshals onto the JavaFX Application Thread via {@link Platform#runLater}.
     *
     * @param title   the window title shown in the OS title bar
     * @param header  the bold text shown in the pine header strip (e.g. visitor email/ID)
     * @param content the body text with park name, date, time, and action taken
     */
    static void showSimulationPopup(String title, String header, String content) {
        Platform.runLater(() -> {
            Alert alert = new Alert(Alert.AlertType.INFORMATION);
            alert.setTitle(title);
            alert.setHeaderText(header);
            alert.setContentText(content);

            DialogPane pane = alert.getDialogPane();
            pane.setMinWidth(500);
            pane.setGraphic(null);    // remove the default blue info icon
            java.net.URL css = WaitlistTimerService.class.getResource("/gui/ServerPort.css");
            if (css != null) pane.getStylesheets().add(css.toExternalForm());
            pane.getStyleClass().add("help-dialog");

            alert.show();
        });
    }

    /**
     * two-arg convenience overload for callers that already have a combined "header / content"
     * message and just want the default title.
     *
     * @deprecated use the three-arg form for clearer popup titles.
     */
    static void showSimulationPopup(String header, String content) {
        showSimulationPopup("Simulation", header, content);
    }

    /**
     * appends a line to the simulation log on the server UI.
     *
     * @param message the simulation line to log
     */
    private static void log(String message) {
        ServerPortFrameController.logSimulation(message);
    }

    /** shuts down the scheduler immediately; call when the server stops. */
    public void shutdown() {
        scheduler.shutdownNow();
    }
}
