package gui;

import client.ChatClient;
import client.ClientUI;
import client.NotificationCenter;
import logic.Message;
import logic.OrderDetail;
import logic.UserSession;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * controller for the visitor's My Orders screen ({@code MyOrdersFrame.fxml}).
 *
 * <p>shows all orders for the logged-in visitor with colour-coded status cells.
 * the cancel button is only enabled for PENDING or CONFIRMED orders.
 * after cancellation, shows a popup if a waiting-list visitor was notified.
 */
public class MyOrdersController {

    @FXML private TextField                         txtVisitorId;
    @FXML private TableView<OrderDetail>            tableOrders;
    @FXML private TableColumn<OrderDetail, Integer> colId;
    @FXML private TableColumn<OrderDetail, String>  colPark;
    @FXML private TableColumn<OrderDetail, String>  colDate;
    @FXML private TableColumn<OrderDetail, String>  colTime;
    @FXML private TableColumn<OrderDetail, Integer> colVisitors;
    @FXML private TableColumn<OrderDetail, String>  colType;
    @FXML private TableColumn<OrderDetail, String>  colStatus;
    @FXML private TableColumn<OrderDetail, String>  colCreated;
    @FXML private Button                            btnTheme;
    @FXML private Button                            btnCancel;
    @FXML private Button                            btnShowQR;
    @FXML private Button                            btnConfirmVisit;
    @FXML private Label                             lblMessage;
    @FXML private Label                             lblOrderCount;

    // cancellation confirmation card
    @FXML private VBox                              paneCancelConfirm;
    @FXML private Label                             lblCancelTitle;
    @FXML private Label                             lblCancelDetail;

    /**
     * pre-fills the visitor ID (read-only when logged in), wires column factories,
     * installs colour-coded status cells, and auto-loads orders for the current session.
     */
    @FXML
    public void initialize() {
        ThemeManager.installToggle(btnTheme);
        UserSession s = UserSession.getInstance();
        if (s != null && s.getUserId() != null && !s.getUserId().isEmpty()) {
            txtVisitorId.setText(s.getUserId());
            txtVisitorId.setEditable(false);
            txtVisitorId.setStyle("-fx-background-color: #f5f5f5; -fx-background-radius: 4; -fx-padding: 5;");
        }

        colId.setCellValueFactory(cell ->
            new SimpleIntegerProperty(cell.getValue().getId()).asObject());
        colPark.setCellValueFactory(cell ->
            new SimpleStringProperty(cell.getValue().getParkName()));
        colDate.setCellValueFactory(cell ->
            new SimpleStringProperty(cell.getValue().getVisitDate()));
        colTime.setCellValueFactory(cell ->
            new SimpleStringProperty(cell.getValue().getVisitTime()));
        colVisitors.setCellValueFactory(cell ->
            new SimpleIntegerProperty(cell.getValue().getNumVisitors()).asObject());
        colType.setCellValueFactory(cell ->
            new SimpleStringProperty(cell.getValue().getOrderType()));
        colCreated.setCellValueFactory(cell ->
            new SimpleStringProperty(cell.getValue().getCreatedAt()));

        // render status as a colour-coded chip so the user can scan status at a glance
        colStatus.setCellValueFactory(cell ->
            new SimpleStringProperty(cell.getValue().getStatus()));
        UiCells.applyStatusChip(colStatus);

        // only active orders can be cancelled or shown as QR;
        // only tomorrow's active orders can be confirmed
        tableOrders.getSelectionModel().selectedItemProperty().addListener(
            (obs, oldSel, newSel) -> {
                boolean active = newSel != null
                    && ("PENDING".equals(newSel.getStatus())
                        || "CONFIRMED".equals(newSel.getStatus()));
                boolean confirmable = active
                    && newSel.getVisitDate() != null
                    && newSel.getVisitDate().equals(
                        LocalDate.now().plusDays(1).toString());
                btnCancel.setDisable(!active);
                btnShowQR.setDisable(!active);
                btnConfirmVisit.setDisable(!confirmable);
                if (newSel != null && !active)
                    lblMessage.setText("QR codes and cancellation are available for PENDING or CONFIRMED orders only.");
                else
                    lblMessage.setText("");
            });

        if (txtVisitorId.getText() != null && !txtVisitorId.getText().isEmpty())
            loadOrders(txtVisitorId.getText().trim());
    }

    /**
     * reloads orders for the visitor ID currently in the text field.
     *
     * @param event the button-click event
     */
    @FXML
    public void handleRefresh(ActionEvent event) {
        lblMessage.setText("");
        String visitorId = txtVisitorId.getText().trim();
        if (visitorId.isEmpty()) { lblMessage.setText("Please enter a Visitor ID."); return; }
        loadOrders(visitorId);
    }

    /**
     * @param visitorId the visitor ID to query orders for
     */
    private void loadOrders(String visitorId) {
        // hide the confirmation card so it doesn't bleed into the next lookup
        paneCancelConfirm.setVisible(false);
        paneCancelConfirm.setManaged(false);
        ChatClient.lastMyOrders = null;
        ClientUI.chat.accept(new Message("GET_MY_ORDERS", visitorId));
        List<OrderDetail> orders = ChatClient.lastMyOrders;
        tableOrders.getItems().clear();
        btnCancel.setDisable(true);
        if (orders == null) {
            lblOrderCount.setText("Couldn't load your reservations.");
            lblMessage.setText("The server didn't respond. Please check your connection and try again.");
            return;
        }
        tableOrders.getItems().addAll(orders);
        int count = orders.size();
        lblOrderCount.setText(count == 0 ? "No orders found." :
            count + " order" + (count == 1 ? "" : "s") + " found.");
    }

    /**
     * confirms with the user, cancels the selected order, and notifies if a waiting-list
     * spot was freed. reloads the table on success.
     *
     * @param event the button-click event
     */
    @FXML
    public void handleCancelOrder(ActionEvent event) {
        lblMessage.setText("");
        OrderDetail selected = tableOrders.getSelectionModel().getSelectedItem();
        if (selected == null) return;

        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("Cancel Order");
        confirm.setHeaderText("Cancel Order #" + selected.getId() + "?");
        confirm.setContentText(
            "Park: " + selected.getParkName() + "\n" +
            "Date: " + selected.getVisitDate() + " at " + selected.getVisitTime() + "\n" +
            "Visitors: " + selected.getNumVisitors() + "\n\n" +
            "This action cannot be undone.");
        ButtonType yesBtn = new ButtonType("Yes, Cancel Order", ButtonBar.ButtonData.YES);
        ButtonType noBtn  = new ButtonType("Keep Order",        ButtonBar.ButtonData.NO);
        confirm.getButtonTypes().setAll(yesBtn, noBtn);
        Optional<ButtonType> response = confirm.showAndWait();
        if (!response.isPresent() || response.get() != yesBtn) return;

        ChatClient.lastCancelResult = null;
        ChatClient.lastCancelError  = null;
        ClientUI.chat.accept(new Message("CANCEL_ORDER", selected.getId()));

        if (ChatClient.lastCancelResult != null && ChatClient.lastCancelResult.isSuccess()) {
            lblMessage.setText("");

            // show the inline confirmation card
            lblCancelTitle.setText("Order #" + selected.getId() + " cancelled successfully.");
            lblCancelDetail.setText(
                "Park: " + selected.getParkName()
                + "   ·   Date: " + selected.getVisitDate()
                + " at " + selected.getVisitTime()
                + "   ·   Visitors: " + selected.getNumVisitors());
            paneCancelConfirm.setVisible(true);
            paneCancelConfirm.setManaged(true);

            // also log to the notification inbox
            NotificationCenter.add("🚫",
                "Order #" + selected.getId() + " cancelled — "
                + selected.getParkName() + " · " + selected.getVisitDate()
                + " at " + selected.getVisitTime() + ". "
                + "Your reserved slot remains counted; cancellation confirmation sent to your email.");

            loadOrders(txtVisitorId.getText().trim());
        } else {
            lblMessage.setStyle("-fx-text-fill: #b71c1c;");
            lblMessage.setText(ChatClient.lastCancelError != null
                ? ChatClient.lastCancelError
                : "Could not cancel this order. Please try again.");
        }
    }

    /**
     * confirms a tomorrow's booking so the server's auto-cancel skips it.
     * enabled only when the selected order is PENDING/CONFIRMED with visit date = tomorrow.
     *
     * @param event the button-click event
     */
    @FXML
    public void handleConfirmVisit(ActionEvent event) {
        lblMessage.setText("");
        OrderDetail selected = tableOrders.getSelectionModel().getSelectedItem();
        if (selected == null) return;

        ChatClient.lastConfirmVisitOk    = null;
        ChatClient.lastConfirmVisitError = null;
        ClientUI.chat.accept(new Message("CONFIRM_VISIT", selected.getId()));

        if (Boolean.TRUE.equals(ChatClient.lastConfirmVisitOk)) {
            lblMessage.setStyle("-fx-text-fill: #1b5e20; -fx-font-weight: bold;");
            lblMessage.setText("Visit confirmed! Order #" + selected.getId()
                + " will not be auto-cancelled.");
            NotificationCenter.add("✅",
                "Visit confirmed — Order #" + selected.getId()
                + " at " + selected.getParkName()
                + " on " + selected.getVisitDate()
                + " at " + selected.getVisitTime()
                + ". You're all set — see you there!");
            btnConfirmVisit.setDisable(true);
        } else {
            lblMessage.setStyle("-fx-text-fill: #c62828;");
            lblMessage.setText(ChatClient.lastConfirmVisitError != null
                ? ChatClient.lastConfirmVisitError
                : "Could not confirm visit. Please try again.");
        }
    }

    /**
     * shows the QR entry pass for the selected order.
     * the visitor presents this code to the employee at the gate.
     *
     * @param event the button-click event
     */
    @FXML
    public void handleShowQR(ActionEvent event) {
        OrderDetail selected = tableOrders.getSelectionModel().getSelectedItem();
        if (selected == null) return;
        UserSession s = UserSession.getInstance();
        String visitorId = (s != null && s.getUserId() != null)
            ? s.getUserId() : selected.getVisitorId();
        QRCodeUtil.showDialog(selected.getId(), visitorId,
            selected.getParkName(), selected.getVisitDate(), selected.getVisitTime());
    }

    /** @param event the button-click event */
    @FXML
    public void handleClose(ActionEvent event) {
        ((Stage) tableOrders.getScene().getWindow()).close();
    }

    /**
     * @param stage the stage in which to show the screen
     * @throws Exception if the FXML file cannot be loaded
     */
    public void start(Stage stage) throws Exception {
        Parent root = FXMLLoader.load(getClass().getResource("/gui/MyOrdersFrame.fxml"));
        stage.setTitle("GoNature — My Orders");
        Scene scene = new Scene(root);
        ThemeManager.register(scene);
        stage.setScene(scene);
        stage.show();
    }
}
