package gui;

import client.ClientUI;
import logic.UserSession;
import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.stage.Modality;
import javafx.stage.Stage;

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
    @FXML private Button btnReports;
    @FXML private Button btnParkSettings;

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
        } else {
            lblInfo.setText(
                "As a park employee you can manage visitor check-ins,\n" +
                "handle the waiting list, and process daily reservations.");
            // btnReports is disabled by default in FXML — employees can't access reports
        }
    }

    // ── Button handlers ───────────────────────────────────────────────────────

    /**
     * opens Today's Reservations as a modal for the employee's assigned park.
     */
    @FXML
    public void handleTodayReservations(ActionEvent event) {
        openModal(stage -> new TodayReservationsController().start(stage),
                  "GoNature — Today's Reservations");
    }

    /** opens Entry Control as a modal. */
    @FXML
    public void handleEntryControl(ActionEvent event) {
        openModal(stage -> new EntryControlController().start(stage),
                  "GoNature — Entry Control");
    }

    /** opens the Waiting List screen as a modal for the employee's assigned park. */
    @FXML
    public void handleWaitingList(ActionEvent event) {
        openModal(stage -> new WaitingListController().start(stage),
                  "GoNature — Waiting List");
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
                  "GoNature — Park Reports");
    }

    /**
     * opens the Park Settings panel so the manager can edit capacity, max orders,
     * visit duration, and full price for their assigned park.
     */
    @FXML
    public void handleParkSettings(ActionEvent event) {
        openModal(stage -> new ParkSettingsController().start(stage),
                  "GoNature — Park Settings");
    }

    // ── Utility ───────────────────────────────────────────────────────────────

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

    // ── Standard buttons ──────────────────────────────────────────────────────

    /** @param event the button-click event */
    @FXML
    public void handleExit(ActionEvent event) {
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
                "PARK MANAGER — QUICK GUIDE\n\n" +
                "TODAY'S RESERVATIONS\n" +
                "  • Click 'View Today's Reservations' to see all bookings for today at your park.\n\n" +
                "ENTRY CONTROL\n" +
                "  • Click 'Entry Control (Check-In / Exit)' to open the gate console.\n" +
                "  • Use the Check-In tab to admit visitors (pre-booked or walk-in).\n" +
                "  • Use the Register Exit tab to log departures.\n\n" +
                "WAITING LIST\n" +
                "  • Click 'Manage Waiting List' to see visitors queued for your park.\n\n" +
                "REPORTS DASHBOARD\n" +
                "  • Click 'View Park Reports' to open your park's analytics.\n" +
                "  • The park filter is locked to your park automatically.\n" +
                "  • Visitor Report: daily visitor counts split by booking type.\n" +
                "  • Cancellation Report: all cancelled and no-show orders.\n" +
                "  • Usage Report: hourly park capacity utilisation.\n\n" +
                "PRICING (ENTRY CONTROL)\n" +
                "  • Pre-booked SOLO or GROUP (any visitor): 15% advance discount.\n" +
                "  • Walk-in SOLO — subscriber: 15% discount (Family Member Club benefit).\n" +
                "  • Walk-in SOLO — regular visitor: full price.\n" +
                "  • GROUP led by guide (pre-booked): guide free + 25% discount for others.\n" +
                "  • GROUP led by guide (walk-in): guide free + 10% discount for others.";
        } else {
            content =
                "PARK EMPLOYEE — QUICK GUIDE\n\n" +
                "TODAY'S RESERVATIONS\n" +
                "  • Click 'View Today's Reservations' to see all bookings for today at your park.\n\n" +
                "CHECKING IN A VISITOR\n" +
                "  • Click 'Entry Control (Check-In / Exit)' then go to the Check-In tab.\n" +
                "  • Enter the visitor's ID number and select the park.\n" +
                "  • Pre-booked visitors: the system finds today's confirmed booking automatically.\n" +
                "  • Walk-in visitors: also enter number of visitors and order type, then click Check In.\n\n" +
                "REGISTERING AN EXIT\n" +
                "  • Go to the 'Register Exit' tab.\n" +
                "  • Enter the visitor's ID number and click 'Register Exit'.\n\n" +
                "WAITING LIST\n" +
                "  • Click 'Manage Waiting List' to see visitors queued for your park.\n\n" +
                "NOTES\n" +
                "  • A visitor already checked in today cannot check in a second time.\n" +
                "  • Walk-ins are only allowed when the park has sufficient remaining capacity.\n" +
                "  • All pricing is calculated automatically at check-in time.";
        }
        showHelp("GoNature — Help (" + role + ")", content);
    }

    /**
     * @param title   the dialog header text
     * @param content the help body text shown in the scrollable text area
     */
    private void showHelp(String title, String content) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("Help");
        alert.setHeaderText(title);
        TextArea ta = new TextArea(content);
        ta.setEditable(false);
        ta.setWrapText(true);
        ta.setPrefRowCount(20);
        ta.setPrefWidth(480);
        ta.setStyle("-fx-font-family: 'Segoe UI', Arial, sans-serif; -fx-font-size: 12px;");
        alert.getDialogPane().setContent(ta);
        alert.getDialogPane().setMinWidth(520);
        alert.showAndWait();
    }

    /**
     * clears the session and returns to the login screen; falls back to exit on FXML failure.
     */
    @FXML
    public void handleLogout(ActionEvent event) {
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
        primaryStage.setTitle("GoNature — " + UserSession.getInstance().getRole());
        primaryStage.setScene(new Scene(root));
        primaryStage.show();
    }
}
