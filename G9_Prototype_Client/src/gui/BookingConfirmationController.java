package gui;

import logic.BookingResult;
import client.UserSession;
import javafx.animation.PauseTransition;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.canvas.Canvas;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import javafx.util.Duration;

/**
 * controller for the booking confirmation screen ({@code BookingConfirmationFrame.fxml}).
 *
 * <p>{@link BookingController} uses the {@link javafx.fxml.FXMLLoader} instance API to load
 * this screen, then calls {@link #populate(BookingResult)} directly on the controller so that
 * all {@code @FXML}-injected fields are guaranteed to be set before any data-binding runs.
 * for confirmed bookings the entry-pass card is shown immediately. the card is hidden for
 * waiting-list entries.
 */
public class BookingConfirmationController {

    @FXML private Button btnTheme;
    @FXML private VBox  bannerBox;
    @FXML private Label lblStatusIcon;
    @FXML private Label lblStatusTitle;
    @FXML private Label lblBookingId;

    // entry pass, only shown for CONFIRMED bookings
    @FXML private VBox  paneEntryPass;
    @FXML private VBox  paneQR;
    @FXML private Label lblEntryCode;

    @FXML private Label lblPark;
    @FXML private Label lblDate;
    @FXML private Label lblTime;
    @FXML private Label lblVisitors;
    @FXML private Label lblType;
    @FXML private Label lblEmail;
    @FXML private Label lblEmailNote;

    // promotion price card, only shown when an active promotion was applied
    @FXML private VBox  panePromo;
    @FXML private Label lblPromoTag;
    @FXML private Label lblPromoDesc;
    @FXML private Label lblPriceOriginal;
    @FXML private Label lblPriceFinal;

    /**
     * called by the FXMLLoader after all {@code @FXML} fields have been injected.
     * nothing to do here; all data-binding is done in {@link #populate(BookingResult)}
     * which is called explicitly by {@link BookingController} after loading.
     */
    @FXML
    public void initialize() { ThemeManager.installToggle(btnTheme); }

    /**
     * binds the booking result to all detail labels and, for confirmed bookings,
     * generates the QR entry pass.  called by {@link BookingController} immediately
     * after {@code loader.load()} so every {@code @FXML} field is already injected.
     *
     * @param r the confirmed booking or waiting-list result; must not be {@code null}
     */
    public void populate(BookingResult r) {
        boolean isWaiting = "WAITING".equals(r.getStatus());
        if (isWaiting) {
            // swap the banner from the confirmed (pine) to the waiting (gold) style class
            bannerBox.getStyleClass().remove("banner-confirmed");
            if (!bannerBox.getStyleClass().contains("banner-waiting"))
                bannerBox.getStyleClass().add("banner-waiting");
            lblStatusIcon.setText("⏳");
            lblStatusTitle.setText("Added to Waiting List");
            lblBookingId.setText("Waiting List Reference: #" + r.getId());
            lblEmailNote.setText(
                "You are on the waiting list. We'll notify you at the email below " +
                "if a spot opens up — usually within 24 hours of the visit date.");
        } else {
            lblStatusIcon.setText("✓");
            lblStatusTitle.setText("Booking Confirmed!");
            lblBookingId.setText("Booking Reference: #" + r.getId());
            lblEmailNote.setText(
                "A confirmation email will be sent to the address below. " +
                "Please arrive 15 minutes before your scheduled time.");

            // generate the entry pass right now, not on button click
            generateEntryPass(r);
        }

        lblPark.setText(r.getParkName());
        lblDate.setText(r.getVisitDate());
        lblTime.setText(r.getVisitTime());
        lblVisitors.setText(r.getNumVisitors() + " visitor" + (r.getNumVisitors() > 1 ? "s" : ""));
        lblType.setText(r.getOrderType());
        lblEmail.setText(r.getEmail());

        // Tomorrow reminder popup — fires 1.5s after the confirmation screen appears
        if (!isWaiting) {
            String tomorrow = java.time.LocalDate.now().plusDays(1)
                .format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd"));
            if (r.getVisitDate().equals(tomorrow)) {
                PauseTransition pause = new PauseTransition(Duration.seconds(1.5));
                pause.setOnFinished(ev -> showTomorrowReminder(r));
                pause.play();
            }
        }

        // Promotion price card, only for confirmed bookings that carry an active promotion.
        if (!isWaiting && r.hasPromotion()) {
            lblPromoTag.setText(String.format("Promotion Applied: %.0f%% off", r.getPromoPercent()));
            lblPromoDesc.setText(r.getPromoDescription());
            lblPriceOriginal.setText(String.format("$%.2f", r.getBasePrice()));
            lblPriceFinal.setText(String.format("$%.2f", r.getFinalPrice()));
            panePromo.setVisible(true);
            panePromo.setManaged(true);
        } else {
            panePromo.setVisible(false);
            panePromo.setManaged(false);
        }
    }

    /**
     * builds the QR canvas for this booking and inserts it into the entry-pass card.
     * the pass encodes {@code GONATURE:ORDER=<id>:VISITOR=<visitorId>} and also shows
     * the entry number as plain text so the visitor can quote it verbally at the gate.
     *
     * @param r the confirmed booking result
     */
    private void generateEntryPass(BookingResult r) {
        UserSession s = UserSession.getInstance();
        String visitorId = (s != null && s.getUserId() != null) ? s.getUserId() : "—";
        String payload   = QRCodeUtil.buildPayload(r.getId(), visitorId);

        // draw the QR grid (8 px per module → 168 × 168 px total)
        Canvas qr = QRCodeUtil.generate(payload, 8);

        // thin black border, matches the style in the help dialog
        StackPane qrFrame = new StackPane(qr);
        qrFrame.setStyle("-fx-border-color: #1A1A1A; -fx-border-width: 2;"
                       + "-fx-background-color: white; -fx-padding: 6;");

        paneQR.getChildren().add(qrFrame);

        // entry reference shown as plain text below the QR so it can also be quoted verbally
        lblEntryCode.setText(
            "Entry #" + r.getId()
            + "   ·   " + r.getParkName()
            + "   ·   " + r.getVisitDate() + " at " + r.getVisitTime());

        paneEntryPass.setVisible(true);
        paneEntryPass.setManaged(true);
    }

    private void showTomorrowReminder(BookingResult r) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("Visit Reminder");
        alert.setHeaderText("⏰ Your visit is TOMORROW!");
        alert.setContentText(
            "Park:     " + r.getParkName() + "\n" +
            "Date:     " + r.getVisitDate() + " at " + r.getVisitTime() + "\n" +
            "Order #:  " + r.getId() + "\n\n" +
            "Please confirm your booking from your dashboard within 2 hours,\n" +
            "or it will be automatically cancelled.");
        ThemeManager.styleDialog(alert);
        alert.show();
    }

    /** @param event the button-click event */
    @FXML
    public void handleBookAnother(ActionEvent event) {
        Stage stage = (Stage) lblPark.getScene().getWindow();
        try {
            new BookingController().start(stage);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /** @param event the button-click event */
    @FXML
    public void handleDone(ActionEvent event) {
        ((Stage) lblPark.getScene().getWindow()).close();
    }
}

