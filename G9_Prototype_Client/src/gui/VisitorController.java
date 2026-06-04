package gui;

import client.ChatClient;
import client.ClientUI;
import client.NotificationCenter;
import logic.ExitRequest;
import logic.Message;
import logic.OrderDetail;
import logic.Park;
import logic.UserSession;
import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.ChoiceDialog;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.TextArea;
import javafx.stage.Modality;
import javafx.stage.Stage;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * controller for the visitor / guide home screen ({@code VisitorFrame.fxml}).
 *
 * <p>shows a personalised welcome message, action buttons, and the simulated
 * notification inbox populated by {@link NotificationCenter}.
 * on load it also checks for any confirmed bookings tomorrow and adds
 * a reminder notification automatically.
 */
public class VisitorController {

    @FXML private Label           lblWelcome;
    @FXML private Label           lblRoleBadge;
    @FXML private Label           lblInfo;

    // notification panel
    @FXML private Label           lblNotifBadge;
    @FXML private Label           lblNoNotif;
    @FXML private ListView<String> lstNotifications;

    private static final DateTimeFormatter DATE_FMT =
        DateTimeFormatter.ofPattern("yyyy-MM-dd");

    /**
     * populates welcome/role labels, wires the notification list cell factory,
     * checks for upcoming-visit reminders, and renders the notification inbox.
     */
    @FXML
    public void initialize() {
        UserSession s = UserSession.getInstance();
        lblWelcome.setText("Welcome, " + s.getFullName() + "!");
        lblRoleBadge.setText(s.getRole());
        switch (s.getRole()) {
            case "GUIDE":
                lblInfo.setText(
                    "As a certified guide you can lead group visits and book solo visits.\n" +
                    "You enter free when leading a group.");
                break;
            case "SUBSCRIBER":
                lblInfo.setText(
                    "Family Member Club — your ID gives you a 15% discount on walk-in visits,\n" +
                    "on top of the standard 15% advance-booking discount.");
                break;
            default: // VISITOR
                lblInfo.setText(
                    "As a visitor you can browse available parks,\n" +
                    "book visits, and manage your existing reservations.");
        }

        // wrap long notification lines so they don't get clipped
        lstNotifications.setCellFactory(lv -> new ListCell<>() {
            { setWrapText(true); }
            @Override protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : item);
                setPrefWidth(0); // lets the cell fill ListView width
            }
        });

        checkUpcomingReminders();
        refreshNotifications();
    }

    // ── Notification panel ────────────────────────────────────────────────────

    /**
     * refreshes the notification panel from {@link NotificationCenter}.
     * re-checks for upcoming-visit reminders before rendering.
     *
     * @param event the button-click event
     */
    @FXML
    public void handleRefreshNotifications(ActionEvent event) {
        checkUpcomingReminders();
        refreshNotifications();
    }

    /**
     * syncs the ListView (and badge count) with the current contents of
     * {@link NotificationCenter}. toggles the empty-state label as needed.
     */
    private void refreshNotifications() {
        List<String> all = NotificationCenter.getAll();
        int count = all.size();

        lblNotifBadge.setText(String.valueOf(count));

        boolean empty = count == 0;
        lblNoNotif.setVisible(empty);
        lblNoNotif.setManaged(empty);
        lstNotifications.setVisible(!empty);
        lstNotifications.setManaged(!empty);

        if (!empty) {
            lstNotifications.getItems().setAll(all);
        }
    }

    /**
     * fetches the visitor's orders and adds a reminder notification for any
     * confirmed/pending booking that falls tomorrow. only adds the reminder once
     * per session (checked by scanning existing entries).
     */
    private void checkUpcomingReminders() {
        UserSession s = UserSession.getInstance();
        if (s == null || s.getUserId() == null) return;

        ChatClient.lastMyOrders = null;
        ClientUI.chat.accept(new Message("GET_MY_ORDERS", s.getUserId()));
        List<OrderDetail> orders = ChatClient.lastMyOrders;
        if (orders == null || orders.isEmpty()) return;

        String tomorrow = LocalDate.now().plusDays(1).format(DATE_FMT);

        // skip if we already added reminder notifications for tomorrow this session
        boolean alreadyDone = NotificationCenter.getAll().stream()
            .anyMatch(n -> n.contains("🔔") && n.contains(tomorrow));
        if (alreadyDone) return;

        for (OrderDetail o : orders) {
            if (tomorrow.equals(o.getVisitDate())
                    && ("CONFIRMED".equals(o.getStatus()) || "PENDING".equals(o.getStatus()))) {
                NotificationCenter.add("🔔",
                    "Reminder: your visit to " + o.getParkName()
                    + " is tomorrow at " + o.getVisitTime()
                    + " (Order #" + o.getId() + "). Please arrive 15 minutes early.");
            }
        }
    }

    // ── Button handlers ───────────────────────────────────────────────────────

    /**
     * opens the booking form as a modal. after it closes the notification panel
     * will show the new booking notification on the next refresh.
     *
     * @param event the button-click event
     */
    @FXML
    public void handleBookVisit(ActionEvent event) {
        try {
            Stage bookingStage = new Stage();
            bookingStage.initModality(Modality.WINDOW_MODAL);
            bookingStage.initOwner(lblWelcome.getScene().getWindow());
            // refresh notifications when the booking modal closes
            bookingStage.setOnHidden(e -> refreshNotifications());
            new BookingController().start(bookingStage);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * opens My Orders as a modal. after it closes the notification panel
     * will reflect any cancellation notifications that were added.
     *
     * @param event the button-click event
     */
    @FXML
    public void handleViewOrders(ActionEvent event) {
        try {
            Stage ordersStage = new Stage();
            ordersStage.initModality(Modality.WINDOW_MODAL);
            ordersStage.initOwner(lblWelcome.getScene().getWindow());
            // refresh notifications when the orders modal closes
            ordersStage.setOnHidden(e -> refreshNotifications());
            new MyOrdersController().start(ordersStage);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * confirms all of the visitor's tomorrow's PENDING/CONFIRMED bookings so the server
     * will not auto-cancel them at the end of the 2-hour reminder window.
     * if there are multiple eligible orders a choice dialog is shown.
     *
     * @param event the button-click event
     */
    @FXML
    public void handleConfirmVisit(ActionEvent event) {
        UserSession s = UserSession.getInstance();
        if (s == null || s.getUserId() == null) return;

        // fetch the visitor's orders
        ChatClient.lastMyOrders = null;
        ClientUI.chat.accept(new Message("GET_MY_ORDERS", s.getUserId()));
        List<OrderDetail> orders = ChatClient.lastMyOrders;

        if (orders == null || orders.isEmpty()) {
            NotificationCenter.add("ℹ️", "No reservations found to confirm.");
            refreshNotifications();
            return;
        }

        String tomorrow = LocalDate.now().plusDays(1).format(DATE_FMT);
        List<OrderDetail> eligible = orders.stream()
            .filter(o -> tomorrow.equals(o.getVisitDate())
                && ("CONFIRMED".equals(o.getStatus()) || "PENDING".equals(o.getStatus())))
            .collect(Collectors.toList());

        if (eligible.isEmpty()) {
            NotificationCenter.add("ℹ️",
                "No bookings for tomorrow that need confirmation.");
            refreshNotifications();
            return;
        }

        // if multiple eligible orders, let visitor choose which to confirm
        List<OrderDetail> toConfirm = new ArrayList<>();
        if (eligible.size() == 1) {
            toConfirm.add(eligible.get(0));
        } else {
            // build human-readable labels for the choice dialog
            List<String> labels = eligible.stream()
                .map(o -> "Order #" + o.getId()
                    + " — " + o.getParkName()
                    + " at " + o.getVisitTime()
                    + " (" + o.getNumVisitors() + " visitor(s))")
                .collect(Collectors.toList());
            ChoiceDialog<String> dlg = new ChoiceDialog<>(labels.get(0), labels);
            dlg.setTitle("Confirm My Visit");
            dlg.setHeaderText("You have " + eligible.size() + " bookings for tomorrow.");
            dlg.setContentText("Select the booking to confirm:");
            dlg.showAndWait().ifPresent(chosen -> {
                int idx = labels.indexOf(chosen);
                if (idx >= 0) toConfirm.add(eligible.get(idx));
            });
        }

        for (OrderDetail order : toConfirm) {
            ChatClient.lastConfirmVisitOk    = null;
            ChatClient.lastConfirmVisitError = null;
            ClientUI.chat.accept(new Message("CONFIRM_VISIT", order.getId()));

            if (Boolean.TRUE.equals(ChatClient.lastConfirmVisitOk)) {
                NotificationCenter.add("✅",
                    "Visit confirmed — Order #" + order.getId()
                    + " at " + order.getParkName()
                    + " tomorrow at " + order.getVisitTime()
                    + ". Your booking will not be auto-cancelled. See you there!");
            } else {
                String err = ChatClient.lastConfirmVisitError != null
                    ? ChatClient.lastConfirmVisitError
                    : "Could not confirm Order #" + order.getId() + ". Please try again.";
                NotificationCenter.add("⚠️", err);
            }
        }
        refreshNotifications();
    }

    /**
     * lets the visitor register their own park exit without needing an employee.
     * loads the park list, asks the visitor to select which park they are leaving,
     * then sends {@code REGISTER_EXIT} using their own session ID.
     *
     * @param event the button-click event
     */
    @FXML
    public void handleRegisterExit(ActionEvent event) {
        // fetch parks so the visitor can select which one they're leaving
        ChatClient.lastParkList = null;
        ClientUI.chat.accept(new Message("GET_PARKS", null));
        List<Park> parks = ChatClient.lastParkList;

        if (parks == null || parks.isEmpty()) {
            NotificationCenter.add("⚠️", "Could not load park list. Please try again.");
            refreshNotifications();
            return;
        }

        // ChoiceDialog uses Park.toString() which returns the park name — no converter needed
        ChoiceDialog<Park> dlg = new ChoiceDialog<>(parks.get(0), parks);
        dlg.setTitle("Register My Exit");
        dlg.setHeaderText("🚗  Leaving a Park");
        dlg.setContentText("Select the park you are leaving:");

        dlg.showAndWait().ifPresent(park -> {
            UserSession s = UserSession.getInstance();
            if (s == null || s.getUserId() == null) return;

            ChatClient.lastExitSuccess = false;
            ChatClient.lastExitError   = null;
            ClientUI.chat.accept(new Message("REGISTER_EXIT",
                new ExitRequest(s.getUserId(), park.getId())));

            if (ChatClient.lastExitSuccess) {
                NotificationCenter.add("🚗",
                    "Exit from " + park.getName() + " registered — thank you for visiting! Have a great day.");
            } else {
                String reason = ChatClient.lastExitError != null
                    ? ChatClient.lastExitError
                    : "No active check-in found for today at " + park.getName() + ".";
                NotificationCenter.add("⚠️", "Exit registration failed — " + reason);
            }
            refreshNotifications();
        });
    }

    /**
     * shows a role-specific help dialog.
     *
     * @param event the button-click event
     */
    @FXML
    public void handleHelp(ActionEvent event) {
        UserSession s = UserSession.getInstance();
        String content;
        if ("GUIDE".equals(s != null ? s.getRole() : "")) {
            content =
                "GROUP BOOKING (Guide)\n" +
                "  • Click 'Book a Visit' and select visit type GROUP.\n" +
                "  • Enter the total headcount including yourself (2–16 people).\n" +
                "  • You enter the park free of charge as the group leader.\n" +
                "  • A 25% advance-booking discount applies to the rest of the group.\n\n" +
                "SOLO BOOKING (Guide visiting alone)\n" +
                "  • Select SOLO — treated the same as any other solo visitor.\n" +
                "  • 15% pre-booked discount applied automatically.\n\n" +
                "MANAGING ORDERS\n" +
                "  • Use 'View My Reservations' to see and cancel bookings.\n\n" +
                "NOTIFICATIONS\n" +
                "  • The Notifications panel shows booking confirmations, cancellations,\n" +
                "    waiting-list alerts, and day-before reminders.\n" +
                "  • Click '↻ Refresh' to pull the latest events.\n\n" +
                "STATUS COLOURS\n" +
                "  GREEN = Confirmed  |  ORANGE = Pending  |  GREY = Completed\n" +
                "  RED = Cancelled";
        } else {
            content =
                "BOOKING A VISIT\n" +
                "  • Click 'Book a Visit' to open the reservation form.\n" +
                "  • SOLO: 1 visitor — 15% pre-booked discount.\n" +
                "  • GROUP: 2–15 visitors — 15% group discount.\n\n" +
                "VIEWING YOUR RESERVATIONS\n" +
                "  • Click 'View My Reservations' to see all your bookings.\n\n" +
                "CANCELLING A RESERVATION\n" +
                "  • Select an order in 'View My Reservations' and click 'Cancel Order'.\n" +
                "  • Only PENDING or CONFIRMED orders can be cancelled.\n\n" +
                "WAITING LIST\n" +
                "  • If a park is fully booked, you can join the waiting list.\n" +
                "  • You'll get a notification here if a spot opens.\n\n" +
                "NOTIFICATIONS\n" +
                "  • The Notifications panel shows booking confirmations, cancellations,\n" +
                "    waiting-list alerts, and day-before reminders.\n" +
                "  • Click '↻ Refresh' to pull the latest events.";
        }
        showHelp("GoNature — Help (" + (s != null ? s.getRole() : "VISITOR") + ")", content);
    }

    /**
     * @param title   the dialog title
     * @param content the help body text
     */
    private void showHelp(String title, String content) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("Help");
        alert.setHeaderText(title);
        TextArea ta = new TextArea(content);
        ta.setEditable(false);
        ta.setWrapText(true);
        ta.setPrefRowCount(18);
        ta.setPrefWidth(460);
        ta.setStyle("-fx-font-family: 'Segoe UI', Arial, sans-serif; -fx-font-size: 12px;");
        alert.getDialogPane().setContent(ta);
        alert.getDialogPane().setMinWidth(500);
        alert.showAndWait();
    }

    /**
     * clears notifications, clears the session, and returns to the login screen.
     *
     * @param event the button-click event
     */
    @FXML
    public void handleLogout(ActionEvent event) {
        NotificationCenter.clear();
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
        Parent root = FXMLLoader.load(getClass().getResource("/gui/VisitorFrame.fxml"));
        primaryStage.setTitle("GoNature — " + UserSession.getInstance().getRole());
        primaryStage.setScene(new Scene(root));
        primaryStage.show();
    }
}
