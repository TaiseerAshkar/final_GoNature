package gui;

import client.ChatClient;
import client.ClientUI;
import logic.Message;
import logic.Park;
import logic.ParkSettingsRequest;
import logic.UserSession;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.stage.Stage;

/**
 * controller for the park manager's Park Settings panel ({@code ParkSettingsFrame.fxml}).
 *
 * <p>loads the current park configuration from the server on open, lets the manager
 * edit the four mutable parameters, validates input client-side before sending, and
 * displays server feedback inline.
 *
 * <p>accessible only to PARK_MANAGER — the button that opens this screen is disabled
 * for PARK_EMPLOYEE in {@link EmployeeController#initialize()}.
 */
public class ParkSettingsController {

    @FXML private Button    btnTheme;
    @FXML private Label     lblParkName;
    @FXML private TextField txtCapacity;
    @FXML private TextField txtMaxOrders;
    @FXML private TextField txtDuration;
    @FXML private TextField txtPrice;
    @FXML private Label     lblStatus;

    /**
     * fetches current settings from the server using the manager's assigned park ID
     * and pre-fills all fields. shows an error if the park cannot be loaded.
     */
    @FXML
    public void initialize() {
        ThemeManager.installToggle(btnTheme);
        UserSession s = UserSession.getInstance();
        if (s == null || s.getParkId() == null) {
            setError("No park assigned to your account.");
            return;
        }

        ChatClient.lastParkSettings      = null;
        ChatClient.lastParkSettingsError = null;
        ClientUI.chat.accept(new Message("GET_PARK_SETTINGS", s.getParkId()));

        if (ChatClient.lastParkSettings != null) {
            populate(ChatClient.lastParkSettings);
        } else {
            setError(ChatClient.lastParkSettingsError != null
                ? ChatClient.lastParkSettingsError
                : "Could not load park settings. Is the server running?");
        }
    }

    /** fills form fields from a {@link Park} snapshot. */
    private void populate(Park p) {
        lblParkName.setText(p.getName() + "  (ID " + p.getId() + ")");
        txtCapacity.setText(String.valueOf(p.getCapacity()));
        txtMaxOrders.setText(String.valueOf(p.getMaxOrders()));
        txtDuration.setText(String.valueOf(p.getVisitDurationHours()));
        txtPrice.setText(String.format("%.2f", p.getFullPrice()));
    }

    /**
     * validates the form and sends {@code SUBMIT_PARK_SETTINGS} to queue the change
     * for department manager approval. The live park values are NOT updated immediately.
     *
     * @param event the button-click event
     */
    @FXML
    public void handleSave(ActionEvent event) {
        lblStatus.setText("");
        lblStatus.setStyle("");

        UserSession s = UserSession.getInstance();
        if (s == null || s.getParkId() == null) {
            setError("Session error — please log in again.");
            return;
        }

        int    capacity, maxOrders, duration;
        double price;
        try {
            capacity  = Integer.parseInt(txtCapacity.getText().trim());
            maxOrders = Integer.parseInt(txtMaxOrders.getText().trim());
            duration  = Integer.parseInt(txtDuration.getText().trim());
            price     = Double.parseDouble(txtPrice.getText().trim());
        } catch (NumberFormatException e) {
            setError("All fields must contain valid numbers.");
            return;
        }

        // Client-side validation mirrors server rules.
        if (capacity < 1 || capacity > 10000) {
            setError("Capacity must be between 1 and 10 000."); return;
        }
        if (maxOrders < 1 || maxOrders > capacity) {
            setError("Max bookable orders must be between 1 and the capacity (" + capacity + ")."); return;
        }
        if (duration < 1 || duration > 24) {
            setError("Visit duration must be between 1 and 24 hours."); return;
        }
        if (price <= 0 || price > 9999.99) {
            setError("Full price must be greater than $0.00 and at most $9 999.99."); return;
        }

        String currentName = lblParkName.getText().contains("(")
            ? lblParkName.getText().substring(0, lblParkName.getText().indexOf("(")).trim()
            : lblParkName.getText();

        // Send as a pending request — the department manager must approve before it goes live.
        ParkSettingsRequest req = new ParkSettingsRequest(
            s.getParkId(), currentName, s.getUserId(),
            capacity, maxOrders, duration, price);

        ChatClient.lastSettingsSubmitSuccess = null;
        ChatClient.lastSettingsSubmitFail    = null;
        ClientUI.chat.accept(new Message("SUBMIT_PARK_SETTINGS", req));

        if (ChatClient.lastSettingsSubmitSuccess != null) {
            setSuccess("Settings change submitted for department manager approval.");
        } else {
            setError(ChatClient.lastSettingsSubmitFail != null
                ? ChatClient.lastSettingsSubmitFail
                : "Submission failed. Please try again.");
        }
    }

    /** @param event the button-click event */
    @FXML
    public void handleClose(ActionEvent event) {
        ((Stage) lblParkName.getScene().getWindow()).close();
    }

    /** @param msg the error text */
    private void setError(String msg) {
        lblStatus.setStyle("");
        lblStatus.getStyleClass().setAll("lbl-error");
        lblStatus.setText(msg);
    }

    /** @param msg the success text */
    private void setSuccess(String msg) {
        lblStatus.setStyle("");
        lblStatus.getStyleClass().setAll("lbl-success");
        lblStatus.setText(msg);
    }

    /**
     * @param stage the stage in which to show the panel
     * @throws Exception if the FXML file cannot be loaded
     */
    public void start(Stage stage) throws Exception {
        Parent root = FXMLLoader.load(getClass().getResource("/gui/ParkSettingsFrame.fxml"));
        stage.setTitle("GoNature — Park Settings");
        Scene scene = new Scene(root);
        ThemeManager.register(scene);
        stage.setScene(scene);
        stage.centerOnScreen();
        stage.show();
    }
}
