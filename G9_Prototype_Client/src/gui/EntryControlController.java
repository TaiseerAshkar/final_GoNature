package gui;

import client.ChatClient;
import client.ClientUI;
import logic.EntryCheckRequest;
import logic.EntryResult;
import logic.ExitRequest;
import logic.Message;
import logic.OrderDetail;
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
 * <p>two tabs: Check-In Visitor (two modes: pre-booked and walk-in) and Register Exit.
 * the check-in tab defaults to Pre-booked mode: employee enters a visitor ID, clicks
 * "Find Booking" to preview today's confirmed order, then "Confirm Entry" to complete it.
 * Walk-in mode collects visit type + visitor count and creates a new order immediately.
 */
public class EntryControlController {

    @FXML private Label lblParkName;
    @FXML private Button btnTheme;

    // ── Mode toggle ────────────────────────────────────────────────────────────
    @FXML private ToggleButton btnModePrebooked;
    @FXML private ToggleButton btnModeWalkin;

    // ── Shared check-in input ──────────────────────────────────────────────────
    @FXML private TextField txtVisitorId;

    // ── Pre-booked mode ────────────────────────────────────────────────────────
    @FXML private VBox   panePrebooked;
    @FXML private Button btnFindBooking;

    // ── Booking details card ───────────────────────────────────────────────────
    @FXML private VBox  paneBookingDetails;
    @FXML private Label lblBookingStatus;
    @FXML private Label lblBookingOrderId;
    @FXML private Label lblBookingVisitorId;
    @FXML private Label lblBookingPark;
    @FXML private Label lblBookingDate;
    @FXML private Label lblBookingTime;
    @FXML private Label lblBookingVisitors;
    @FXML private Label lblBookingType;
    @FXML private Button btnConfirmEntry;

    // ── Walk-in mode ───────────────────────────────────────────────────────────
    @FXML private VBox             paneWalkin;
    @FXML private TextField        txtWalkInVisitors;
    @FXML private ComboBox<String> cboOrderType;
    @FXML private Label            lblVisitorHint;
    @FXML private Button           btnCheckIn;

    // ── Feedback + result card ────────────────────────────────────────────────
    @FXML private Label lblCheckInMessage;
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

    // ── Exit tab ───────────────────────────────────────────────────────────────
    @FXML private ComboBox<Park> cboExitPark;
    @FXML private TextField      txtExitVisitorId;
    @FXML private Label          lblExitMessage;

    /** the employee's assigned park, resolved once at startup, used for all server calls. */
    private Park selectedPark;

    /** booking found in pre-booked mode; set by handleFindBooking(), consumed by handleConfirmEntry(). */
    private OrderDetail foundBooking;

    @FXML
    public void initialize() {
        ThemeManager.installToggle(btnTheme);
        UserSession s = UserSession.getInstance();
        lblParkName.setText(s != null && s.getParkId() != null
            ? "Park #" + s.getParkId() : "No Park Assigned");
        cboOrderType.getItems().addAll("SOLO", "FAMILY", "GROUP");
        cboOrderType.setValue("SOLO");
        cboOrderType.valueProperty().addListener((obs, oldVal, newVal) ->
            updateVisitorControls(newVal));
        updateVisitorControls("SOLO");
        loadSelectedPark();
        applyModeStyle(true); // pre-booked active by default
    }

    /**
     * loads parks from the server, pre-selects the employee's assigned park,
     * locks the exit park selector, and caches park name in UserSession.
     */
    private void loadSelectedPark() {
        ChatClient.lastParkList = null;
        ClientUI.chat.accept(new Message("GET_PARKS", null));
        List<Park> parks = ChatClient.lastParkList;
        if (parks == null || parks.isEmpty()) return;

        cboExitPark.getItems().addAll(parks);

        UserSession s = UserSession.getInstance();
        if (s != null && s.getParkId() != null) {
            for (Park p : parks) {
                if (p.getId() == s.getParkId()) {
                    selectedPark = p;
                    cboExitPark.setValue(p);
                    lblParkName.setText(p.getName());
                    s.setParkName(p.getName());
                    break;
                }
            }
        }
        cboExitPark.setDisable(true);
    }

    // ── Mode toggle ────────────────────────────────────────────────────────────

    @FXML
    public void handleModeToggle(ActionEvent event) {
        boolean preBooked = (event.getSource() == btnModePrebooked);
        applyModeStyle(preBooked);
    }

    private void applyModeStyle(boolean preBooked) {
        // keep at least one button selected at all times
        btnModePrebooked.setSelected(preBooked);
        btnModeWalkin.setSelected(!preBooked);

        btnModePrebooked.getStyleClass().setAll(
            "mode-btn-left", preBooked ? "mode-btn-active" : "mode-btn-inactive");
        btnModeWalkin.getStyleClass().setAll(
            "mode-btn-right", preBooked ? "mode-btn-inactive" : "mode-btn-active");

        panePrebooked.setVisible(preBooked);
        panePrebooked.setManaged(preBooked);
        paneWalkin.setVisible(!preBooked);
        paneWalkin.setManaged(!preBooked);

        // reset state on mode switch
        foundBooking = null;
        paneBookingDetails.setVisible(false);
        paneBookingDetails.setManaged(false);
        paneResult.setVisible(false);
        paneResult.setManaged(false);
        lblCheckInMessage.setText("");
        lblCheckInMessage.setStyle("-fx-font-size:12px;");
    }

    // ── Pre-booked mode ────────────────────────────────────────────────────────

    /** sends FIND_TODAY_BOOKING and shows the booking details card, or an error hint. */
    @FXML
    public void handleFindBooking(ActionEvent event) {
        paneBookingDetails.setVisible(false);
        paneBookingDetails.setManaged(false);
        paneResult.setVisible(false);
        paneResult.setManaged(false);
        lblCheckInMessage.setText("");

        String visitorId = txtVisitorId.getText().trim();
        if (visitorId.isEmpty())             { setCheckInError("Please enter a Visitor ID."); return; }
        if (!visitorId.matches("\\d+"))      { setCheckInError("ID must contain numbers only."); return; }
        if (visitorId.length() != 9)         { setCheckInError("ID must be exactly 9 digits."); return; }
        if (selectedPark == null) {
            setCheckInError("No park assigned to your account."); return;
        }

        ChatClient.lastFoundBooking     = null;
        ChatClient.lastFindBookingError = null;
        ClientUI.chat.accept(new Message("FIND_TODAY_BOOKING",
            new EntryCheckRequest(visitorId, selectedPark.getId(), 0, "")));

        if (ChatClient.lastFoundBooking != null) {
            showBookingDetails(ChatClient.lastFoundBooking);
        } else {
            String err = ChatClient.lastFindBookingError;
            setCheckInError(err != null ? err
                : "No confirmed booking found for this visitor today. Switch to Walk-in mode.");
        }
    }

    private void showBookingDetails(OrderDetail b) {
        foundBooking = b;
        String status = b.getStatus() != null ? b.getStatus().toUpperCase() : "CONFIRMED";
        lblBookingStatus.setText(status);
        lblBookingStatus.getStyleClass().setAll("chip",
            "CONFIRMED".equals(status) ? "chip-confirmed" : "chip-pending");
        lblBookingOrderId.setText("#" + b.getId());
        lblBookingVisitorId.setText(b.getVisitorId());
        lblBookingPark.setText(b.getParkName());
        lblBookingDate.setText(b.getVisitDate());
        lblBookingTime.setText(b.getVisitTime());
        lblBookingVisitors.setText(String.valueOf(b.getNumVisitors()));
        lblBookingType.setText(b.getOrderType());
        paneBookingDetails.setVisible(true);
        paneBookingDetails.setManaged(true);
    }

    /** confirms entry for the pre-booked visitor found by handleFindBooking(). */
    @FXML
    public void handleConfirmEntry(ActionEvent event) {
        if (foundBooking == null) {
            setCheckInError("Please click 'Find Booking' first."); return;
        }
        if (selectedPark == null) {
            setCheckInError("No park assigned to your account."); return;
        }

        ChatClient.lastEntryResult = null;
        ChatClient.lastEntryError  = null;
        ClientUI.chat.accept(new Message("CHECK_IN_VISITOR",
            new EntryCheckRequest(
                foundBooking.getVisitorId(),
                selectedPark.getId(),
                foundBooking.getNumVisitors(),
                foundBooking.getOrderType())));

        if (ChatClient.lastEntryResult != null) {
            paneBookingDetails.setVisible(false);
            paneBookingDetails.setManaged(false);
            showEntryResult(ChatClient.lastEntryResult,
                foundBooking.getVisitorId(), selectedPark.getName(),
                foundBooking.getNumVisitors(), foundBooking.getOrderType());
            foundBooking = null;
        } else {
            setCheckInError(ChatClient.lastEntryError != null
                ? ChatClient.lastEntryError : "Check-in failed. Please try again.");
        }
    }

    // ── Walk-in mode ────────────────────────────────────────────────────────────

    @FXML
    public void handleCheckIn(ActionEvent event) {
        paneResult.setVisible(false);
        paneResult.setManaged(false);
        lblCheckInMessage.setText("");

        String visitorId = txtVisitorId.getText().trim();
        if (visitorId.isEmpty())             { setCheckInError("Please enter a Visitor ID."); return; }
        if (!visitorId.matches("\\d+"))      { setCheckInError("ID must contain numbers only."); return; }
        if (visitorId.length() != 9)         { setCheckInError("ID must be exactly 9 digits."); return; }
        if (selectedPark == null) { setCheckInError("No park assigned to your account."); return; }

        String orderType = cboOrderType.getValue();
        if (orderType == null) { setCheckInError("Please select a visit type."); return; }

        int numVisitors;
        try {
            numVisitors = Integer.parseInt(txtWalkInVisitors.getText().trim());
            if (numVisitors < 1) throw new NumberFormatException();
        } catch (NumberFormatException e) {
            setCheckInError("Number of visitors must be at least 1.");
            return;
        }

        switch (orderType) {
            case "SOLO":
                if (numVisitors != 1) { setCheckInError("SOLO check-in allows exactly 1 visitor."); return; }
                break;
            case "FAMILY":
                if (numVisitors < 2 || numVisitors > 15) { setCheckInError("FAMILY requires 2–15 visitors."); return; }
                break;
            case "GROUP":
                if (numVisitors < 2) { setCheckInError("Minimum 2 visitors required for a GROUP check-in."); return; }
                if (numVisitors > 16) { setCheckInError("Maximum 16 visitors for GROUP (15 guests + guide)."); return; }
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

    // ── Shared result card ──────────────────────────────────────────────────────

    private void showEntryResult(EntryResult r,
                                  String fallbackVisitorId,
                                  String fallbackParkName,
                                  int    fallbackVisitors,
                                  String fallbackType) {
        boolean prebooked = r.isPrebooked();
        String oType = (r.getOrderType() != null && !r.getOrderType().isEmpty()) ? r.getOrderType() : fallbackType;
        String vId   = (r.getVisitorId() != null && !r.getVisitorId().isEmpty())  ? r.getVisitorId()  : fallbackVisitorId;
        String park  = (r.getParkName()  != null && !r.getParkName().isEmpty())   ? r.getParkName()   : fallbackParkName;
        int    vis   = r.getNumVisitors() > 0 ? r.getNumVisitors() : fallbackVisitors;

        String bannerColor = prebooked ? "#1b5e20" : "#0d47a1";
        bannerBox.setStyle("-fx-padding: 10 14; -fx-background-radius: 6; -fx-background-color: " + bannerColor + ";");
        lblResultStatus.setText(prebooked
            ? "ENTRY APPROVED — PRE-BOOKED " + oType
            : "ENTRY APPROVED — WALK-IN " + oType);

        lblResultOrderId.setText(r.getOrderId() > 0 ? "#" + r.getOrderId() : "—");
        lblResultVisitorId.setText(vId);
        lblResultPark.setText(park);
        lblResultVisitors.setText(String.valueOf(vis));
        lblResultType.setText(oType);
        lblResultPayment.setText(String.format("$%.2f", r.getTotalPrice()));
        lblResultNote.setText(r.getMessage());
        paneResult.setVisible(true);
        paneResult.setManaged(true);
    }

    // ── Exit tab ────────────────────────────────────────────────────────────────

    @FXML
    public void handleRegisterExit(ActionEvent event) {
        lblExitMessage.setText("");

        String visitorId = txtExitVisitorId.getText().trim();
        if (visitorId.isEmpty())        { setExitMessage("Please enter a Visitor ID.", false); return; }
        if (!visitorId.matches("\\d+")) { setExitMessage("ID must contain numbers only.", false); return; }
        if (visitorId.length() != 9)    { setExitMessage("ID must be exactly 9 digits.", false); return; }

        Park exitPark = cboExitPark.getValue();
        if (exitPark == null) { setExitMessage("Please select a park.", false); return; }

        ChatClient.lastExitSuccess = false;
        ChatClient.lastExitError   = null;
        ClientUI.chat.accept(new Message("REGISTER_EXIT",
            new ExitRequest(visitorId, exitPark.getId())));

        if (ChatClient.lastExitSuccess) {
            setExitMessage("Exit registered for visitor " + visitorId
                + " at " + exitPark.getName() + ". Have a great day!", true);
            txtExitVisitorId.clear();
        } else {
            setExitMessage(ChatClient.lastExitError != null
                ? ChatClient.lastExitError
                : "No active entry found for visitor " + visitorId
                  + " at " + exitPark.getName() + " today.", false);
        }
    }

    // ── QR scan ─────────────────────────────────────────────────────────────────

    @FXML
    public void handleScanQRCode(ActionEvent event) {
        TextInputDialog dlg = new TextInputDialog();
        dlg.setTitle("QR Code Scanner");
        dlg.setHeaderText("📷  Scan Visitor Entry QR Code");
        dlg.setContentText(
            "Enter the value from the visitor's QR code\n" +
            "or type their government ID directly:");
        ThemeManager.styleDialog(dlg);
        dlg.showAndWait().ifPresent(raw -> {
            String visitorId = QRCodeUtil.parseVisitorId(raw);
            if (!visitorId.isEmpty()) {
                txtVisitorId.setText(visitorId);
                lblCheckInMessage.setText("");
                paneBookingDetails.setVisible(false);
                paneBookingDetails.setManaged(false);
                paneResult.setVisible(false);
                paneResult.setManaged(false);
                foundBooking = null;
            }
        });
    }

    // ── Misc ────────────────────────────────────────────────────────────────────

    @FXML
    public void handleClose(ActionEvent event) {
        ((Stage) lblParkName.getScene().getWindow()).close();
    }

    public void start(Stage stage) throws Exception {
        Parent root = FXMLLoader.load(getClass().getResource("/gui/EntryControlFrame.fxml"));
        stage.setTitle("GoNature — Entry Control");
        Scene scene = new Scene(root);
        ThemeManager.register(scene);
        stage.setScene(scene);
        stage.centerOnScreen();
        stage.show();
    }

    private void updateVisitorControls(String orderType) {
        if (orderType == null) return;
        switch (orderType) {
            case "SOLO":
                txtWalkInVisitors.setText("1");
                txtWalkInVisitors.setDisable(true);
                txtWalkInVisitors.setStyle("-fx-background-color: -park-surface-2; -fx-background-radius: 4;");
                lblVisitorHint.setText("SOLO: exactly 1 visitor.");
                break;
            case "FAMILY":
                txtWalkInVisitors.setDisable(false);
                txtWalkInVisitors.setStyle("");
                if ("1".equals(txtWalkInVisitors.getText().trim())) txtWalkInVisitors.setText("2");
                lblVisitorHint.setText("FAMILY: 2–15 visitors (15% discount for subscribers).");
                break;
            case "GROUP":
                txtWalkInVisitors.setDisable(false);
                txtWalkInVisitors.setStyle("");
                if ("1".equals(txtWalkInVisitors.getText().trim())) txtWalkInVisitors.setText("2");
                lblVisitorHint.setText("GROUP: 2–16 visitors. The Visitor ID must belong to an approved guide.");
                break;
            default:
                txtWalkInVisitors.setDisable(false);
                txtWalkInVisitors.setStyle("");
                lblVisitorHint.setText("");
        }
    }

    private void setCheckInError(String msg) {
        // theme class instead of a hardcoded red so the message stays readable in dark mode
        lblCheckInMessage.setStyle("-fx-font-size: 12px;");
        lblCheckInMessage.getStyleClass().remove("lbl-error");
        lblCheckInMessage.getStyleClass().add("lbl-error");
        lblCheckInMessage.setText(msg);
    }

    private void setExitMessage(String msg, boolean success) {
        lblExitMessage.setStyle("");
        lblExitMessage.getStyleClass().setAll(success ? "lbl-success" : "lbl-error");
        lblExitMessage.setText(msg);
    }
}
