package Server;

import gui.ServerPortFrameController;
import javafx.application.Platform;
import javafx.scene.control.Alert;
import javafx.scene.control.DialogPane;
import logic.CancelResult;

import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * background scheduler for reminder and auto-cancellation simulation.
 *
 * <p>singleton; call {@link #runStartupCheck(DBController)} once at server start.
 * it queries tomorrow's orders, logs a reminder simulation line for each, shows a popup,
 * then schedules auto-cancellation after {@value #CONFIRM_WINDOW_HOURS} hour(s).
 *
 * <p>all UI updates go through {@link Platform#runLater}. the executor thread is a daemon
 * so it doesn't block JVM shutdown.
 */
public class ReminderService {

    /** Hours to wait after sending reminders before auto-cancelling unconfirmed orders. */
    private static final long CONFIRM_WINDOW_HOURS = 2;

    private static ReminderService instance;

    private final ScheduledExecutorService scheduler =
        Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "reminder-service");
            t.setDaemon(true);
            return t;
        });

    /**
     * order IDs that were sent a reminder at startup and are therefore in the auto-cancel window.
     * populated once in {@link #runStartupCheck}; queried in {@link #confirmVisit}.
     */
    private final java.util.Set<Integer> remindedOrderIds =
        java.util.Collections.newSetFromMap(new java.util.concurrent.ConcurrentHashMap<>());

    /**
     * order IDs that the visitor has explicitly confirmed during the window.
     * populated by {@link #confirmVisit}; consulted in {@link #runAutoCancelCheck}.
     */
    private final java.util.Set<Integer> confirmedOrderIds =
        java.util.Collections.newSetFromMap(new java.util.concurrent.ConcurrentHashMap<>());

    /** use {@link #getInstance()}, not new. */
    private ReminderService() {}

    /**
     * returns (or creates) the singleton.
     *
     * @return the singleton instance; never {@code null}
     */
    public static synchronized ReminderService getInstance() {
        if (instance == null) instance = new ReminderService();
        return instance;
    }

    /**
     * queries tomorrow's orders, logs a simulation reminder for each, shows a popup,
     * and schedules the auto-cancel check. returns immediately (async).
     *
     * @param db the database controller used to query and later cancel the orders
     */
    public void runStartupCheck(DBController db) {
        scheduler.execute(() -> {
            List<ReminderOrderInfo> orders = db.getTomorrowOrdersForReminder();
            if (orders.isEmpty()) {
                log("No orders scheduled for tomorrow — no reminders needed.");
                return;
            }

            log("=== Startup Reminder Check — " + orders.size() + " order(s) for tomorrow ===");
            for (ReminderOrderInfo o : orders) remindedOrderIds.add(o.id);

            StringBuilder popup = new StringBuilder();
            for (ReminderOrderInfo o : orders) {
                String line = String.format(
                    "📧 Reminder sent → %s  |  %s  |  %s at %s  |  Order #%d",
                    o.email, o.parkName, o.visitDate, o.visitTime, o.id);
                log(line);
                EmailService.sendEmail(o.email,
                    "GoNature — Visit Reminder (Order #" + o.id + ")",
                    "Hello,\n\n" +
                    "This is a friendly reminder about your visit tomorrow.\n\n" +
                    "Order #: " + o.id + "\n" +
                    "Park: " + o.parkName + "\n" +
                    "Date: " + o.visitDate + " at " + o.visitTime + "\n\n" +
                    "Please confirm your visit in the GoNature app within " + CONFIRM_WINDOW_HOURS +
                    " hour(s), or your booking may be automatically cancelled.\n\n" +
                    "— GoNature Parks");
                popup.append("• ").append(o.email)
                     .append(" — ").append(o.parkName)
                     .append(" on ").append(o.visitDate)
                     .append(" at ").append(o.visitTime).append("\n");
            }

            final String alertContent = popup.toString();
            final int    count        = orders.size();
            Platform.runLater(() -> {
                Alert alert = new Alert(Alert.AlertType.INFORMATION);
                alert.setTitle("Simulation — Reminder Sent");
                alert.setHeaderText(count + " day-before reminder(s) sent");
                alert.setContentText(
                    alertContent.trim() + "\n\n"
                    + "Visitors have " + CONFIRM_WINDOW_HOURS
                    + " hour(s) to confirm. Unconfirmed bookings will be auto-cancelled.");

                DialogPane pane = alert.getDialogPane();
                pane.setMinWidth(500);
                pane.setGraphic(null);   // remove the default blue info icon
                java.net.URL css = ReminderService.class.getResource("/gui/ServerPort.css");
                if (css != null) pane.getStylesheets().add(css.toExternalForm());
                pane.getStyleClass().add("help-dialog");

                alert.show();
            });

            log("Auto-cancel scheduled in " + CONFIRM_WINDOW_HOURS + " hour(s).");
            scheduler.schedule(() -> runAutoCancelCheck(db, orders),
                CONFIRM_WINDOW_HOURS, TimeUnit.HOURS);
        });
    }

    /**
     * cancels each reminded order after the confirmation window expires,
     * skipping any order the visitor explicitly confirmed via {@link #confirmVisit}.
     *
     * @param db       the database controller used to cancel orders
     * @param reminded the orders that were sent reminders at startup
     */
    private void runAutoCancelCheck(DBController db, List<ReminderOrderInfo> reminded) {
        log("=== Auto-Cancel Check (" + CONFIRM_WINDOW_HOURS
            + "-hour(s) confirmation window expired) ===");
        for (ReminderOrderInfo o : reminded) {
            if (confirmedOrderIds.contains(o.id)) {
                log("✅ Order #" + o.id + " visitor-confirmed — auto-cancel skipped.");
                continue;
            }
            CancelResult result = db.cancelOrder(o.id);
            if (result.isSuccess()) {
                log(String.format(
                    "🚫 Order #%d auto-cancelled — notification sent to %s",
                    o.id, o.email));
                EmailService.sendEmail(o.email,
                    "GoNature — Booking Auto-Cancelled (Order #" + o.id + ")",
                    "Hello,\n\n" +
                    "Your booking was automatically cancelled because it wasn't confirmed in time.\n\n" +
                    "Order #: " + o.id + "\n" +
                    "Park: " + o.parkName + "\n" +
                    "Date: " + o.visitDate + " at " + o.visitTime + "\n\n" +
                    "You're welcome to book again any time on GoNature.\n\n" +
                    "— GoNature Parks");
                if (result.getNotifiedEmail() != null) {
                    log("📨 Waiting-list notification → " + result.getNotifiedEmail() +
                        " — you have " + CONFIRM_WINDOW_HOURS + " hour(s) to confirm your spot");
                }
            } else {
                log("Order #" + o.id +
                    " already completed or cancelled — no action taken.");
            }
        }
    }

    /**
     * called when a visitor sends a {@code CONFIRM_VISIT} message.
     * records the confirmation so {@link #runAutoCancelCheck} will skip this order.
     *
     * @param orderId the order ID the visitor is confirming
     * @return {@code true} if the order was in the reminder window; {@code false} if it
     *         was not queued (visitor can still "confirm" but auto-cancel was never going to fire)
     */
    public boolean confirmVisit(int orderId) {
        confirmedOrderIds.add(orderId);
        boolean wasQueued = remindedOrderIds.contains(orderId);
        if (wasQueued) {
            log("✅ Visitor confirmed Order #" + orderId
                + " — removed from auto-cancel queue.");
        } else {
            log("ℹ️  Visitor confirmed Order #" + orderId
                + " (not in current auto-cancel window — confirmation recorded anyway).");
        }
        return wasQueued;
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
