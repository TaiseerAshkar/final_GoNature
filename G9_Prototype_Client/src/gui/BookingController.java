package gui;

import client.ChatClient;
import client.ClientUI;
import client.NotificationCenter;
import logic.BookingRequest;
import logic.BookingResult;
import logic.Message;
import logic.Park;
import client.UserSession;
import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import java.io.IOException;
import java.util.concurrent.atomic.AtomicBoolean;
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
    @FXML private Label            lblAvailability;
    @FXML private TextField        txtVisitorId;
    @FXML private TextField        txtEmail;
    @FXML private Label            lblPriceEstimate;
    @FXML private Label            lblError;
    @FXML private Button           btnSubmit;
    @FXML private Button           btnTheme;

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    /** prevents concurrent GET_AVAILABLE_SPOTS requests when the user picks values quickly. */
    private final AtomicBoolean availCheckInFlight = new AtomicBoolean(false);

    /**
     * when {@code true}, the date/time change listeners skip the background availability
     * check. set while the form is being populated programmatically (e.g. applying an
     * alternative slot) so the availability round-trip does not run concurrently with a
     * BOOK_ORDER request — the two would race on the shared {@code awaitResponse} flag in
     * {@link ChatClient} and clobber each other's results.
     */
    private boolean suppressAvailabilityCheck = false;

    /**
     * loads parks, populates combo-boxes, pre-fills visitor ID/email from session,
     * and wires change listeners for the live price estimate.
     */
    @FXML
    public void initialize() {
        ThemeManager.installToggle(btnTheme);
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

    /** hourly slots 08:00-16:00 to match the park's operating hours. */
    private void setupTimeSlots() {
        cboTime.getItems().addAll(
            "08:00", "09:00", "10:00", "11:00", "12:00",
            "13:00", "14:00", "15:00", "16:00");
    }

    /** sets up the three visit types and applies the initial hint for SOLO. */
    private void setupOrderTypes() {
        cboOrderType.getItems().addAll("SOLO", "FAMILY", "GROUP");
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
        cboPark.valueProperty().addListener((obs, old, val) -> {
            updatePriceEstimate();
            updateAvailability();
        });
        txtVisitors.textProperty().addListener((obs, old, val) -> updatePriceEstimate());
        // refresh the live-availability badge whenever park, date, or time changes
        dpDate.valueProperty().addListener((obs, old, val) -> updateAvailability());
        cboTime.valueProperty().addListener((obs, old, val) -> updateAvailability());
    }

    /**
     * queries the server for free slots once park, date, and time are all chosen, then
     * shows "Available spots: X of Y" in green, or a full-slot notice in orange.
     * stays blank until the visitor has picked all three fields.
     *
     * <p>runs the network call on a short-lived daemon thread so the JavaFX Application
     * Thread is never blocked. an {@link AtomicBoolean} guard skips the request if a
     * previous one is still in-flight (prevents concurrent ChatClient calls).
     */
    private void updateAvailability() {
        if (lblAvailability == null) return;
        // skip while the form is being filled programmatically (alternative-slot apply),
        // so this network call doesn't race with the BOOK_ORDER request on the same wire
        if (suppressAvailabilityCheck) return;
        Park   park = cboPark.getValue();
        LocalDate date = dpDate.getValue();
        String time = cboTime.getValue();
        if (park == null || date == null || time == null || time.isEmpty()) {
            lblAvailability.setText("");
            return;
        }
        // Skip if a check is already running; the result from the previous pick
        // will arrive and update the label momentarily.
        if (!availCheckInFlight.compareAndSet(false, true)) return;

        final String parkIdStr = String.valueOf(park.getId());
        final String dateStr   = date.format(DATE_FMT);
        // Pre-booking availability is measured against max_orders (the booking cap that
        // leaves headroom for gate walk-ins), not the physical capacity.
        final int    capacity  = park.getMaxOrders();

        lblAvailability.getStyleClass().setAll("lbl-loading");
        lblAvailability.setStyle("");
        lblAvailability.setText("Checking availability…");

        Thread worker = new Thread(() -> {
            try {
                ChatClient.lastAvailableSpots = null;
                ClientUI.chat.accept(new Message("GET_AVAILABLE_SPOTS",
                    new String[]{ parkIdStr, dateStr, time }));
                final Integer available = ChatClient.lastAvailableSpots;

                Platform.runLater(() -> {
                    if (available == null) {
                        // null = server never responded (15-second timeout or network error)
                        lblAvailability.getStyleClass().setAll("lbl-loading");
                        lblAvailability.setStyle("");
                        lblAvailability.setText("Couldn't check availability right now.");
                    } else if (available <= 0) {
                        // 0  = park is exactly full
                        // <0 = slight overbooking edge case, still treat as full
                        lblAvailability.getStyleClass().setAll("lbl-warning");
                        lblAvailability.setStyle("");
                        lblAvailability.setText(
                            "This slot is fully booked — you may join the waiting list.");
                    } else {
                        lblAvailability.getStyleClass().setAll("lbl-success");
                        lblAvailability.setStyle("");
                        lblAvailability.setText(
                            "Available spots: " + available + " of " + capacity);
                    }
                });
            } finally {
                availCheckInFlight.set(false); // release guard so the next change can fire
            }
        }, "availability-check");
        worker.setDaemon(true);
        worker.start();
    }

    /**
     * locks or unlocks the visitor-count field and updates the hint based on visit type.
     * SOLO is fixed at 1; FAMILY allows 2-15 for anyone; GROUP is for registered guides (2-16).
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
            case "FAMILY":
                lblVisitorHint.setText("2–15 visitors — 15% family discount");
                txtVisitors.setDisable(false);
                break;
            case "GROUP":
                lblVisitorHint.setText("2–16 total (guide enters free · up to 15 guests · 25%+12% advance discount)");
                txtVisitors.setDisable(false);
                break;
        }
    }

    /**
     * recomputes the price estimate live. clears the label if inputs are incomplete.
     * SOLO:   1 × fullPrice × 0.85 (15% pre-booked discount).
     * FAMILY: visitors × fullPrice × 0.85 (15% family discount).
     * GROUP:  (visitors − 1) × fullPrice × 0.75 × 0.88 (guide free + 25% group + 12% advance).
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
                // pre-booked group: guide free; 25% group + 12% advance stacked (0.75 × 0.88)
                int paying = Math.max(0, visitors - 1); // guide enters free
                total = paying * park.getFullPrice() * 0.75 * 0.88;
                note  = String.format(" (guide free · %d paying · 25%%+12%% advance discount)", paying);
            } else if ("FAMILY".equals(type)) {
                total = visitors * park.getFullPrice() * 0.85;
                note  = " (15% family discount)";
            } else {
                // SOLO
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
     * SOLO   = exactly 1 visitor, any role.
     * FAMILY = 2-15 visitors, any role.
     * GROUP  = 2-16 total, registered guides only (guide enters free at check-in).
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
                    setError("SOLO bookings allow exactly 1 visitor."); return null;
                }
                break;
            case "FAMILY":
                if (visitors < 2 || visitors > 15) {
                    setError("FAMILY bookings require 2–15 visitors."); return null;
                }
                break;
            case "GROUP":
                UserSession gs2 = UserSession.getInstance();
                boolean isGuideBooking = gs2 != null && "GUIDE".equals(gs2.getRole());
                if (!isGuideBooking) {
                    setError("GROUP bookings are for registered guides only. Use FAMILY for group visits."); return null;
                }
                // guide books the full headcount (themselves + up to 15 guests = max 16 total)
                if (visitors < 2 || visitors > 16) {
                    setError("Guide GROUP bookings require 2–16 visitors total (you enter free)."); return null;
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
     * offers to join the waiting list or book an alternative slot when the park is full.
     *
     * <p>fetches up to 3 nearby available slots from the server (same-day different time,
     * then next-day same time). if alternatives exist a third "Book Alternative" button
     * is shown alongside "Join Waiting List" and "Cancel". choosing "Book Alternative"
     * opens a choice dialog so the visitor can pick a slot, then immediately re-submits
     * the booking for that slot without leaving the form.
     *
     * @param req the original booking request, reused for waiting-list entry or as a
     *            template for alternative bookings
     */
    private void offerWaitingList(BookingRequest req) {
        // fetch alternative available slots from the server
        ChatClient.lastAlternativeSlots = null;
        ClientUI.chat.accept(new Message("GET_ALTERNATIVE_SLOTS",
            new Object[]{ req.getParkId(), req.getVisitDate(),
                          req.getVisitTime(), req.getNumVisitors(),
                          req.getVisitorId() }));
        List<String> alts = ChatClient.lastAlternativeSlots;
        boolean hasAlts = alts != null && !alts.isEmpty();

        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle("Park Fully Booked");
        alert.setHeaderText(req.getParkName() + " is fully booked for this slot");

        // ── branded body: hourglass mark + pitch + booking-facts card ──────────
        Label pitch = new Label("Join the waiting list?");
        pitch.getStyleClass().add("card-title");
        Label pitchSub = new Label(
            "If a spot frees up you're booked automatically and notified by email.");
        pitchSub.getStyleClass().add("screen-subtitle");
        pitchSub.setWrapText(true);
        javafx.scene.layout.VBox pitchBox = new javafx.scene.layout.VBox(3, pitch, pitchSub);
        javafx.scene.layout.HBox top = new javafx.scene.layout.HBox(14,
            Icons.hourglass(36), pitchBox);
        top.setAlignment(javafx.geometry.Pos.CENTER_LEFT);

        javafx.scene.layout.GridPane facts = new javafx.scene.layout.GridPane();
        facts.setHgap(18);
        facts.setVgap(7);
        String[][] rows = {
            { "PARK",      req.getParkName() },
            { "DATE",      req.getVisitDate() + "  at  " + req.getVisitTime() },
            { "VISITORS",  String.valueOf(req.getNumVisitors()) },
            { "NOTIFY AT", req.getEmail() },
        };
        for (int i = 0; i < rows.length; i++) {
            Label k = new Label(rows[i][0]);
            k.getStyleClass().add("section-label");
            Label v = new Label(rows[i][1]);
            v.setStyle("-fx-font-weight: bold;");
            facts.add(k, 0, i);
            facts.add(v, 1, i);
        }
        javafx.scene.layout.VBox factsCard = new javafx.scene.layout.VBox(facts);
        factsCard.setPadding(new javafx.geometry.Insets(12, 16, 12, 16));
        factsCard.setStyle("-fx-background-color: -park-surface-2; -fx-background-radius: 10; "
            + "-fx-border-color: -park-line; -fx-border-radius: 10; -fx-border-width: 1;");

        javafx.scene.layout.VBox body = new javafx.scene.layout.VBox(14, top, factsCard);

        // ── alternative slots hint ───────────────────────────────────────────────
        if (hasAlts) {
            Label altHint = new Label("Available alternatives for " + req.getParkName() + ":");
            altHint.getStyleClass().add("section-label");
            javafx.scene.layout.VBox altList = new javafx.scene.layout.VBox(3);
            for (String alt : alts) {
                String[] parts = alt.split(" ");
                String display = (parts.length == 2)
                    ? parts[0] + "  at  " + parts[1]
                    : alt;
                Label lbl = new Label("  • " + display);
                lbl.setStyle("-fx-font-weight: bold;");
                altList.getChildren().add(lbl);
            }
            javafx.scene.layout.VBox altCard = new javafx.scene.layout.VBox(6, altHint, altList);
            altCard.setPadding(new javafx.geometry.Insets(10, 16, 10, 16));
            altCard.setStyle("-fx-background-color: -park-surface-2; -fx-background-radius: 10; "
                + "-fx-border-color: -park-line; -fx-border-radius: 10; -fx-border-width: 1;");
            body.getChildren().add(altCard);
        }

        body.setPadding(new javafx.geometry.Insets(6, 4, 4, 4));
        alert.getDialogPane().setContent(body);
        alert.getDialogPane().setPrefWidth(620);

        ButtonType joinBtn  = new ButtonType("Join Waiting List", ButtonBar.ButtonData.YES);
        ButtonType altBtn   = new ButtonType("Book Alternative",  ButtonBar.ButtonData.OTHER);
        ButtonType cancelBtn = new ButtonType("Cancel",           ButtonBar.ButtonData.NO);
        if (hasAlts) {
            alert.getButtonTypes().setAll(joinBtn, altBtn, cancelBtn);
        } else {
            alert.getButtonTypes().setAll(joinBtn, cancelBtn);
        }
        ThemeManager.styleDialog(alert);
        alert.getDialogPane().getStyleClass().add("help-adaptive");

        Optional<ButtonType> response = alert.showAndWait();
        if (!response.isPresent()) return;

        if (response.get() == joinBtn) {
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

        } else if (response.get() == altBtn && hasAlts) {
            // let the visitor pick which alternative slot to book
            ChoiceDialog<String> pick = new ChoiceDialog<>(alts.get(0), alts);
            pick.setTitle("Choose an Alternative Slot");
            pick.setHeaderText("Available slots at " + req.getParkName());
            pick.setContentText("Select a date and time:");
            ThemeManager.styleDialog(pick);
            pick.getDialogPane().getStyleClass().add("help-adaptive");
            Optional<String> chosen = pick.showAndWait();
            if (!chosen.isPresent()) return;

            // parse "yyyy-MM-dd HH:mm" and pre-fill the form fields, then re-submit
            String[] parts = chosen.get().split(" ");
            if (parts.length == 2) {
                try {
                    // Suppress the availability listener while we set the fields so its
                    // background network call can't run concurrently with BOOK_ORDER below;
                    // they share ChatClient.awaitResponse and would clobber each other.
                    suppressAvailabilityCheck = true;
                    dpDate.setValue(LocalDate.parse(parts[0], DATE_FMT));
                    cboTime.setValue(parts[1]);
                    suppressAvailabilityCheck = false;

                    // re-build the request with the new date/time and submit immediately
                    BookingRequest altReq = buildAndValidate();
                    if (altReq == null) return;   // validation message already shown
                    ChatClient.lastBookingResult = null;
                    ChatClient.lastBookingError  = null;
                    ClientUI.chat.accept(new Message("BOOK_ORDER", altReq));
                    if (ChatClient.lastBookingResult != null) {
                        showConfirmation(ChatClient.lastBookingResult);
                    } else if ("FULL".equals(ChatClient.lastBookingError)) {
                        offerWaitingList(altReq);
                    } else {
                        setError(ChatClient.lastBookingError != null
                            ? ChatClient.lastBookingError
                            : "Booking failed. Please try again.");
                    }
                } catch (Exception e) {
                    setError("Could not apply the selected slot. Please set the date and time manually.");
                } finally {
                    suppressAvailabilityCheck = false;  // always re-enable live checks
                }
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
            Scene scene = new Scene(root);
            ThemeManager.register(scene);                            // theme the confirmation screen
            stage.setScene(scene);                                   // attach to live scene first
            ctrl.populate(result);                                   // then data-bind (labels now in live scene)
        } catch (IOException e) {
            setError("Your booking went through, but we couldn't open the confirmation screen. "
                   + "You can view it any time under 'My Reservations'.");
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
        Scene scene = new Scene(root);
        ThemeManager.register(scene);
        stage.setScene(scene);
        stage.centerOnScreen();
        stage.show();
    }
}

