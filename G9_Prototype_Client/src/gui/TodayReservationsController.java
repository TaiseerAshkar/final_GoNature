package gui;

import client.ChatClient;
import client.ClientUI;
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
import javafx.stage.Stage;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * FXML controller for the Today's Reservations screen ({@code TodayReservationsFrame.fxml}).
 *
 * <p>sends {@code GET_TODAY_ORDERS} for the logged-in employee's park and displays
 * all bookings for today in a table.
 */
public class TodayReservationsController {

    @FXML private Label lblParkName;
    @FXML private Label lblDate;
    @FXML private Label lblCount;
    @FXML private Label lblError;
    @FXML private Button btnTheme;

    @FXML private TableView<OrderDetail>            tableOrders;
    @FXML private TableColumn<OrderDetail, Integer> colId;
    @FXML private TableColumn<OrderDetail, String>  colVisitorId;
    @FXML private TableColumn<OrderDetail, String>  colTime;
    @FXML private TableColumn<OrderDetail, Integer> colVisitors;
    @FXML private TableColumn<OrderDetail, String>  colType;
    @FXML private TableColumn<OrderDetail, String>  colStatus;
    @FXML private TableColumn<OrderDetail, String>  colEmail;

    /**
     * populates the park/date labels, wires column factories, and auto-loads today's data.
     */
    @FXML
    public void initialize() {
        ThemeManager.installToggle(btnTheme);
        UserSession s = UserSession.getInstance();
        Integer parkId = s != null ? s.getParkId() : null;
        String parkName = (s != null && s.getParkName() != null) ? s.getParkName()
                          : (parkId != null ? "Park #" + parkId : "No Park Assigned");
        lblParkName.setText(parkName);
        lblDate.setText("Today: " +
            LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd")));

        colId.setCellValueFactory(d ->
            new SimpleIntegerProperty(d.getValue().getId()).asObject());
        colVisitorId.setCellValueFactory(d ->
            new SimpleStringProperty(d.getValue().getVisitorId()));
        colTime.setCellValueFactory(d ->
            new SimpleStringProperty(d.getValue().getVisitTime()));
        colVisitors.setCellValueFactory(d ->
            new SimpleIntegerProperty(d.getValue().getNumVisitors()).asObject());
        colType.setCellValueFactory(d ->
            new SimpleStringProperty(d.getValue().getOrderType()));
        colStatus.setCellValueFactory(d ->
            new SimpleStringProperty(d.getValue().getStatus()));
        UiCells.applyStatusChip(colStatus);
        colEmail.setCellValueFactory(d ->
            new SimpleStringProperty(d.getValue().getEmail()));

        tableOrders.setPlaceholder(EmptyState.of(Icons.calendar(34),
            "No reservations for today",
            "Bookings for today's date will appear here."));

        loadData();
    }

    /** Sends {@code GET_TODAY_ORDERS} and refreshes the table. */
    private void loadData() {
        lblError.setText("");
        UserSession s = UserSession.getInstance();
        Integer parkId = s != null ? s.getParkId() : null;
        if (parkId == null) {
            lblError.setText("No park assigned to your account.");
            lblCount.setText("");
            return;
        }

        ChatClient.lastTodayOrders = null;
        ClientUI.chat.accept(new Message("GET_TODAY_ORDERS", parkId));
        List<OrderDetail> orders = ChatClient.lastTodayOrders;
        tableOrders.getItems().clear();

        if (orders == null) {
            lblError.setText("Could not load today's reservations. Is the server running?");
            lblCount.setText("");
            return;
        }
        tableOrders.getItems().addAll(orders);
        lblCount.setText(orders.size() + " reservation(s) today");
    }

    /** Reloads the table from the server. */
    @FXML
    public void handleRefresh(ActionEvent event) {
        loadData();
    }

    /** Closes this window. */
    @FXML
    public void handleClose(ActionEvent event) {
        ((Stage) lblParkName.getScene().getWindow()).close();
    }

    /**
     * @param stage the stage to use
     * @throws Exception if the FXML cannot be loaded
     */
    public void start(Stage stage) throws Exception {
        Parent root = FXMLLoader.load(
            getClass().getResource("/gui/TodayReservationsFrame.fxml"));
        stage.setTitle("GoNature — Today's Reservations");
        Scene scene = new Scene(root);
        ThemeManager.register(scene);
        stage.setScene(scene);
        stage.show();
    }
}
