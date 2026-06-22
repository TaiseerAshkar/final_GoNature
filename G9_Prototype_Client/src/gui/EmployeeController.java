package gui;

import client.ChatClient;
import client.ClientUI;
import logic.Message;
import logic.OrderDetail;
import logic.Park;
import client.UserSession;
import java.util.List;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.util.Duration;

/**
 * controller for the park employee / manager home screen ({@code EmployeeFrame.fxml}).
 *
 * <p>shows the employee's name, role, and park, then provides access to:
 * <ul>
 *   <li>Today's Reservations</li>
 *   <li>Entry Control (check-in and exit gate)</li>
 *   <li>Waiting List management</li>
 *   <li>Park Reports (PARK_MANAGER only)</li>
 * </ul>
 */
public class EmployeeController {

    @FXML private Label  lblWelcome;
    @FXML private Label  lblRoleBadge;
    @FXML private Label  lblParkBadge;
    @FXML private Label  lblInfo;
    @FXML private Label  lblStats;
    @FXML private Button btnReports;
    @FXML private Button btnParkSettings;
    @FXML private Button btnPromotions;

    // header controls
    @FXML private Label  lblSessionTimer;
    @FXML private Button btnTheme;

    /** background ticker that refreshes the live stats strip every 30 seconds. */
    private Timeline statsTimeline;

    /** ticks once a second to refresh the logged-in-duration label in the header. */
    private Timeline sessionTimeline;

    /**
     * populates welcome/role/park labels and enables the Reports button only for PARK_MANAGER.
     */
    @FXML
    public void initialize() {
        UserSession s = UserSession.getInstance();
        lblWelcome.setText("Welcome, " + s.getFullName() + "!");
        lblRoleBadge.setText(s.getRole());
        lblParkBadge.setText(s.getParkId() != null ? "Park #" + s.getParkId() : "");

        if ("PARK_MANAGER".equals(s.getRole())) {
            lblInfo.setText(
                "As a park manager you can view and edit park parameters,\n" +
                "manage reservations, and generate reports for your park.");
            btnReports.setDisable(false);
            btnParkSettings.setDisable(false);
            btnPromotions.setDisable(false);
        } else {
            lblInfo.setText(
                "As a park employee you can manage visitor check-ins,\n" +
                "handle the waiting list, and process daily reservations.");
            // btnReports is disabled by default in FXML; employees can't access reports
        }

        ThemeManager.installToggle(btnTheme);
        startSessionTimer();

        loadStats();
        startStatsAutoRefresh();
    }

    // â”€â”€ Header: theme toggle + session timer â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

    /**
     * flips the whole app between light and dark mode and updates this button's icon.
     *
     * @param event the button-click event
     */
    @FXML
    public void handleToggleTheme(ActionEvent event) {
        ThemeManager.toggle();
        if (btnTheme != null) btnTheme.setText(ThemeManager.iconText());
    }

    /**
     * starts a 1-second ticker that keeps the "Session: HH:MM:SS" header label current.
     * the window-close handler installed by {@link #startStatsAutoRefresh()} stops this
     * ticker along with the stats ticker.
     */
    private void startSessionTimer() {
        if (lblSessionTimer == null) return;
        updateSessionTimer(); // show 00:00:00 immediately
        sessionTimeline = new Timeline(new KeyFrame(Duration.seconds(1), e -> updateSessionTimer()));
        sessionTimeline.setCycleCount(Timeline.INDEFINITE);
        sessionTimeline.play();
    }

    /** writes the current elapsed session time into the header label. */
    private void updateSessionTimer() {
        UserSession s = UserSession.getInstance();
        if (s != null && lblSessionTimer != null)
            lblSessionTimer.setText("Session: " + s.getSessionDuration());
    }

    /** stops and releases the session-timer ticker if it's running. */
    private void stopSessionTimer() {
        if (sessionTimeline != null) {
            sessionTimeline.stop();
            sessionTimeline = null;
        }
    }

    /**
     * starts a 30-second background ticker that re-runs {@link #loadStats()} so the live
     * stats strip stays current without a logout/login. the {@link Timeline} runs on the
     * JavaFX application thread, and is stopped on logout, exit, or window close via
     * {@link #stopStatsTimeline()}.
     */
    private void startStatsAutoRefresh() {
        if (lblStats == null) return;
        statsTimeline = new Timeline(new KeyFrame(Duration.seconds(30), e -> loadStats()));
        statsTimeline.setCycleCount(Timeline.INDEFINITE);
        statsTimeline.play();

        // stop both tickers if the window is closed outright (logout/exit handle the other cases)
        lblStats.sceneProperty().addListener((obs, oldScene, newScene) -> {
            if (newScene != null) {
                newScene.windowProperty().addListener((o2, oldWin, newWin) -> {
                    if (newWin != null) newWin.setOnHidden(ev -> {
                        stopStatsTimeline();
                        stopSessionTimer();
                    });
                });
            }
        });
    }

    /** stops and releases the stats auto-refresh ticker if it's running. */
    private void stopStatsTimeline() {
        if (statsTimeline != null) {
            statsTimeline.stop();
            statsTimeline = null;
        }
    }

    /**
     * fetches a compact live snapshot for the employee's assigned park and renders it
     * in the stats strip: today's booking count, visitors currently inside, and the
     * resulting capacity utilisation percentage. hides the strip if no park is assigned
     * or the data can't be loaded.
     */
    private void loadStats() {
        if (lblStats == null) return;
        UserSession s = UserSession.getInstance();
        Integer parkId = (s != null) ? s.getParkId() : null;
        if (parkId == null) { hideStats(); return; }

        // 1) today's bookings for this park
        ChatClient.lastTodayOrders = null;
        ClientUI.chat.accept(new Message("GET_TODAY_ORDERS", parkId));
        List<OrderDetail> today = ChatClient.lastTodayOrders;
        int todayCount = (today != null) ? today.size() : 0;

        // 2) visitors currently inside (checked-in, not yet exited)
        ChatClient.lastParkOccupancy = null;
        ClientUI.chat.accept(new Message("GET_PARK_OCCUPANCY", parkId));
        int inside = (ChatClient.lastParkOccupancy != null) ? ChatClient.lastParkOccupancy : 0;

        // 3) park capacity (from the parks list) to compute utilisation
        int capacity = 0;
        ChatClient.lastParkList = null;
        ClientUI.chat.accept(new Message("GET_PARKS", null));
        List<Park> parks = ChatClient.lastParkList;
        if (parks != null) {
            for (Park p : parks) {
                if (p.getId() == parkId) {
                    capacity = p.getCapacity();
                    s.setParkName(p.getName());
                    if (lblParkBadge != null) lblParkBadge.setText(p.getName());
                    break;
                }
            }
        }

        int pct = (capacity > 0) ? (int) Math.round(inside * 100.0 / capacity) : 0;
        lblStats.setText(
            "ðŸ“… Today's bookings: " + todayCount
            + "      ðŸ‘¥ Currently inside: " + inside
            + "      ðŸ• Capacity: " + pct + "%");
    }

    /** collapses the stats strip when there's nothing meaningful to show. */
    private void hideStats() {
        lblStats.setVisible(false);
        lblStats.setManaged(false);
    }

    // â”€â”€ Button handlers â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

    /**
     * opens Today's Reservations as a modal for the employee's assigned park.
     */
    @FXML
    public void handleTodayReservations(ActionEvent event) {
        openModal(stage -> new TodayReservationsController().start(stage),
                  "GoNature â€” Today's Reservations");
    }

    /** opens Entry Control as a modal. */
    @FXML
    public void handleEntryControl(ActionEvent event) {
        openModal(stage -> new EntryControlController().start(stage),
                  "GoNature â€” Entry Control");
    }

    /** opens the Waiting List screen as a modal for the employee's assigned park. */
    @FXML
    public void handleWaitingList(ActionEvent event) {
        openModal(stage -> new WaitingListController().start(stage),
                  "GoNature â€” Waiting List");
    }

    /**
     * opens Reports locked to this park. sets {@link ReportsController#lockedParkId} first
     * so the dashboard pre-filters and disables the park filter combo-box.
     */
    @FXML
    public void handleViewReports(ActionEvent event) {
        UserSession s = UserSession.getInstance();
        ReportsController.lockedParkId = s.getParkId();   // lock before opening
        openModal(stage -> new ReportsController().start(stage),
                  "GoNature â€” Park Reports");
    }

    /**
     * opens the Park Settings panel so the manager can edit capacity, max orders,
     * visit duration, and full price for their assigned park.
     */
    @FXML
    public void handleParkSettings(ActionEvent event) {
        openModal(stage -> new ParkSettingsController().start(stage),
                  "GoNature â€” Park Settings");
    }

    /**
     * opens the Promotions panel so the manager can propose discount promotions for their
     * assigned park. Promotions require department manager approval before they affect pricing.
     */
    @FXML
    public void handlePromotions(ActionEvent event) {
        openModal(stage -> new PromotionsController().start(stage),
                  "GoNature â€” Promotions");
    }

    // â”€â”€ Utility â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

    /** lambda that opens a screen into a given stage; may throw on FXML load failure. */
    @FunctionalInterface
    private interface ModalOpener {
        void open(Stage stage) throws Exception;
    }

    /**
     * creates a child modal stage, calls {@code opener} to populate it, and shows it.
     *
     * @param opener        the functional interface that loads a screen into the stage
     * @param fallbackTitle the window title (currently unused but kept for future error dialogs)
     */
    private void openModal(ModalOpener opener, String fallbackTitle) {
        try {
            Stage modal = new Stage();
            modal.initModality(Modality.WINDOW_MODAL);
            modal.initOwner(lblWelcome.getScene().getWindow());
            opener.open(modal);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // â”€â”€ Standard buttons â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

    /** @param event the button-click event */
    @FXML
    public void handleExit(ActionEvent event) {
        stopStatsTimeline();
        stopSessionTimer();
        if (ClientUI.chat != null) ClientUI.chat.disconnect();
        Platform.exit();
    }

    /**
     * shows role-specific help (PARK_MANAGER vs PARK_EMPLOYEE content differs).
     */
    @FXML
    public void handleHelp(ActionEvent event) {
        UserSession s = UserSession.getInstance();
        String role = (s != null ? s.getRole() : "PARK_EMPLOYEE");
        String content;
        if ("PARK_MANAGER".equals(role)) {
            content =
                "PARK MANAGER â€” QUICK GUIDE\n\n" +
                "TODAY'S RESERVATIONS\n" +
                "  â€¢ Click 'View Today's Reservations' to see all bookings for today at your park.\n\n" +
                "ENTRY CONTROL\n" +
                "  â€¢ Click 'Entry Control (Check-In / Exit)' to open the gate console.\n" +
                "  â€¢ Use the Check-In tab to admit visitors (pre-booked or walk-in).\n" +
                "  â€¢ Use the Register Exit tab to log departures.\n\n" +
                "WAITING LIST\n" +
                "  â€¢ Click 'Manage Waiting List' to see visitors queued for your park.\n\n" +
                "REPORTS DASHBOARD\n" +
                "  â€¢ Click 'View Park Reports' to open your park's analytics.\n" +
                "  â€¢ The park filter is locked to your park automatically.\n" +
                "  â€¢ Visitor Report: daily visitor counts split by booking type.\n" +
                "  â€¢ Cancellation Report: cancelled and no-show orders, plus a day-of-week\n" +
                "    distribution chart with average cancellations per weekday.\n" +
                "  â€¢ Usage Report: hourly park capacity utilisation.\n\n" +
                "PRICING (ENTRY CONTROL)\n" +
                "  â€¢ Pre-booked SOLO or FAMILY: 15% advance discount.\n" +
                "  â€¢ Pre-booked GROUP (guide-led): guide enters free + 25%+12% advance discount\n" +
                "    (about 34% off) for the rest of the group.\n" +
                "  â€¢ Walk-in SOLO â€” subscriber: 10% discount (Family Member Club benefit).\n" +
                "  â€¢ Walk-in SOLO â€” regular visitor: full price.\n" +
                "  â€¢ Walk-in GROUP (guide-led): guide pays; 10% discount applies to everyone\n" +
                "    including the guide.";
        } else {
            content =
                "PARK EMPLOYEE â€” QUICK GUIDE\n\n" +
                "TODAY'S RESERVATIONS\n" +
                "  â€¢ Click 'View Today's Reservations' to see all bookings for today at your park.\n\n" +
                "CHECKING IN A VISITOR\n" +
                "  â€¢ Click 'Entry Control (Check-In / Exit)' then go to the Check-In tab.\n" +
                "  â€¢ Enter the visitor's ID number and select the park.\n" +
                "  â€¢ Pre-booked visitors: the system finds today's confirmed booking automatically.\n" +
                "  â€¢ Walk-in visitors: also enter number of visitors and order type, then click Check In.\n" +
                "  â€¢ Unknown visitor ID: if the visitor has no account, the system creates a minimal\n" +
                "    record automatically â€” no pre-registration needed.\n\n" +
                "REGISTERING AN EXIT\n" +
                "  â€¢ Go to the 'Register Exit' tab.\n" +
                "  â€¢ Enter the visitor's ID number and click 'Register Exit'.\n\n" +
                "WAITING LIST\n" +
                "  â€¢ Click 'Manage Waiting List' to see visitors queued for your park.\n\n" +
                "NOTES\n" +
                "  â€¢ A visitor already checked in today cannot check in a second time.\n" +
                "  â€¢ Walk-ins are only allowed when the park has sufficient remaining capacity.\n" +
                "  â€¢ All pricing is calculated automatically at check-in time.";
        }
        showHelp("GoNature â€” Help (" + role + ")", content);
    }

    /**
     * @param title   the dialog header text
     * @param content the help body text shown in the scrollable text area
     */
    private void showHelp(String title, String content) {
        HelpDialog.show(title, content);
    }

    /**
     * clears the session and returns to the login screen; falls back to exit on FXML failure.
     */
    @FXML
    public void handleLogout(ActionEvent event) {
        stopStatsTimeline();
        stopSessionTimer();   // stop before clearing the session so the clock doesn't tick on a null user
        if (ClientUI.chat != null) ClientUI.chat.sendLogout();
        UserSession.clear();
        Stage stage = (Stage) lblWelcome.getScene().getWindow();
        try {
            new LoginController().start(stage);
        } catch (Exception e) {
            if (ClientUI.chat != null) ClientUI.chat.disconnect();
            Platform.exit();
        }
    }

    /**
     * @param primaryStage the stage to display the screen in
     * @throws Exception if the FXML file cannot be loaded
     */
    public void start(Stage primaryStage) throws Exception {
        Parent root = FXMLLoader.load(getClass().getResource("/gui/EmployeeFrame.fxml"));
        primaryStage.setTitle("GoNature â€” " + UserSession.getInstance().getRole());
        Scene scene = new Scene(root);
        ThemeManager.register(scene);   // apply the current light/dark theme to this screen
        primaryStage.setScene(scene);
        primaryStage.centerOnScreen();
        primaryStage.show();
        Animations.introduce(root);     // subtle fade-and-rise on load
    }
}

