package gui;

import client.ChatClient;
import client.ClientUI;
import logic.EntryCheckRequest;
import logic.EntryResult;
import logic.ExitRequest;
import logic.Message;
import logic.Park;
import logic.UserSession;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.TextInputDialog;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

import java.util.List;

/**
 * controller for the park employee entry-control screen ({@code EntryControlFrame.fxml}).
 *
 * <p>two tabs: Check-In Visitor (validates input, sends {@code CHECK_IN_VISITOR},
 * shows the result card with pricing) and Register Exit (sends {@code REGISTER_EXIT}).
 * both tabs load parks from the server and pre-select the employee's assigned park.
 */
public class EntryControlController {

    @FXML private Label lblParkName;

    // Check-in tab fields
    @FXML private TextField        txtVisitorId;
    @FXML private ComboBox<Park>   cboPark;
    @FXML private TextField        txtWalkInVisitors;
    @FXML private ComboBox<String> cboOrderType;
    @FXML private Label            lblVisitorHint;
    @FXML private Button           btnCheckIn;
    @FXML private Label            lblCheckInMessage;

    // Entry result card
    @FXML private VBox  paneResult;
    @FXML private HBox  bannerBox;
    @FXML private Label lblResultStatus;
    @FXML private Label lblResultOrderId;
    @FXML private Label lblResultVisitorId;
    @FXML private Label lblResultPark;
    @FXML private Label lblResultVisitors;
    @FXML private Label lblResultType;
    @FXML private Label lblResultPayment;
    @FXML private Label lblResultNote;

    // Exit tab fields
    @FXML private ComboBox<Park> cboExitPark;
    @FXML private TextField      txtExitVisitorId;
    @FXML private Label          lblExitMessage;

    /**
     * populates the park badge, pre-fills order type, and loads parks into both dropdowns.
     */
    @FXML
    public void initialize() {
        UserSession s = UserSession.getInstance();
        lblParkName.setText(s != null && s.getParkId() != null
            ? "Park #" + s.getParkId() : "No Park Assigned");
        cboOrderType.getItems().addAll("SOLO", "FAMILY", "GROUP");
        cboOrderType.setValue("SOLO");
        // Listener: update hint and auto-set/lock visitors when type changes
        cboOrderType.valueProperty().addListener((obs, oldVal, newVal) ->
            updateVisitorControls(newVal));
        updateVisitorControls("SOLO"); // apply initial state
        loadParksIntoSelectors();
    }

    /**
     * loads parks from the server, fills both dropdowns, and pre-selects the employee's
     * assigned park. also updates the header badge to show the park name.
     */
    private void loadParksIntoSelectors() {
        ChatClient.lastParkList = null;
        ClientUI.chat.accept(new Message("GET_PARKS", null));
        List<Park> parks = ChatClient.lastParkList;
        if (parks == null || parks.isEmpty()) return;

        cboPark.getItems().addAll(parks);
        cboExitPark.getItems().addAll(parks);

        // Pre-select the employee's assigned park
        UserSession s = UserSession.getInstance();
        if (s != null && s.getParkId() != null) {
            for (Park p : parks) {
                if (p.getId() == s.getParkId()) {
                    cboPark.setValue(p);
                    cboExitPark.setValue(p);
                    lblParkName.setText(p.getName());
                    break;
                }
            }
        }
    }

    /**
     * locks the count field at 1 for SOLO, or unlocks it for FAMILY / GROUP.
     * also updates the hint so the employee knows the allowed range.
     *
     * @param orderType the newly selected visit type string
     */
    private void updateVisitorControls(String orderType) {
        if (orderType == null) return;
        switch (orderType) {
            case "SOLO":
                txtWalkInVisitors.setText("1");
                txtWalkInVisitors.setDisable(true);
                txtWalkInVisitors.setStyle(
                    "-fx-background-color: #f5f5f5; -fx-background-radius: 4;");
                lblVisitorHint.setText("SOLO: exactly 1 visitor.");
                break;
            case "FAMILY":
                txtWalkInVisitors.setDisable(false);
                txtWalkInVisitors.setStyle("");
                if ("1".equals(txtWalkInVisitors.getText().trim()))
                    txtWalkInVisitors.setText("2");
                lblVisitorHint.setText("FAMILY: 2–15 visitors (15% discount for subscribers).");
                break;
            case "GROUP":
                txtWalkInVisitors.setDisable(false);
                txtWalkInVisitors.setStyle("");
                if ("1".equals(txtWalkInVisitors.getText().trim()))
                    txtWalkInVisitors.setText("2");
                lblVisitorHint.setText(
                    "GROUP: 2–16 visitors led by a registered guide (guide enters free).");
                break;
            default:
                txtWalkInVisitors.setDisable(false);
                txtWalkInVisitors.setStyle("");
                lblVisitorHint.setText("");
        }
    }

    /**
     * simulates a QR code scan by prompting the employee for the code value from the
     * visitor's phone. parses the {@code GONATURE:ORDER=N:VISITOR=ID} payload (or accepts
     * a plain visitor ID) and pre-fills the Visitor ID field so check-in can proceed normally.
     *
     * @param event the button-click event
     */
    @FXML
    public void handleScanQRCode(ActionEvent event) {
        TextInputDialog dlg = new TextInputDialog();
        dlg.setTitle("QR Code Scanner");
        dlg.setHeaderText("📷  Scan Visitor Entry QR Code");
        dlg.setContentText(
            "Enter the value from the visitor's QR code\n" +
            "or type their government ID directly:");
        dlg.showAndWait().ifPresent(raw -> {
            String visitorId = QRCodeUtil.parseVisitorId(raw);
            if (!visitorId.isEmpty()) {
                txtVisitorId.setText(visitorId);
                setCheckInError(""); // clear stale messages
                clearCheckInState();
            }
        });
    }

    /**
     * validates the form, sends {@code CHECK_IN_VISITOR}, then shows the result card
     * on success or an error message on denial.
     *
     * @param event the button-click event
     */
    @FXML
    public void handleCheckIn(ActionEvent event) {
        clearCheckInState();

        String visitorId = txtVisitorId.getText().trim();
        if (visitorId.isEmpty()) { setCheckInError("Please enter a Visitor ID."); return; }

        Park selectedPark = cboPark.getValue();
        if (selectedPark == null) { setCheckInError("Please select a park."); return; }

        String orderType = cboOrderType.getValue();
        if (orderType == null) { setCheckInError("Please select an order type."); return; }

        int numVisitors;
        try {
            numVisitors = Integer.parseInt(txtWalkInVisitors.getText().trim());
            if (numVisitors < 1) throw new NumberFormatException();
        } catch (NumberFormatException e) {
            setCheckInError("Number of visitors must be at least 1.");
            return;
        }

        // Per-type client-side visitor count validation (mirrors server rules)
        switch (orderType) {
            case "SOLO":
                if (numVisitors != 1) {
                    setCheckInError("SOLO check-in allows exactly 1 visitor.");
                    return;
                }
                break;
            case "FAMILY":
                if (numVisitors < 2 || numVisitors > 15) {
                    setCheckInError("FAMILY check-in requires 2–15 visitors.");
                    return;
                }
                break;
            case "GROUP":
                if (numVisitors < 2) {
                    setCheckInError("Minimum 2 visitors required for a GROUP check-in.");
                    return;
                }
                if (numVisitors > 16) {
                    setCheckInError(
                        "Maximum 16 visitors allowed for a GROUP check-in (15 guests + guide).");
                    return;
                }
                break;
        }

        ChatClient.lastEntryResult = null;
        ChatClient.lastEntryError  = null;
        ClientUI.chat.accept(new Message("CHECK_IN_VISITOR",
            new EntryCheckRequest(visitorId, selectedPark.getId(), numVisitors, orderType)));

        if (ChatClient.lastEntryResult != null)
            showEntryResult(ChatClient.lastEntryResult,
                visitorId, selectedPark.getName(), numVisitors, orderType);
        else
            setCheckInError(ChatClient.lastEntryError != null
                ? ChatClient.lastEntryError : "Check-in failed. Please try again.");
    }

    /**
     * populates and shows the result card. server values take priority; form values
     * are fallbacks for any fields the server left blank or zero.
     *
     * @param r                   the successful {@link EntryResult} from the server
     * @param fallbackVisitorId   visitor ID from the form (used if server omits it)
     * @param fallbackParkName    park name from the dropdown (used if server omits it)
     * @param fallbackVisitors    visitor count from the form (used if server returns 0)
     * @param fallbackType        order type from the form (used if server omits it)
     */
    private void showEntryResult(EntryResult r,
                                  String fallbackVisitorId,
                                  String fallbackParkName,
                                  int    fallbackVisitors,
                                  String fallbackType) {
        boolean prebooked = r.isPrebooked();

        // server value takes priority; fall back to the form value if server left it blank
        String oType = (r.getOrderType() != null && !r.getOrderType().isEmpty())
                        ? r.getOrderType() : fallbackType;
        String vId   = (r.getVisitorId() != null && !r.getVisitorId().isEmpty())
                        ? r.getVisitorId() : fallbackVisitorId;
        String park  = (r.getParkName() != null && !r.getParkName().isEmpty())
                        ? r.getParkName() : fallbackParkName;
        int    vis   = r.getNumVisitors() > 0 ? r.getNumVisitors() : fallbackVisitors;

        String bannerColor = prebooked ? "#1b5e20" : "#0d47a1";
        bannerBox.setStyle("-fx-padding: 10 14; -fx-background-radius: 6; " +
                           "-fx-background-color: " + bannerColor + ";");
        lblResultStatus.setText(prebooked
            ? "ENTRY APPROVED — PRE-BOOKED " + oType
            : "ENTRY APPROVED — WALK-IN " + oType);

        lblResultOrderId.setText(r.getOrderId() > 0 ? "#" + r.getOrderId() : "—");
        lblResultVisitorId.setText(vId);
        lblResultPark.setText(park);
        lblResultVisitors.setText(String.valueOf(vis));
        lblResultType.setText(oType);
        lblResultPayment.setText(String.format("$%.2f", r.getTotalPrice()));
        // pricing note is built server-side so it accurately reflects guide/subscriber status
        lblResultNote.setText(r.getMessage());
        paneResult.setVisible(true);
        paneResult.setManaged(true);
    }

    /**
     * validates the exit form and sends {@code REGISTER_EXIT}.
     * clears the visitor ID field on success.
     *
     * @param event the button-click event
     */
    @FXML
    public void handleRegisterExit(ActionEvent event) {
        lblExitMessage.setText("");

        String visitorId = txtExitVisitorId.getText().trim();
        if (visitorId.isEmpty()) { setExitMessage("Please enter a Visitor ID.", false); return; }

        Park selectedPark = cboExitPark.getValue();
        if (selectedPark == null) { setExitMessage("Please select a park.", false); return; }

        ChatClient.lastExitSuccess = false;
        ChatClient.lastExitError   = null;
        ClientUI.chat.accept(new Message("REGISTER_EXIT",
            new ExitRequest(visitorId, selectedPark.getId())));

        if (ChatClient.lastExitSuccess) {
            setExitMessage("Exit registered for visitor " + visitorId
                + " at " + selectedPark.getName() + ". Have a great day!", true);
            txtExitVisitorId.clear();
        } else {
            setExitMessage(ChatClient.lastExitError != null
                ? ChatClient.lastExitError
                : "No active entry found for visitor " + visitorId
                  + " at " + selectedPark.getName() + " today.", false);
        }
    }

    /** @param event the button-click event */
    @FXML
    public void handleClose(ActionEvent event) {
        ((Stage) lblParkName.getScene().getWindow()).close();
    }

    /**
     * @param stage the stage in which to show the screen
     * @throws Exception if the FXML file cannot be loaded
     */
    public void start(Stage stage) throws Exception {
        Parent root = FXMLLoader.load(getClass().getResource("/gui/EntryControlFrame.fxml"));
        stage.setTitle("GoNature — Entry Control");
        stage.setScene(new Scene(root));
        stage.show();
    }

    /** hides the result card and clears any previous error so the next check-in starts clean. */
    private void clearCheckInState() {
        lblCheckInMessage.setText("");
        lblCheckInMessage.setStyle("-fx-font-size: 12px;");
        paneResult.setVisible(false);
        paneResult.setManaged(false);
    }

    /** @param msg the error text to display in red */
    private void setCheckInError(String msg) {
        lblCheckInMessage.setStyle("-fx-font-size: 12px; -fx-text-fill: #c62828;");
        lblCheckInMessage.setText(msg);
    }

    /**
     * @param msg     the message text
     * @param success {@code true} for green success style; {@code false} for red error style
     */
    private void setExitMessage(String msg, boolean success) {
        lblExitMessage.setStyle(success
            ? "-fx-font-size: 13px; -fx-font-weight: bold; -fx-text-fill: #1b5e20;"
            : "-fx-font-size: 13px; -fx-text-fill: #c62828;");
        lblExitMessage.setText(msg);
    }
}
