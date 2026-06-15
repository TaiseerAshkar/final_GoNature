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
 * periodic sweep that detects visitors who never checked in after their scheduled visit time.
 *
 * <p>every {@value #SWEEP_INTERVAL_MINUTES} minutes the service queries for CONFIRMED orders
 * on today's date where {@code entry_time IS NULL} and the scheduled time plus a configurable
 * grace period has already passed. each such order is atomically marked {@code no_show} and
 * the freed slot is offered to the waiting list via
 * {@link DBController#markNoShowsAndPromote(int)}.
 *
 * <p>singleton; call {@link #start(DBController)} once in {@code serverStarted()}. the
 * executor thread is a daemon so it never blocks JVM shutdown. all UI work is marshalled onto
 * the JavaFX Application Thread.
 */
public class NoShowService {

    /** minutes after visit_time before a non-checked-in visitor is marked no_show. */
    public static final int NO_SHOW_GRACE_MINUTES = 15;

    /** how often the sweep runs (in minutes). */
    private static final int SWEEP_INTERVAL_MINUTES = 5;

    private static NoShowService instance;

    private DBController db;

    private final ScheduledExecutorService scheduler =
        Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "no-show-sweeper");
            t.setDaemon(true);
            return t;
        });

    /** use {@link #getInstance()}, not new. */
    private NoShowService() {}

    /**
     * returns (or creates) the singleton.
     *
     * @return the singleton instance; never {@code null}
     */
    public static synchronized NoShowService getInstance() {
        if (instance == null) instance = new NoShowService();
        return instance;
    }

    /**
     * starts the recurring no-show sweep. safe to call once at server startup.
     *
     * @param db the database controller used to detect and mark no-shows
     */
    public void start(DBController db) {
        this.db = db;
        // first sweep 1 minute after start (catches anything already overdue at startup),
        // then every SWEEP_INTERVAL_MINUTES thereafter.
        scheduler.scheduleAtFixedRate(
            this::sweep, 1, SWEEP_INTERVAL_MINUTES * 60L, TimeUnit.SECONDS);
        log("🕐 No-show detector started — grace " + NO_SHOW_GRACE_MINUTES
            + " min, sweeping every " + SWEEP_INTERVAL_MINUTES + " min.");
    }

    /** queries for overdue orders, marks them no_show, and fires a popup per detection. */
    private void sweep() {
        if (db == null) return;
        try {
            List<DBController.NoShowInfo> marked =
                db.markNoShowsAndPromote(NO_SHOW_GRACE_MINUTES);
            for (DBController.NoShowInfo ns : marked) {
                log(String.format(
                    "🚷 No-show: visitor %s did not check in for %s on %s at %s"
                    + " (Order #%d) — status → no_show, slot released.",
                    ns.visitorId, ns.parkName, ns.visitDate, ns.visitTime, ns.orderId));
                showPopup(ns);
            }
        } catch (Exception ex) {
            log("No-show sweep error: " + ex.getMessage());
        }
    }

    /**
     * shows a themed server-side "Simulation" popup for a detected no-show.
     * safe to call from any thread.
     *
     * @param ns the no-show record to display
     */
    private static void showPopup(DBController.NoShowInfo ns) {
        Platform.runLater(() -> {
            Alert alert = new Alert(Alert.AlertType.INFORMATION);
            alert.setTitle("Simulation");
            alert.setHeaderText("No-show detected");
            alert.setContentText(
                "No-show detected: " + ns.visitorId
                + " did not check in for " + ns.parkName
                + " on " + ns.visitDate + " at " + ns.visitTime + ".\n\n"
                + "Order #" + ns.orderId + " marked as no_show.\n"
                + "The slot has been released and offered to the\n"
                + "next visitor in the waiting list.");

            DialogPane pane = alert.getDialogPane();
            pane.setMinWidth(460);
            pane.setGraphic(null);   // remove the default blue info icon
            java.net.URL css = NoShowService.class.getResource("/gui/ServerPort.css");
            if (css != null) pane.getStylesheets().add(css.toExternalForm());
            pane.getStyleClass().add("help-dialog");

            alert.show();
        });
    }

    /** appends a line to the simulation log on the server UI. */
    private static void log(String message) {
        ServerPortFrameController.logSimulation(message);
    }

    /** shuts down the scheduler immediately; call when the server stops. */
    public void shutdown() {
        scheduler.shutdownNow();
    }
}
