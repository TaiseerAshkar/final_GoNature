package gui;

import client.ChatClient;
import client.ClientUI;
import logic.Message;
import logic.Order;
import logic.OrderDetail;
import logic.RegisterGuideRequest;
import logic.RegisterRequest;
import logic.RegisterResult;
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
 * customer-facing tasks: loading and updating orders on a visitor's behalf, and
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
            // Server returned an error; show it inline and let the rep correct
            lblErr.setText(ChatClient.lastGuideRegisterFail != null
                ? ChatClient.lastGuideRegisterFail : "Registration failed. Try again.");
        }
    }

    /**
     * opens a modal dialog for registering a new Family Member Club subscriber.
     *
     * <p>per the GoNature spec, subscriber registration is performed by a service representative
     * (not by the visitor themselves). this captures first name, last name, ID number, mobile
     * phone, email, family size, and an OPTIONAL credit card (the customer may choose to pay
     * cash, in which case the field is left blank). it sends {@code REGISTER_SUBSCRIBER}; the
     * server inserts the visitor + subscriber rows and returns the generated subscriber ID.
     *
     * @param event the button-click event
     */
    @FXML
    public void handleRegisterSubscriber(ActionEvent event) {
        Dialog<ButtonType> dlg = new Dialog<>();
        dlg.setTitle("Register New Subscriber");
        dlg.setHeaderText("Register a Family Member Club subscriber\n(a unique subscriber ID is generated on success)");
        dlg.initModality(Modality.WINDOW_MODAL);
        dlg.initOwner(lblUserName.getScene().getWindow());

        ButtonType submitBtn = new ButtonType("Register", ButtonBar.ButtonData.OK_DONE);
        dlg.getDialogPane().getButtonTypes().addAll(submitBtn, ButtonType.CANCEL);

        GridPane grid = new GridPane();
        grid.setHgap(12);
        grid.setVgap(12);
        grid.setPadding(new Insets(20, 24, 12, 24));

        TextField fldId         = new TextField(); fldId.setPromptText("9-digit government ID");
        TextField fldFirstName  = new TextField(); fldFirstName.setPromptText("First name");
        TextField fldLastName   = new TextField(); fldLastName.setPromptText("Last name");
        TextField fldEmail      = new TextField(); fldEmail.setPromptText("name@example.com");
        TextField fldPhone      = new TextField(); fldPhone.setPromptText("10 digits");
        TextField fldFamilySize = new TextField(); fldFamilySize.setPromptText("Family members (1–10)");
        TextField fldCreditCard = new TextField(); fldCreditCard.setPromptText("16 digits — optional (blank = cash)");
        for (TextField f : new TextField[]{ fldId, fldFirstName, fldLastName, fldEmail,
                                            fldPhone, fldFamilySize, fldCreditCard }) {
            f.getStyleClass().add("text-field");
            f.setPrefWidth(250);
        }

        grid.add(fieldLabel("ID Number *"),   0, 0); grid.add(fldId,         1, 0);
        grid.add(fieldLabel("First Name *"),  0, 1); grid.add(fldFirstName,  1, 1);
        grid.add(fieldLabel("Last Name *"),   0, 2); grid.add(fldLastName,   1, 2);
        grid.add(fieldLabel("Email *"),       0, 3); grid.add(fldEmail,      1, 3);
        grid.add(fieldLabel("Mobile Phone *"),0, 4); grid.add(fldPhone,      1, 4);
        grid.add(fieldLabel("Family Size *"), 0, 5); grid.add(fldFamilySize, 1, 5);
        grid.add(fieldLabel("Credit Card"),   0, 6); grid.add(fldCreditCard, 1, 6);

        Label lblErr = new Label("");
        lblErr.getStyleClass().add("lbl-error");
        lblErr.setWrapText(true);
        grid.add(lblErr, 0, 7, 2, 1);

        DialogPane pane = dlg.getDialogPane();
        pane.setContent(grid);
        pane.setPrefWidth(460);
        ThemeManager.styleDialog(dlg);

        // Keep Register disabled until all required fields are non-empty (credit card stays optional).
        javafx.scene.Node okBtn = pane.lookupButton(submitBtn);
        okBtn.setDisable(true);
        Runnable checkRequired = () -> okBtn.setDisable(
            fldId.getText().trim().isEmpty()         ||
            fldFirstName.getText().trim().isEmpty()  ||
            fldLastName.getText().trim().isEmpty()   ||
            fldEmail.getText().trim().isEmpty()      ||
            fldPhone.getText().trim().isEmpty()      ||
            fldFamilySize.getText().trim().isEmpty());
        for (TextField f : new TextField[]{ fldId, fldFirstName, fldLastName,
                                            fldEmail, fldPhone, fldFamilySize }) {
            f.textProperty().addListener((o, ov, nv) -> checkRequired.run());
        }

        // Signals from inside the filter to the post-showAndWait code.
        boolean[] succeeded = { false };
        String[]  successMsg = { "" };

        // Intercept the OK button so validation failures keep the dialog open (no close/reopen flash).
        okBtn.addEventFilter(javafx.event.ActionEvent.ACTION, ev -> {
            lblErr.setText("");

            String id    = fldId.getText().trim();
            String first = UserSession.capitalize(fldFirstName.getText().trim());
            String last  = UserSession.capitalize(fldLastName.getText().trim());
            String email = fldEmail.getText().trim();
            String phone = fldPhone.getText().trim();
            String fsStr = fldFamilySize.getText().trim();
            String cc    = fldCreditCard.getText().trim();

            // ID — exactly 9 digits (matches RegisterController)
            if (!id.matches("\\d+")) {
                lblErr.setText("ID number must contain digits only."); ev.consume(); return;
            }
            if (id.length() != 9) {
                lblErr.setText("ID number must be exactly 9 digits."); ev.consume(); return;
            }

            // Names — letters, spaces, hyphens and apostrophes only (matches RegisterController)
            if (!first.matches("[\\p{L} '-]+")) {
                lblErr.setText("First name must contain letters only."); ev.consume(); return;
            }
            if (first.length() > 50) {
                lblErr.setText("First name must be 50 characters or fewer."); ev.consume(); return;
            }
            if (!last.matches("[\\p{L} '-]+")) {
                lblErr.setText("Last name must contain letters only."); ev.consume(); return;
            }
            if (last.length() > 50) {
                lblErr.setText("Last name must be 50 characters or fewer."); ev.consume(); return;
            }

            // Email
            if (!email.matches("^[\\w._%+\\-]+@[\\w.\\-]+\\.[a-zA-Z]{2,}$")) {
                lblErr.setText("Please enter a valid email address."); ev.consume(); return;
            }

            // Phone — exactly 10 digits (matches RegisterController)
            if (!phone.matches("\\d+")) {
                lblErr.setText("Mobile phone must contain digits only."); ev.consume(); return;
            }
            if (phone.length() != 10) {
                lblErr.setText("Mobile phone must be exactly 10 digits."); ev.consume(); return;
            }

            // Family size
            int familySize;
            try {
                familySize = Integer.parseInt(fsStr);
            } catch (NumberFormatException ex) {
                lblErr.setText("Family size must be a whole number."); ev.consume(); return;
            }
            if (familySize < 1 || familySize > 10) {
                lblErr.setText("Family size must be between 1 and 10."); ev.consume(); return;
            }

            // Credit card — optional; if filled must be exactly 16 digits
            String creditCard = null;
            if (!cc.isEmpty()) {
                if (!cc.matches("\\d{16}")) {
                    lblErr.setText("Credit card must be exactly 16 digits (or leave it blank for cash).");
                    ev.consume(); return;
                }
                creditCard = cc;
            }

            // All client-side checks passed — send to server
            ChatClient.lastRegisterResult = null;
            ChatClient.lastRegisterError  = null;
            ClientUI.chat.accept(new Message("REGISTER_SUBSCRIBER",
                new RegisterRequest(id, first, last, email, phone, true, creditCard, familySize)));

            RegisterResult res = ChatClient.lastRegisterResult;
            if (res != null && res.isSuccess()) {
                String sub = (res.getSubscriberId() != null)
                    ? "  Subscriber ID: " + res.getSubscriberId() : "";
                succeeded[0]  = true;
                successMsg[0] = "Subscriber registered." + sub;
                // don't consume — let dialog close normally
            } else {
                lblErr.setText(ChatClient.lastRegisterError != null
                    ? ChatClient.lastRegisterError : "Registration failed. Try again.");
                ev.consume(); // keep dialog open so the rep can correct
            }
        });

        Platform.runLater(fldId::requestFocus);
        dlg.showAndWait();

        if (succeeded[0]) Toast.success(successMsg[0]);
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
     * date must be a future {@code YYYY-MM-DD}; visitors must be 1-15.
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
            "REGISTERING A GUIDE\n" +
            "  • Click 'Register New Guide' to register an approved tour guide.\n" +
            "  • The guide's ID is then authorised to make GROUP bookings.\n\n" +
            "REGISTERING A SUBSCRIBER\n" +
            "  • Click '⭐ Register Subscriber' to open the Family Member Club form.\n" +
            "  • Enter the visitor's name, government ID, phone, email, family size,\n" +
            "    and optionally a credit card number (leave blank for cash payment).\n" +
            "  • A subscriber ID is issued immediately on successful registration.\n" +
            "  • Subscribers receive a 10% discount on top of any applicable booking discount.\n\n" +
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
