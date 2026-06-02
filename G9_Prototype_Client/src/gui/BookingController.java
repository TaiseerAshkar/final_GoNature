package gui;

import client.ChatClient;
import client.ClientUI;
import client.NotificationCenter;
import logic.BookingRequest;
import logic.BookingResult;
import logic.Message;
import logic.Park;
import logic.UserSession;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import java.io.IOException;
import javafx.stage.Stage;
import javafx.util.StringConverter;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;

/**
 * controller for the booking form screen ({@code BookingFrame.fxml}).
 *
 * <p>lets a logged-in visitor or guide request a new park visit. handles park loading,
 * time-slot setup, order-type selection, client-side validation, live price estimation,
 * waiting-list offer dialog, and navigation to the confirmation screen.
 *
 * <p>validation rules mirror the server so the user gets fast feedback before the round-trip.
 */
public class BookingController {

    @FXML private ComboBox<Park>   cboPark;
    @FXML private DatePicker       dpDate;
    @FXML private ComboBox<String> cboTime;
    @FXML private ComboBox<String> cboOrderType;
    @FXML private TextField        txtVisitors;
    @FXML private Label            lblVisitorHint;
    @FXML private TextField        txtVisitorId;
    @FXML private TextField        txtEmail;
    @FXML private Label            lblPriceEstimate;
    @FXML private Label            lblError;
    @FXML private Button           btnSubmit;

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    /**
     * loads parks, populates combo-boxes, pre-fills visitor ID/email from session,
     * and wires change listeners for the live price estimate.
     */
    @FXML
    public void initialize() {
        loadParks();
        setupTimeSlots();
        setupOrderTypes();
        setupDatePicker();
        prefillUserInfo();
        setupChangeListeners();
    }

    /** disables submit and shows an error if the server returns no parks. */
    private void loadParks() {
        ChatClient.lastParkList = null;
        ClientUI.chat.accept(new Message("GET_PARKS", null));
        List<Park> parks = ChatClient.lastParkList;
        if (parks != null && !parks.isEmpty()) {
            cboPark.getItems().addAll(parks);
            cboPark.setConverter(new StringConverter<Park>() {
                @Override public String toString(Park p)     { return p == null ? "" : p.getName(); }
                @Override public Park   fromString(String s) { return null; }
            });
        } else {
            setError("Could not load parks. Please ensure the server is running.");
            btnSubmit.setDisable(true);
        }
    }

    /** hourly slots 08:00–16:00 to match the park's operating hours. */
    private void setupTimeSlots() {
        cboTime.getItems().addAll(
            "08:00", "09:00", "10:00", "11:00", "12:00",
            "13:00", "14:00", "15:00", "16:00");
    }

    /** sets up the two visit types and applies the initial hint for SOLO. */
    private void setupOrderTypes() {
        cboOrderType.getItems().addAll("SOLO", "GROUP");
        cboOrderType.setValue("SOLO");
        updateVisitorHint("SOLO");
    }

    /** disables past dates and today so only future dates can be selected. */
    private void setupDatePicker() {
        dpDate.setDayCellFactory(picker -> new DateCell() {
            @Override
            public void updateItem(LocalDate date, boolean empty) {
                super.updateItem(date, empty);
                setDisable(empty || !date.isAfter(LocalDate.now()));
            }
        });
    }

    /** pre-fills visitor ID and email so the user doesn't have to retype them. */
    private void prefillUserInfo() {
        UserSession s = UserSession.getInstance();
        if (s != null) {
            txtVisitorId.setText(s.getUserId() != null ? s.getUserId() : "");
            txtEmail.setText(s.getEmail()    != null ? s.getEmail()    : "");
        }
    }

    /** keeps the price estimate in sync whenever type, park, or visitor count changes. */
    private void setupChangeListeners() {
        cboOrderType.valueProperty().addListener((obs, old, val) -> {
            updateVisitorHint(val);
            updatePriceEstimate();
        });
        cboPark.valueProperty().addListener((obs, old, val) -> updatePriceEstimate());
        txtVisitors.textProperty().addListener((obs, old, val) -> updatePriceEstimate());
    }

    /**
     * locks or unlocks the visitor-count field and updates the hint based on visit type.
     * SOLO is fixed at 1; GROUP allows 2–16 and the guide enters free if the booker is a guide.
     *
     * @param orderType the currently selected visit type string
     */
    private void updateVisitorHint(String orderType) {
        if (orderType == null) return;
        switch (orderType) {
            case "SOLO":
                lblVisitorHint.setText("1 visitor — 15% pre-booked discount");
                txtVisitors.setText("1");
                txtVisitors.setDisable(true);
                break;
            case "GROUP":
                UserSession gs = UserSession.getInstance();
                boolean isGuide = gs != null && "GUIDE".equals(gs.getRole());
                lblVisitorHint.setText(isGuide
                    ? "2–16 total (you enter free · others get 25% advance discount)"
                    : "2–16 visitors — 15% group discount");
                txtVisitors.setDisable(false);
                break;
        }
    }

    /**
     * recomputes the price estimate live. clears the label if inputs are incomplete.
     * SOLO: 1 × fullPrice × 0.85 (15% discount).
     * GROUP by guide: (visitors − 1) × fullPrice × 0.75 (guide free + 25% advance discount).
     * GROUP by visitor: visitors × fullPrice × 0.85 (15% group discount).
     */
    private void updatePriceEstimate() {
        Park   park   = cboPark.getValue();
        String visStr = txtVisitors.getText().trim();
        if (park == null || visStr.isEmpty()) { lblPriceEstimate.setText(""); return; }
        try {
            int visitors = Integer.parseInt(visStr);
            if (visitors <= 0) { lblPriceEstimate.setText(""); return; }
            String type  = cboOrderType.getValue();
            double total;
            String note;
            if ("GROUP".equals(type)) {
                UserSession s = UserSession.getInstance();
                boolean isGuide = s != null && "GUIDE".equals(s.getRole());
                if (isGuide) {
                    int paying = Math.max(0, visitors - 1); // guide enters free
                    total = paying * park.getFullPrice() * 0.75;
                    note  = String.format(" (guide free · %d paying · 25%% advance discount)", paying);
                } else {
                    total = visitors * park.getFullPrice() * 0.85;
                    note  = " (15% group discount)";
                }
            } else {
                // SOLO — same 15% pre-booked discount regardless of role
                total = park.getFullPrice() * 0.85;
                note  = " (15% pre-booked discount)";
            }
            lblPriceEstimate.setText(String.format("Estimated total: $%.2f%s", total, note));
        } catch (NumberFormatException ignored) {
            lblPriceEstimate.setText("");
        }
    }

    /**
     * validates the form, sends {@code BOOK_ORDER}, then either navigates to the
     * confirmation screen or offers a waiting-list dialog when the park is full.
     *
     * @param event the button-click event
     */
    @FXML
    public void handleSubmit(ActionEvent event) {
        lblError.setText("");
        BookingRequest req = buildAndValidate();
        if (req == null) return;

        ChatClient.lastBookingResult = null;
        ChatClient.lastBookingError  = null;
        ClientUI.chat.accept(new Message("BOOK_ORDER", req));

        if (ChatClient.lastBookingResult != null) {
            showConfirmation(ChatClient.lastBookingResult);
        } else if ("FULL".equals(ChatClient.lastBookingError)) {
            offerWaitingList(req);
        } else {
            setError(ChatClient.lastBookingError != null
                ? ChatClient.lastBookingError
                : "Booking failed. Please try again.");
        }
    }

    /**
     * validates all form inputs and builds a {@link BookingRequest} on success.
     * SOLO = exactly 1 visitor, any role.
     * GROUP = 2–16 visitors, any role (guides get the guide-free benefit at entry).
     *
     * @return a populated {@link BookingRequest}, or {@code null} if validation failed
     */
    private BookingRequest buildAndValidate() {
        Park park = cboPark.getValue();
        if (park == null) { setError("Please select a park."); return null; }

        LocalDate date = dpDate.getValue();
        if (date == null) { setError("Please select a visit date."); return null; }
        if (!date.isAfter(LocalDate.now())) {
            setError("Visit date must be a future date."); return null;
        }

        String time = cboTime.getValue();
        if (time == null || time.isEmpty()) { setError("Please select a visit time."); return null; }

        String orderType = cboOrderType.getValue();
        if (orderType == null) { setError("Please select a visit type."); return null; }

        String visStr = txtVisitors.getText().trim();
        if (visStr.isEmpty()) { setError("Please enter the number of visitors."); return null; }
        int visitors;
        try {
            visitors = Integer.parseInt(visStr);
        } catch (NumberFormatException e) {
            setError("Number of visitors must be a whole number."); return null;
        }
        switch (orderType) {
            case "SOLO":
                if (visitors != 1) {
                    setError("Solo bookings allow exactly 1 visitor."); return null;
                }
                break;
            case "GROUP":
                if (visitors < 2 || visitors > 16) {
                    setError("Group bookings require 2–16 visitors."); return null;
                }
                break;
        }

        String visitorId = txtVisitorId.getText().trim();
        if (visitorId.isEmpty()) {
            setError("Visitor ID is missing. Please log in again."); return null; }

        String email = txtEmail.getText().trim();
        if (email.isEmpty()) { setError("Email address is required."); return null; }
        if (!email.matches("^[\\w._%+\\-]+@[\\w.\\-]+\\.[a-zA-Z]{2,}$")) {
            setError("Please enter a valid email address."); return null;
        }

        return new BookingRequest(visitorId, park.getId(), park.getName(),
            date.format(DATE_FMT), time, visitors, orderType, email);
    }

    /**
     * offers to join the waiting list when the slot is full. sends {@code JOIN_WAITING_LIST}
     * and navigates to the confirmation screen if the user accepts.
     *
     * @param req the original booking request, reused as-is for the waiting-list entry
     */
    private void offerWaitingList(BookingRequest req) {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle("Park Fully Booked");
        alert.setHeaderText(req.getParkName() + " has no available spots\n" +
            "on " + req.getVisitDate() + " at " + req.getVisitTime() + ".");
        alert.setContentText(
            "Would you like to join the waiting list?\n" +
            "You'll be notified at " + req.getEmail() + " if a spot opens up.");
        ButtonType joinBtn   = new ButtonType("Join Waiting List", ButtonBar.ButtonData.YES);
        ButtonType cancelBtn = new ButtonType("Cancel",            ButtonBar.ButtonData.NO);
        alert.getButtonTypes().setAll(joinBtn, cancelBtn);
        Optional<ButtonType> response = alert.showAndWait();
        if (response.isPresent() && response.get() == joinBtn) {
            ChatClient.lastBookingResult = null;
            ChatClient.lastBookingError  = null;
            ClientUI.chat.accept(new Message("JOIN_WAITING_LIST", req));
            if (ChatClient.lastBookingResult != null) {
                showConfirmation(ChatClient.lastBookingResult);
            } else {
                setError(ChatClient.lastBookingError != null
                    ? ChatClient.lastBookingError
                    : "Could not join waiting list. Please try again.");
            }
        }
    }

    /**
     * pushes a simulated notification to {@link NotificationCenter}, then navigates
     * to the confirmation screen. a day-before reminder is also added automatically
     * when the booked visit falls tomorrow.
     *
     * @param result the booking or waiting-list confirmation from the server
     */
    private void showConfirmation(BookingResult result) {
        // simulate the email/SMS notification the visitor would receive
        if ("WAITING".equals(result.getStatus())) {
            NotificationCenter.add("⏳",
                "Added to waiting list — " + result.getParkName()
                + " on " + result.getVisitDate() + " at " + result.getVisitTime()
                + ". You'll be notified at " + result.getEmail() + " if a spot opens.");
        } else {
            NotificationCenter.add("✅",
                "Booking confirmed — " + result.getParkName()
                + " on " + result.getVisitDate() + " at " + result.getVisitTime()
                + " · " + result.getNumVisitors() + " visitor(s)."
                + " Confirmation email sent to " + result.getEmail() + ".");
            // add a reminder if the visit is tomorrow
            String tomorrow = LocalDate.now().plusDays(1).format(DATE_FMT);
            if (result.getVisitDate().equals(tomorrow)) {
                NotificationCenter.add("🔔",
                    "Reminder: your visit to " + result.getParkName()
                    + " is tomorrow at " + result.getVisitTime()
                    + " (Order #" + result.getId() + "). Please arrive 15 minutes early.");
            }
        }

        try {
            // Use the FXMLLoader instance API so we can call populate() on the controller
            // *after* load() has finished injecting all @FXML fields.  The static-field
            // hand-off pattern (pendingResult = result before FXMLLoader.load()) is
            // unreliable because the static convenience FXMLLoader.load(URL) may resolve
            // the controller class through a different ClassLoader than the one that owns
            // the static field, leaving pendingResult null inside initialize().
            FXMLLoader loader = new FXMLLoader(
                getClass().getResource("/gui/BookingConfirmationFrame.fxml"));
            Parent root = loader.load();                             // @FXML fields injected here
            BookingConfirmationController ctrl = loader.getController();

            Stage stage = (Stage) btnSubmit.getScene().getWindow();
            stage.setTitle("GoNature — " +
                ("WAITING".equals(result.getStatus()) ? "Waiting List" : "Booking Confirmed"));
            stage.setScene(new Scene(root));                         // attach to live scene first
            ctrl.populate(result);                                   // then data-bind (labels now in live scene)
        } catch (IOException e) {
            setError("Error showing confirmation: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /** @param event the button-click event */
    @FXML
    public void handleCancel(ActionEvent event) {
        ((Stage) btnSubmit.getScene().getWindow()).close();
    }

    /** @param msg the error text to display */
    private void setError(String msg) { lblError.setText(msg); }

    /**
     * @param stage the stage in which to show the form
     * @throws Exception if the FXML file cannot be loaded
     */
    public void start(Stage stage) throws Exception {
        Parent root = FXMLLoader.load(getClass().getResource("/gui/BookingFrame.fxml"));
        stage.setTitle("GoNature — New Booking");
        stage.setScene(new Scene(root));
        stage.show();
    }
}
