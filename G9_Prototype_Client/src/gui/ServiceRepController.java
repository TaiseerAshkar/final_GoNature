package gui;

import client.ChatClient;
import client.ClientUI;
import logic.Message;
import logic.Order;
import logic.OrderDetail;
import logic.RegisterGuideRequest;
import logic.UserSession;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Insets;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.GridPane;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.util.Duration;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Optional;

/**
 * controller for the service representative dashboard ({@code ServiceRepFrame.fxml}).
 *
 * <p>distinct from the department-manager view: the service rep screen centres on
 * customer-facing tasks — loading and updating orders on a visitor's behalf, and
 * registering new visitors directly from the desk.
 */
public class ServiceRepController {

    @FXML private Label lblUserName;

    // header controls
    @FXML private Label  lblSessionTimer;
    @FXML private Button btnTheme;

    /** ticks once a second to refresh the logged-in-duration label in the header. */
    private Timeline sessionTimeline;

    // live-orders table
    @FXML private TableView<OrderDetail>            tableOrders;
    @FXML private TableColumn<OrderDetail, Integer> colId;
    @FXML private TableColumn<OrderDetail, String>  colPark;
    @FXML private TableColumn<OrderDetail, String>  colVisitorId;
    @FXML private TableColumn<OrderDetail, String>  colDate;
    @FXML private TableColumn<OrderDetail, String>  colTime;
    @FXML private TableColumn<OrderDetail, Integer> colVisitors;
    @FXML private TableColumn<OrderDetail, String>  colType;
    @FXML private TableColumn<OrderDetail, String>  colStatus;
    @FXML private TableColumn<OrderDetail, String>  colBookedOn;

    // update-order form
    @FXML private TextField txtOrderNum;
    @FXML private TextField txtNewDate;
    @FXML private TextField txtNewVisitors;
    @FXML private Label     lblResult;

    /**
     * populates the header label and wires column cell-value factories.
     */
    @FXML
    public void initialize() {
        UserSession s = UserSession.getInstance();
        if (s != null) lblUserName.setText(s.getFullName());

        ThemeManager.installToggle(btnTheme);
        startSessionTimer();

        colId.setCellValueFactory(d ->
            new SimpleIntegerProperty(d.getValue().getId()).asObject());
        colPark.setCellValueFactory(d ->
            new SimpleStringProperty(d.getValue().getParkName()));
        colVisitorId.setCellValueFactory(d ->
            new SimpleStringProperty(d.getValue().getVisitorId()));
        colDate.setCellValueFactory(d ->
            new SimpleStringProperty(d.getValue().getVisitDate()));
        colTime.setCellValueFactory(d ->
            new SimpleStringProperty(d.getValue().getVisitTime()));
        colVisitors.setCellValueFactory(d ->
            new SimpleIntegerProperty(d.getValue().getNumVisitors()).asObject());
        colType.setCellValueFactory(d ->
            new SimpleStringProperty(d.getValue().getOrderType()));
        colStatus.setCellValueFactory(d ->
            new SimpleStringProperty(d.getValue().getStatus()));
        UiCells.applyStatusChip(colStatus);
        colBookedOn.setCellValueFactory(d ->
            new SimpleStringProperty(d.getValue().getCreatedAt()));

        tableOrders.setPlaceholder(EmptyState.of(Icons.calendar(34),
            "No live orders loaded",
            "Click \"Load Orders\" to fetch the current reservations."));
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
     * fetches all live orders from the server and populates the table.
     *
     * @param event the button-click event
     */
    @FXML
    public void loadOrders(ActionEvent event) {
        lblResult.setText("Loading…");
        ChatClient.lastLiveOrders = null;
        ClientUI.chat.accept(new Message("GET_LIVE_ORDERS", null));
        List<OrderDetail> orders = ChatClient.lastLiveOrders;
        if (orders != null) {
            tableOrders.getItems().setAll(orders);
            lblResult.setText("Loaded " + orders.size() + " order(s).");
        } else {
            tableOrders.getItems().clear();
            lblResult.setText("Could not load orders — is the server running?");
        }
    }

    /**
     * opens the visitor registration screen as a modal so the service rep can
     * register a new visitor directly from the desk.
     *
     * @param event the button-click event
     */
    @FXML
    public void handleRegisterVisitor(ActionEvent event) {
        try {
            Stage modal = new Stage();
            modal.initModality(Modality.WINDOW_MODAL);
            modal.initOwner(lblUserName.getScene().getWindow());
            new RegisterController().start(modal);
        } catch (Exception e) {
            lblResult.setText("Error opening registration form: " + e.getMessage());
        }
    }

    /**
     * opens a modal dialog for registering a new guide (pending manager approval).
     * validates that ID, first name, and last name are filled; all other fields optional.
     * sends {@code REGISTER_GUIDE}; server rejects with "Guide already registered" on duplicate ID.
     *
     * @param event the button-click event
     */
    @FXML
    public void handleRegisterGuide(ActionEvent event) {
        Dialog<ButtonType> dlg = new Dialog<>();
        dlg.setTitle("Register New Guide");
        dlg.setHeaderText("Register a new tour guide\n(pending department manager approval)");
        dlg.initModality(Modality.WINDOW_MODAL);
        dlg.initOwner(lblUserName.getScene().getWindow());

        ButtonType submitBtn = new ButtonType("Register", ButtonBar.ButtonData.OK_DONE);
        dlg.getDialogPane().getButtonTypes().addAll(submitBtn, ButtonType.CANCEL);

        GridPane grid = new GridPane();
        grid.setHgap(12);
        grid.setVgap(12);
        grid.setPadding(new Insets(20, 24, 12, 24));

        TextField fldId        = new TextField(); fldId.setPromptText("e.g. 123456789");
        TextField fldFirstName = new TextField(); fldFirstName.setPromptText("First name");
        TextField fldLastName  = new TextField(); fldLastName.setPromptText("Last name");
        TextField fldEmail     = new TextField(); fldEmail.setPromptText("Optional");
        TextField fldPhone     = new TextField(); fldPhone.setPromptText("Optional");
        for (TextField f : new TextField[]{ fldId, fldFirstName, fldLastName, fldEmail, fldPhone }) {
            f.getStyleClass().add("text-field");
            f.setPrefWidth(230);
        }

        grid.add(fieldLabel("ID Number *"),  0, 0); grid.add(fldId,        1, 0);
        grid.add(fieldLabel("First Name *"), 0, 1); grid.add(fldFirstName, 1, 1);
        grid.add(fieldLabel("Last Name *"),  0, 2); grid.add(fldLastName,  1, 2);
        grid.add(fieldLabel("Email"),        0, 3); grid.add(fldEmail,     1, 3);
        grid.add(fieldLabel("Phone"),        0, 4); grid.add(fldPhone,     1, 4);

        Label lblErr = new Label("");
        lblErr.getStyleClass().add("lbl-error");
        lblErr.setWrapText(true);
        grid.add(lblErr, 0, 5, 2, 1);

        DialogPane pane = dlg.getDialogPane();
        pane.setContent(grid);
        pane.setPrefWidth(430);
        // pine/gold themed header & buttons, always-light parchment, follows the active theme
        ThemeManager.styleDialog(dlg);

        // Keep Register button disabled until the three required fields are non-empty
        javafx.scene.Node okBtn = dlg.getDialogPane().lookupButton(submitBtn);
        okBtn.setDisable(true);
        Runnable checkRequired = () -> okBtn.setDisable(
            fldId.getText().trim().isEmpty() ||
            fldFirstName.getText().trim().isEmpty() ||
            fldLastName.getText().trim().isEmpty());
        fldId.textProperty().addListener((o, ov, nv)        -> checkRequired.run());
        fldFirstName.textProperty().addListener((o, ov, nv) -> checkRequired.run());
        fldLastName.textProperty().addListener((o, ov, nv)  -> checkRequired.run());

        Platform.runLater(fldId::requestFocus);

        // Loop so validation errors keep the dialog open for correction
        while (true) {
            Optional<ButtonType> result = dlg.showAndWait();
            if (!result.isPresent() || result.get() != submitBtn) return; // cancelled

            String id    = fldId.getText().trim();
            String first = UserSession.capitalize(fldFirstName.getText().trim());
            String last  = UserSession.capitalize(fldLastName.getText().trim());
            String email = fldEmail.getText().trim();
            String phone = fldPhone.getText().trim();

            if (id.isEmpty() || first.isEmpty() || last.isEmpty()) {
                lblErr.setText("ID, first name, and last name are required.");
                continue;
            }

            ChatClient.lastGuideRegisterSuccess = null;
            ChatClient.lastGuideRegisterFail    = null;
            ClientUI.chat.accept(new Message("REGISTER_GUIDE",
                new RegisterGuideRequest(id, first, last, email, phone)));

            if (ChatClient.lastGuideRegisterSuccess != null) {
                Toast.success(ChatClient.lastGuideRegisterSuccess);
                return;
            }
            // Server returned an error — show it inline and let the rep correct
            lblErr.setText(ChatClient.lastGuideRegisterFail != null
                ? ChatClient.lastGuideRegisterFail : "Registration failed. Try again.");
        }
    }

    /**
     * builds a form label styled with the topographic {@code field-label} class
     * (pine, bold) so the register-guide dialog matches the rest of the client UI.
     *
     * @param text the label caption
     * @return a styled {@link Label}
     */
    private static Label fieldLabel(String text) {
        Label l = new Label(text);
        l.getStyleClass().add("field-label");
        return l;
    }

    /**
     * validates and sends an {@code UPDATE_ORDER} request on behalf of a visitor.
     * date must be a future {@code YYYY-MM-DD}; visitors must be 1–15.
     *
     * @param event the button-click event
     */
    @FXML
    public void updateOrder(ActionEvent event) {
        try {
            String orderNumText = txtOrderNum.getText().trim();
            if (orderNumText.isEmpty()) {
                lblResult.setText("Please enter an order number.");
                return;
            }
            int num = Integer.parseInt(orderNumText);

            String date = txtNewDate.getText().trim();
            if (date.isEmpty()) { lblResult.setText("Please enter a date."); return; }
            try {
                LocalDate newDate = LocalDate.parse(date, DateTimeFormatter.ofPattern("yyyy-MM-dd"));
                if (!newDate.isAfter(LocalDate.now())) {
                    lblResult.setText("Date must be a future date.");
                    return;
                }
            } catch (DateTimeParseException e) {
                lblResult.setText("Invalid date format — use YYYY-MM-DD.");
                return;
            }

            String visText = txtNewVisitors.getText().trim();
            if (visText.isEmpty()) { lblResult.setText("Please enter number of visitors."); return; }
            int vis = Integer.parseInt(visText);
            if (vis < 1 || vis > 15) {
                lblResult.setText("Visitor count must be between 1 and 15.");
                return;
            }

            Order o = new Order(num, date, vis);
            ClientUI.chat.accept(new Message("UPDATE_ORDER", o));
            lblResult.setText(ChatClient.lastResult);

        } catch (NumberFormatException e) {
            lblResult.setText("Please enter valid numbers for order ID and visitor count.");
        }
    }

    /**
     * opens the Reports Dashboard as a modal.
     *
     * @param event the button-click event
     */
    @FXML
    public void handleViewReports(ActionEvent event) {
        try {
            Stage modal = new Stage();
            modal.initModality(Modality.WINDOW_MODAL);
            modal.initOwner(lblUserName.getScene().getWindow());
            new ReportsController().start(modal);
        } catch (Exception e) {
            lblResult.setText("Error opening reports: " + e.getMessage());
        }
    }

    /**
     * shows a quick-reference help dialog for service representatives.
     *
     * @param event the button-click event
     */
    @FXML
    public void handleHelp(ActionEvent event) {
        HelpDialog.show("GoNature — Service Representative Guide",
            "LOADING ORDERS\n" +
            "  • Click 'Load All Orders' to see every active booking in the system.\n\n" +
            "UPDATING AN ORDER\n" +
            "  • Enter the Order Number, a new future Date (YYYY-MM-DD),\n" +
            "    and a new visitor count (1–15), then click 'Update Order'.\n\n" +
            "REGISTERING A VISITOR\n" +
            "  • Click 'Register New Visitor' to open the registration form.\n" +
            "  • Fill in the visitor's details on their behalf and submit.\n" +
            "  • The visitor can then log in with their government ID.\n\n" +
            "REPORTS\n" +
            "  • Click 'View Reports Dashboard' to access visitor, cancellation,\n" +
            "    and usage reports across all parks.\n\n" +
            "ORDER STATUS REFERENCE\n" +
            "  PENDING   — booked, awaiting park confirmation\n" +
            "  CONFIRMED — confirmed by the park\n" +
            "  COMPLETED — visitor has checked in and out\n" +
            "  CANCELLED — cancelled by visitor or system");
    }

    /**
     * sends a logout message, clears the session, and returns to the login screen.
     *
     * @param event the button-click event
     */
    @FXML
    public void handleLogout(ActionEvent event) {
        stopSessionTimer();   // stop before clearing the session so the clock doesn't tick on a null user
        if (ClientUI.chat != null) ClientUI.chat.sendLogout();
        UserSession.clear();
        Stage stage = (Stage) lblUserName.getScene().getWindow();
        try {
            new LoginController().start(stage);
        } catch (Exception e) {
            if (ClientUI.chat != null) ClientUI.chat.disconnect();
            Platform.exit();
        }
    }

    /**
     * @param primaryStage the stage in which to show the dashboard
     * @throws Exception if the FXML file cannot be loaded
     */
    public void start(Stage primaryStage) throws Exception {
        Parent root = FXMLLoader.load(getClass().getResource("/gui/ServiceRepFrame.fxml"));
        primaryStage.setTitle("GoNature — Service Representative");
        Scene scene = new Scene(root);
        ThemeManager.register(scene);   // apply the current light/dark theme to this screen
        primaryStage.setScene(scene);
        primaryStage.centerOnScreen();
        primaryStage.show();
        Animations.introduce(root);     // subtle fade-and-rise on load
    }
}
