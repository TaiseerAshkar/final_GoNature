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

import java.util.List;

/**
 * FXML controller for the Waiting List screen ({@code WaitingListFrame.fxml}).
 *
 * <p>sends {@code GET_WAITING_LIST} for the logged-in employee's park and shows
 * all queued visitors in a table.
 */
public class WaitingListController {

    @FXML private Label lblParkName;
    @FXML private Label lblCount;
    @FXML private Label lblError;

    @FXML private TableView<OrderDetail>            tableWaiting;
    @FXML private TableColumn<OrderDetail, String>  colVisitorId;
    @FXML private TableColumn<OrderDetail, String>  colDate;
    @FXML private TableColumn<OrderDetail, String>  colTime;
    @FXML private TableColumn<OrderDetail, Integer> colVisitors;
    @FXML private TableColumn<OrderDetail, String>  colType;
    @FXML private TableColumn<OrderDetail, String>  colAddedOn;

    /**
     * populates the park label, wires column factories, and auto-loads waiting-list data.
     */
    @FXML
    public void initialize() {
        UserSession s = UserSession.getInstance();
        Integer parkId = s != null ? s.getParkId() : null;
        lblParkName.setText(parkId != null ? "Park #" + parkId : "No Park Assigned");

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
        colAddedOn.setCellValueFactory(d ->
            new SimpleStringProperty(d.getValue().getCreatedAt()));

        loadData();
    }

    /** Sends {@code GET_WAITING_LIST} and refreshes the table. */
    private void loadData() {
        lblError.setText("");
        UserSession s = UserSession.getInstance();
        Integer parkId = s != null ? s.getParkId() : null;
        if (parkId == null) {
            lblError.setText("No park assigned to your account.");
            lblCount.setText("");
            return;
        }

        ChatClient.lastWaitingList = null;
        ClientUI.chat.accept(new Message("GET_WAITING_LIST", parkId));
        List<OrderDetail> waiting = ChatClient.lastWaitingList;
        tableWaiting.getItems().clear();

        if (waiting == null) {
            lblError.setText("Could not load the waiting list. Is the server running?");
            lblCount.setText("");
            return;
        }
        tableWaiting.getItems().addAll(waiting);
        lblCount.setText(waiting.size() + " visitor(s) waiting");
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
            getClass().getResource("/gui/WaitingListFrame.fxml"));
        stage.setTitle("GoNature — Waiting List");
        stage.setScene(new Scene(root));
        stage.show();
    }
}
