package gui;

import client.ChatClient;
import client.ClientUI;
import client.NotificationCenter;
import logic.ExitRequest;
import logic.Message;
import logic.OrderDetail;
import logic.Park;
import logic.UserSession;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ChoiceDialog;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.DialogPane;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.Separator;
import javafx.scene.control.TextArea;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.util.Duration;

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
    @FXML private Label           lblUpcoming;

    // header controls
    @FXML private Label           lblSessionTimer;
    @FXML private Button          btnTheme;

    // notification panel
    @FXML private Label           lblNotifBadge;
    @FXML private Label           lblNoNotif;
    @FXML private ListView<String> lstNotifications;

    /** ticks once a second to refresh the logged-in-duration label in the header. */
    private Timeline sessionTimeline;

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

        ThemeManager.installToggle(btnTheme);
        startSessionTimer();

        checkUpcomingReminders();
        updateUpcomingCount();
        refreshNotifications();
    }

    // ── Header: theme toggle + session timer ──────────────────────────────────

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
     * starts a 1-second ticker that keeps the "Session: HH:MM:SS" label current, and stops
     * it automatically if the window is closed.
     */
    private void startSessionTimer() {
        if (lblSessionTimer == null) return;
        updateSessionTimer(); // show 00:00:00 immediately
        sessionTimeline = new Timeline(new KeyFrame(Duration.seconds(1), e -> updateSessionTimer()));
        sessionTimeline.setCycleCount(Timeline.INDEFINITE);
        sessionTimeline.play();

        lblSessionTimer.sceneProperty().addListener((obs, oldScene, newScene) -> {
            if (newScene != null) {
                newScene.windowProperty().addListener((o2, oldWin, newWin) -> {
                    if (newWin != null) newWin.setOnHidden(ev -> stopSessionTimer());
                });
            }
        });
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
     * counts the visitor's active upcoming reservations (status PENDING or CONFIRMED
     * with a visit date today or later) and shows a friendly summary on the home card.
     * reuses the order list already fetched by {@link #checkUpcomingReminders()}.
     */
    private void updateUpcomingCount() {
        if (lblUpcoming == null) return;
        List<OrderDetail> orders = ChatClient.lastMyOrders;
        if (orders == null) { lblUpcoming.setText("No upcoming reservations."); return; }

        String today = LocalDate.now().format(DATE_FMT);
        long count = orders.stream()
            .filter(o -> ("PENDING".equals(o.getStatus()) || "CONFIRMED".equals(o.getStatus()))
                && o.getVisitDate() != null
                && o.getVisitDate().compareTo(today) >= 0)
            .count();

        lblUpcoming.setText(count == 0
            ? "No upcoming reservations."
            : "You have " + count + " upcoming reservation" + (count == 1 ? "" : "s") + ".");
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
        updateUpcomingCount();
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

        Park park = showParkExitPicker(parks);
        if (park == null) return;   // visitor cancelled

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
    }

    /**
     * shows a GoNature-themed park picker for registering an exit. unlike the plain
     * {@code ChoiceDialog} it replaces, the content sits on an elevated card (drop shadow +
     * green accent border), uses a styled park selector with location pins, themed buttons,
     * and honours the current light/dark theme.
     *
     * @param parks the non-empty list of parks the visitor can choose from
     * @return the selected park, or {@code null} if the visitor cancelled
     */
    private Park showParkExitPicker(List<Park> parks) {
        Dialog<Park> dlg = new Dialog<>();
        dlg.setTitle("Register My Exit");
        dlg.initModality(Modality.WINDOW_MODAL);
        if (lblWelcome.getScene() != null)
            dlg.initOwner(lblWelcome.getScene().getWindow());

        DialogPane pane = dlg.getDialogPane();
        pane.getStylesheets().add(getClass().getResource("/gui/gonature.css").toExternalForm());
        pane.setPrefWidth(400);
        // keep the picker consistent with the app's current light/dark theme
        pane.sceneProperty().addListener((o, ov, sc) -> { if (sc != null) ThemeManager.register(sc); });

        ButtonType confirmType = new ButtonType("Register Exit", ButtonBar.ButtonData.OK_DONE);
        pane.getButtonTypes().addAll(confirmType, ButtonType.CANCEL);

        // ── elevated content card ──────────────────────────────────────────────
        Label title = new Label("🚗  Leaving a Park");
        title.getStyleClass().add("card-title");

        Label hint = new Label("Select the park you're leaving and we'll log your exit time.");
        hint.getStyleClass().add("screen-subtitle");
        hint.setWrapText(true);

        Label pickLbl = new Label("PARK");
        pickLbl.getStyleClass().add("section-label");

        ComboBox<Park> combo = new ComboBox<>();
        combo.getItems().setAll(parks);
        combo.getSelectionModel().selectFirst();
        combo.setMaxWidth(Double.MAX_VALUE);
        combo.setPromptText("Choose a park…");
        combo.setCellFactory(lv -> new ListCell<>() {
            @Override protected void updateItem(Park p, boolean empty) {
                super.updateItem(p, empty);
                setText(empty || p == null ? null : "📍  " + p.getName());
            }
        });
        combo.setButtonCell(new ListCell<>() {
            @Override protected void updateItem(Park p, boolean empty) {
                super.updateItem(p, empty);
                setText(empty || p == null ? null : "📍  " + p.getName());
            }
        });

        VBox card = new VBox(12, title, new Separator(), hint, pickLbl, combo);
        card.getStyleClass().add("card");          // .card supplies the drop-shadow elevation
        card.setFillWidth(true);

        // a little breathing room around the card so its shadow is visible against the pane
        VBox wrap = new VBox(card);
        wrap.setPadding(new Insets(8, 6, 4, 6));
        pane.setContent(wrap);

        // ── themed buttons ─────────────────────────────────────────────────────
        Node ok = pane.lookupButton(confirmType);
        ok.getStyleClass().add("btn-primary");
        Node cancel = pane.lookupButton(ButtonType.CANCEL);
        cancel.getStyleClass().add("btn-secondary");

        ok.setDisable(combo.getValue() == null);
        combo.valueProperty().addListener((o, a, b) -> ok.setDisable(b == null));

        dlg.setResultConverter(bt -> bt == confirmType ? combo.getValue() : null);
        return dlg.showAndWait().orElse(null);
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
        stopSessionTimer();   // stop before clearing the session so the clock doesn't tick on a null user
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
        Scene scene = new Scene(root);
        ThemeManager.register(scene);   // apply the current light/dark theme to this screen
        primaryStage.setScene(scene);
        primaryStage.show();
        Animations.introduce(root);     // subtle fade-and-rise on load
    }
}
