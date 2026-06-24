package gui;

import client.ChatClient;
import client.ClientUI;
import client.NotificationCenter;
import logic.Message;
import logic.OrderDetail;
import client.UserSession;
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

import java.util.ArrayList;
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
     * when {@code true} the screen is opened from the "Cancel a Reservation" tile: the table
     * shows only cancellable (PENDING / CONFIRMED) bookings and a banner prompts the visitor to
     * pick one to cancel. set by {@link #start(Stage, boolean)} via {@link #enterCancelMode()}.
     */
    private boolean cancelMode = false;

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
            txtVisitorId.setStyle("-fx-background-color: -park-surface-2; -fx-background-radius: 4; -fx-padding: 5;");
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

        tableOrders.setPlaceholder(EmptyState.of(Icons.calendar(34),
            "No reservations yet",
            "Load your orders above, or book a new park visit."));

        // default (My Reservations) is view-only: the only action is "Show QR Code".
        // cancelling lives on the dedicated "Cancel a Reservation" tile (cancel mode), and
        // confirming lives on the dashboard's own "Confirm My Visit" tile.
        applyModeButtons();

        // QR is available only for active (PENDING/CONFIRMED) bookings; cancel/leave is enabled
        // for active bookings and still-waiting waiting-list entries (only relevant in cancel mode)
        tableOrders.getSelectionModel().selectedItemProperty().addListener(
            (obs, oldSel, newSel) -> {
                boolean active = newSel != null
                    && ("PENDING".equals(newSel.getStatus())
                        || "CONFIRMED".equals(newSel.getStatus()));
                boolean waiting = newSel != null && "WAITING".equals(newSel.getStatus());
                btnShowQR.setDisable(!active);
                btnCancel.setDisable(!(active || waiting));
                // leaving the waiting list and cancelling a booking are different actions,
                // so the one cancel-mode button relabels itself to match the selected row
                btnCancel.setText(waiting ? "👋  Leave Waiting List" : "❌  Cancel Selected Order");
                if (!cancelMode && newSel != null && !active)
                    lblMessage.setText("QR codes are available for PENDING or CONFIRMED reservations only.");
                else if (!cancelMode)
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
        // in cancel mode only show things that can actually be cancelled/left: active
        // (PENDING/CONFIRMED) bookings and still-waiting waiting-list entries. completed and
        // already-cancelled history is hidden so the visitor isn't distracted.
        if (cancelMode) {
            List<OrderDetail> cancellable = new ArrayList<>();
            for (OrderDetail o : orders) {
                if ("PENDING".equals(o.getStatus()) || "CONFIRMED".equals(o.getStatus())
                        || "WAITING".equals(o.getStatus()))
                    cancellable.add(o);
            }
            orders = cancellable;
        }

        tableOrders.getItems().addAll(orders);
        int count = orders.size();
        if (cancelMode) {
            lblOrderCount.setText(count == 0 ? "No upcoming reservations to cancel." :
                count + " cancellable reservation" + (count == 1 ? "" : "s") + ".");
            // pre-select the first one so "Cancel Selected Order" is immediately usable
            if (count > 0) tableOrders.getSelectionModel().selectFirst();
        } else {
            lblOrderCount.setText(count == 0 ? "No orders found." :
                count + " order" + (count == 1 ? "" : "s") + " found.");
        }
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

        // a WAITING row is a waiting-list entry, not an order: leaving the queue is a
        // different server action (LEAVE_WAITLIST) and a different confirmation message.
        if ("WAITING".equals(selected.getStatus())) {
            handleLeaveWaitlist(selected);
            return;
        }

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
        ThemeManager.styleDialog(confirm);
        // destructive confirmation reads rust, so it can't be mistaken for the safe choice
        javafx.scene.Node yesNode = confirm.getDialogPane().lookupButton(yesBtn);
        if (yesNode != null) yesNode.getStyleClass().add("btn-dialog-danger");
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
            Toast.success("Order #" + selected.getId() + " cancelled.");
        } else {
            Toast.error(ChatClient.lastCancelError != null
                ? ChatClient.lastCancelError
                : "Could not cancel this order. Please try again.");
        }
    }

    /**
     * confirms with the user, then removes a still-waiting waiting-list entry ("leave the
     * queue"). unlike cancelling a real order this frees no slot, so no waiting-list
     * promotion is triggered server-side. reloads the table on success.
     *
     * @param selected the WAITING entry the visitor chose to leave
     */
    private void handleLeaveWaitlist(OrderDetail selected) {
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("Leave Waiting List");
        confirm.setHeaderText("Leave the waiting list for " + selected.getParkName() + "?");
        confirm.setContentText(
            "Park: " + selected.getParkName() + "\n" +
            "Date: " + selected.getVisitDate() + " at " + selected.getVisitTime() + "\n" +
            "Visitors: " + selected.getNumVisitors() + "\n\n" +
            "You'll give up your place in line. If a spot opens later you'd have to join again.");
        ButtonType yesBtn = new ButtonType("Yes, Leave Waiting List", ButtonBar.ButtonData.YES);
        ButtonType noBtn  = new ButtonType("Stay in Line",            ButtonBar.ButtonData.NO);
        confirm.getButtonTypes().setAll(yesBtn, noBtn);
        ThemeManager.styleDialog(confirm);
        // leaving is a destructive choice, so style it rust like the cancel confirmation
        javafx.scene.Node yesNode = confirm.getDialogPane().lookupButton(yesBtn);
        if (yesNode != null) yesNode.getStyleClass().add("btn-dialog-danger");
        Optional<ButtonType> response = confirm.showAndWait();
        if (!response.isPresent() || response.get() != yesBtn) return;

        String visitorId = txtVisitorId.getText().trim();
        ChatClient.lastWaitlistLeft       = null;
        ChatClient.lastWaitlistLeaveError = null;
        ClientUI.chat.accept(new Message("LEAVE_WAITLIST",
            new Object[] { selected.getId(), visitorId }));

        if (Boolean.TRUE.equals(ChatClient.lastWaitlistLeft)) {
            lblMessage.setText("");
            NotificationCenter.add("👋",
                "You left the waiting list for " + selected.getParkName()
                + " · " + selected.getVisitDate() + " at " + selected.getVisitTime() + ".");
            loadOrders(visitorId);
            Toast.success("You've left the waiting list for " + selected.getParkName() + ".");
        } else {
            Toast.error(ChatClient.lastWaitlistLeaveError != null
                ? ChatClient.lastWaitlistLeaveError
                : "Could not leave the waiting list. Please try again.");
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
            NotificationCenter.markVisitConfirmed(selected.getId());
            NotificationCenter.add("✅",
                "Visit confirmed — Order #" + selected.getId()
                + " at " + selected.getParkName()
                + " on " + selected.getVisitDate()
                + " at " + selected.getVisitTime()
                + ". You're all set — see you there!");
            // reload so the status chip refreshes from PENDING to CONFIRMED
            loadOrders(txtVisitorId.getText().trim());
            Toast.success("Visit confirmed! Order #" + selected.getId()
                + " is now confirmed.");
            btnConfirmVisit.setDisable(true);
        } else {
            Toast.error(ChatClient.lastConfirmVisitError != null
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
     * switches the already-loaded screen into cancel-focused mode: filters the table to only
     * cancellable bookings, shows a guiding banner, and pre-selects the first row.
     * called by {@link #start(Stage, boolean)} after the FXML controller has initialised.
     */
    private void enterCancelMode() {
        cancelMode = true;
        applyModeButtons();
        lblMessage.setText("Select a reservation below and click \"Cancel Selected Order\".");
        // re-run the load so the cancel-mode filter and pre-selection are applied
        if (txtVisitorId.getText() != null && !txtVisitorId.getText().trim().isEmpty())
            loadOrders(txtVisitorId.getText().trim());
    }

    /**
     * shows exactly the one action button that matches the current mode and hides the rest:
     * view-only "My Reservations" exposes only "Show QR Code"; cancel mode exposes only
     * "Cancel Selected Order". "Confirm My Visit" is never shown here — it has its own tile
     * on the visitor dashboard.
     */
    private void applyModeButtons() {
        btnShowQR.setVisible(!cancelMode);
        btnShowQR.setManaged(!cancelMode);
        btnCancel.setVisible(cancelMode);
        btnCancel.setManaged(cancelMode);
        btnConfirmVisit.setVisible(false);
        btnConfirmVisit.setManaged(false);
    }

    /**
     * opens the screen in normal "My Reservations" mode.
     *
     * @param stage the stage in which to show the screen
     * @throws Exception if the FXML file cannot be loaded
     */
    public void start(Stage stage) throws Exception {
        start(stage, false);
    }

    /**
     * opens the screen, optionally in cancel-focused mode (used by the "Cancel a Reservation"
     * tile so it does something distinct from "My Reservations").
     *
     * @param stage      the stage in which to show the screen
     * @param cancelMode {@code true} to open the cancel-focused view
     * @throws Exception if the FXML file cannot be loaded
     */
    public void start(Stage stage, boolean cancelMode) throws Exception {
        FXMLLoader loader = new FXMLLoader(getClass().getResource("/gui/MyOrdersFrame.fxml"));
        Parent root = loader.load();
        MyOrdersController controller = loader.getController();
        stage.setTitle(cancelMode ? "GoNature — Cancel a Reservation" : "GoNature — My Orders");
        Scene scene = new Scene(root);
        ThemeManager.register(scene);
        stage.setScene(scene);
        stage.centerOnScreen();
        if (cancelMode) controller.enterCancelMode();
        stage.show();
    }
}

