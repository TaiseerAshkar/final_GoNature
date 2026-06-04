package gui;

import client.ChatClient;
import client.ClientUI;
import logic.Message;
import logic.Order;
import logic.OrderDetail;
import logic.UserSession;
import javafx.application.Platform;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.stage.Modality;
import javafx.stage.Stage;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Arrays;

/**
 * controller for the academic/admin screen ({@code AcademicFrame.fxml}).
 *
 * <p>used exclusively by DEPARTMENT_MANAGER. loads live orders via {@code GET_LIVE_ORDERS}
 * and lets the user update visit date or visitor count for any order by ID.
 */
public class AcademicFrameController {

    @FXML private Label lblUserName;
    @FXML private Label lblUserRole;

    // ── live-orders table ─────────────────────────────────────────────────────
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

    // ── update-order form ─────────────────────────────────────────────────────
    @FXML private TextField        txtOrderNum;
    @FXML private TextField        txtNewDate;
    @FXML private ComboBox<String> cboNewOrderType;
    @FXML private TextField        txtNewVisitors;
    @FXML private Label            lblResult;

    /**
     * populates header labels from {@link UserSession}, wires column cell-value factories,
     * sets up the visit-type dropdown, and installs a table-row selection listener that
     * pre-fills the update form when a row is clicked.
     */
    @FXML
    public void initialize() {
        UserSession session = UserSession.getInstance();
        if (session != null && session.getRole() != null) {
            lblUserName.setText(session.getFullName());
            lblUserRole.setText(session.getRole());
        }

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
        colBookedOn.setCellValueFactory(d ->
            new SimpleStringProperty(d.getValue().getCreatedAt()));

        // Visit-type dropdown — SOLO locks visitors to 1; FAMILY/GROUP unlock it.
        cboNewOrderType.getItems().addAll("SOLO", "FAMILY", "GROUP");
        cboNewOrderType.setValue("SOLO");
        applyTypeSideEffects("SOLO");
        cboNewOrderType.valueProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null) applyTypeSideEffects(newVal);
        });

        // Clicking a table row pre-fills the update form.
        tableOrders.getSelectionModel().selectedItemProperty().addListener(
            (obs, oldSel, sel) -> {
                if (sel == null) return;
                txtOrderNum.setText(String.valueOf(sel.getId()));
                txtNewDate.setText(sel.getVisitDate());
                txtNewVisitors.setText(String.valueOf(sel.getNumVisitors()));
                String type = sel.getOrderType();
                if (Arrays.asList("SOLO", "FAMILY", "GROUP").contains(type))
                    cboNewOrderType.setValue(type);
                else
                    cboNewOrderType.setValue("SOLO");
                lblResult.setText("");
            });
    }

    /** Locks/unlocks the visitors field and resets its value when the type changes. */
    private void applyTypeSideEffects(String type) {
        if ("SOLO".equals(type)) {
            txtNewVisitors.setText("1");
            txtNewVisitors.setDisable(true);
            txtNewVisitors.setStyle("-fx-background-color:#f5f5f5; -fx-background-radius:8;");
        } else {
            txtNewVisitors.setDisable(false);
            txtNewVisitors.setStyle("");
            // Reset to minimum valid value when switching away from SOLO
            if ("1".equals(txtNewVisitors.getText().trim()))
                txtNewVisitors.setText("2");
        }
    }

    /**
     * loads all live orders from the server and populates the table, newest first.
     *
     * @param event the button-click event
     */
    public void loadOrders(ActionEvent event) {
        lblResult.setText("Loading…");
        ChatClient.lastLiveOrders = null;
        ClientUI.chat.accept(new Message("GET_LIVE_ORDERS", null));
        List<OrderDetail> orders = ChatClient.lastLiveOrders;
        if (orders != null) {
            tableOrders.getItems().setAll(orders);
            lblResult.setText("Loaded " + orders.size() + " orders.");
        } else {
            tableOrders.getItems().clear();
            lblResult.setText("Could not load orders — is the server running?");
        }
    }

    /**
     * validates and sends an {@code UPDATE_ORDER} request.
     * <ul>
     *   <li>SOLO  — visitor count forced to 1.</li>
     *   <li>FAMILY — visitor count must be 2–15.</li>
     *   <li>GROUP  — visitor count must be 2–15; server additionally checks that
     *               the order's visitor_id is an approved guide.</li>
     * </ul>
     *
     * @param event the button-click event
     */
    public void updateOrder(ActionEvent event) {
        try {
            // ── Order number ─────────────────────────────────────────────────
            String orderNumText = txtOrderNum.getText().trim();
            if (orderNumText.isEmpty()) {
                lblResult.setText("Please enter an order number.");
                return;
            }
            int num = Integer.parseInt(orderNumText);

            // ── Date ─────────────────────────────────────────────────────────
            String date = txtNewDate.getText().trim();
            if (date.isEmpty()) { lblResult.setText("Please enter a new date."); return; }
            try {
                LocalDate newDate = LocalDate.parse(date, DateTimeFormatter.ofPattern("yyyy-MM-dd"));
                if (!newDate.isAfter(LocalDate.now())) {
                    lblResult.setText("Date must be a future date (use YYYY-MM-DD).");
                    return;
                }
            } catch (DateTimeParseException e) {
                lblResult.setText("Invalid date format — please use YYYY-MM-DD.");
                return;
            }

            // ── Visit type ───────────────────────────────────────────────────
            String orderType = cboNewOrderType.getValue();
            if (orderType == null) {
                lblResult.setText("Please select a visit type.");
                return;
            }

            // ── Visitor count (type-specific rules) ──────────────────────────
            String visText = txtNewVisitors.getText().trim();
            if (visText.isEmpty()) {
                lblResult.setText("Please enter the number of visitors.");
                return;
            }
            int vis = Integer.parseInt(visText);

            switch (orderType) {
                case "SOLO":
                    if (vis != 1) {
                        lblResult.setText("SOLO orders must have exactly 1 visitor.");
                        return;
                    }
                    break;
                case "FAMILY":
                    if (vis < 2 || vis > 15) {
                        lblResult.setText("FAMILY orders require 2–15 visitors.");
                        return;
                    }
                    break;
                case "GROUP":
                    if (vis < 2 || vis > 15) {
                        lblResult.setText("GROUP orders require 2–15 visitors " +
                                          "(the guide enters free at the gate).");
                        return;
                    }
                    // Guide eligibility is validated server-side; we rely on the
                    // server's error message to explain why it was rejected.
                    break;
                default:
                    lblResult.setText("Unknown visit type selected.");
                    return;
            }

            Order o = new Order(num, date, vis, 0, 0, "", orderType);
            ClientUI.chat.accept(new Message("UPDATE_ORDER", o));
            lblResult.setText(ChatClient.lastResult != null ? ChatClient.lastResult : "No response from server.");

        } catch (NumberFormatException e) {
            lblResult.setText("Please enter valid numbers for Order ID and Visitor Count.");
        }
    }

    /**
     * shows the department-manager help dialog.
     *
     * @param event the button-click event
     */
    public void handleHelp(ActionEvent event) {
        UserSession s = UserSession.getInstance();
        String content =
            "DEPARTMENT MANAGER — QUICK GUIDE\n\n" +
            "MANAGING ORDERS\n" +
            "  • Click 'Load All Orders' to view all live bookings.\n" +
            "  • Columns: Order #, Park, Visitor ID, Date, Time, Visitors, Type, Status, Booked On.\n" +
            "  • To update: click a row to pre-fill the form, or enter the Order Number manually.\n" +
            "    Set a new Date (YYYY-MM-DD), choose a Visit Type, and adjust visitor count:\n" +
            "      SOLO   → exactly 1 visitor.\n" +
            "      FAMILY → 2–15 visitors.\n" +
            "      GROUP  → 2–15 visitors; only allowed when the visitor is an approved guide.\n" +
            "    Then click 'Update Order'.\n\n" +
            "REPORTS DASHBOARD\n" +
            "  • Click 'View Reports Dashboard' to open the analytics panel.\n\n" +
            "  Visitor Report — bar chart of daily visitors for the last 30 days,\n" +
            "    split by Solo vs Group bookings.\n\n" +
            "  Cancellation Report — table of all CANCELLED orders.\n\n" +
            "  Usage Report — line chart of hourly park capacity utilisation.\n\n" +
            "  • Use the park filter to narrow any report to a specific park.";
        showHelp("GoNature — Help (DEPARTMENT_MANAGER)", content);
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
        ta.setPrefRowCount(18);
        ta.setPrefWidth(480);
        ta.setStyle("-fx-font-family: 'Segoe UI', Arial, sans-serif; -fx-font-size: 12px;");
        alert.getDialogPane().setContent(ta);
        alert.getDialogPane().setMinWidth(520);
        alert.showAndWait();
    }

    /**
     * opens the Reports Dashboard as a modal (no park lock for dept. managers).
     *
     * @param event the button-click event
     */
    public void handleViewReports(ActionEvent event) {
        try {
            Stage reportsStage = new Stage();
            reportsStage.initModality(Modality.WINDOW_MODAL);
            reportsStage.initOwner(lblUserName.getScene().getWindow());
            new ReportsController().start(reportsStage);
        } catch (Exception e) {
            lblResult.setText("Error opening reports: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * notifies the server, clears the session, and returns to the login screen.
     *
     * @param event the button-click event
     */
    @FXML
    public void handleLogout(ActionEvent event) {
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

    /** @param event the button-click event */
    public void getExitBtn(ActionEvent event) {
        if (ClientUI.chat != null) ClientUI.chat.disconnect();
        Platform.exit();
    }

    /**
     * @param primaryStage the stage in which to show the screen
     * @throws Exception if the FXML file cannot be loaded
     */
    public void start(Stage primaryStage) throws Exception {
        Parent root = FXMLLoader.load(getClass().getResource("/gui/AcademicFrame.fxml"));
        Scene scene = new Scene(root);
        primaryStage.setTitle("GoNature — Management Dashboard");
        primaryStage.setScene(scene);
        primaryStage.show();
    }
}
