package gui;

import client.ChatClient;
import client.ClientUI;
import logic.Message;
import logic.Promotion;
import logic.UserSession;
import javafx.beans.property.SimpleStringProperty;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.stage.Stage;

import java.time.LocalDate;
import java.util.List;

/**
 * controller for the park manager's Promotions panel ({@code PromotionsFrame.fxml}).
 *
 * <p>lets a PARK_MANAGER propose a discount promotion for their assigned park. Promotions
 * are stored as {@code PENDING} and only affect pricing once a department manager approves
 * them. The table shows the manager's own park promotions and their current status.
 */
public class PromotionsController {

    @FXML private Button     btnTheme;
    @FXML private Label      lblParkName;
    @FXML private TextField  txtDescription;
    @FXML private TextField  txtDiscount;
    @FXML private DatePicker dpStart;
    @FXML private DatePicker dpEnd;
    @FXML private Label      lblStatus;

    @FXML private TableView<Promotion>         tablePromotions;
    @FXML private TableColumn<Promotion, String> colDescription;
    @FXML private TableColumn<Promotion, String> colDiscount;
    @FXML private TableColumn<Promotion, String> colDates;
    @FXML private TableColumn<Promotion, String> colStatus;

    /**
     * wires the theme toggle, sets the park-name header, configures the table columns
     * (including a themed status chip), and loads the park's existing promotions.
     */
    @FXML
    public void initialize() {
        ThemeManager.installToggle(btnTheme);

        UserSession s = UserSession.getInstance();
        if (s != null) {
            String name = (s.getParkName() != null && !s.getParkName().isEmpty())
                ? s.getParkName()
                : (s.getParkId() != null ? "Park #" + s.getParkId() : "your park");
            lblParkName.setText("Promotions — " + name);
        }

        colDescription.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().getDescription()));
        colDiscount.setCellValueFactory(   d -> new SimpleStringProperty(d.getValue().getDiscountLabel()));
        colDates.setCellValueFactory(      d -> new SimpleStringProperty(d.getValue().getDateRange()));
        colStatus.setCellValueFactory(     d -> new SimpleStringProperty(d.getValue().getStatus()));
        colStatus.setCellFactory(col -> new TableCell<Promotion, String>() {
            @Override protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) { setText(null); setGraphic(null); return; }
                Label chip = new Label(item);
                chip.getStyleClass().add(chipClassFor(item));
                setText(null);
                setGraphic(chip);
            }
        });

        tablePromotions.setPlaceholder(EmptyState.of(Icons.inbox(34),
            "No promotions yet",
            "Create a promotion above — it will appear here once submitted."));

        loadPromotions();
    }

    /** maps a promotion status to its themed chip CSS class. */
    private String chipClassFor(String status) {
        switch (status == null ? "" : status.toUpperCase()) {
            case "ACTIVE":   return "chip-confirmed";
            case "REJECTED": return "chip-cancelled";
            default:         return "chip-pending"; // PENDING
        }
    }

    /**
     * validates the form and sends {@code SUBMIT_PROMOTION} to queue the promotion for
     * department manager approval. Live pricing is NOT affected until the promotion is approved.
     *
     * @param event the button-click event
     */
    @FXML
    public void handleSubmit(ActionEvent event) {
        lblStatus.setText("");
        lblStatus.setStyle("");

        UserSession s = UserSession.getInstance();
        if (s == null || s.getParkId() == null) {
            setError("No park assigned to your account.");
            return;
        }

        String description = txtDescription.getText() != null ? txtDescription.getText().trim() : "";
        if (description.isEmpty()) { setError("Please enter a promotion description."); return; }
        if (description.length() > 255) { setError("Description must be 255 characters or fewer."); return; }

        double discount;
        try {
            discount = Double.parseDouble(txtDiscount.getText().trim());
        } catch (NumberFormatException e) {
            setError("Discount must be a number between 1 and 50."); return;
        }
        if (discount < 1 || discount > 50) {
            setError("Discount must be between 1 and 50 percent."); return;
        }

        LocalDate start = dpStart.getValue();
        LocalDate end   = dpEnd.getValue();
        if (start == null || end == null) {
            setError("Please choose both a start and an end date."); return;
        }
        if (end.isBefore(start)) {
            setError("End date must be on or after the start date."); return;
        }
        if (end.isBefore(LocalDate.now())) {
            setError("The end date is in the past — choose a current or future date."); return;
        }

        String parkName = (s.getParkName() != null) ? s.getParkName() : "";
        Promotion promo = new Promotion(
            s.getParkId(), parkName, description, discount,
            start.toString(), end.toString(), s.getUserId());

        ChatClient.lastPromotionSubmitSuccess = null;
        ChatClient.lastPromotionSubmitFail    = null;
        ClientUI.chat.accept(new Message("SUBMIT_PROMOTION", promo));

        if (ChatClient.lastPromotionSubmitSuccess != null) {
            setSuccess(ChatClient.lastPromotionSubmitSuccess);
            txtDescription.clear();
            txtDiscount.clear();
            dpStart.setValue(null);
            dpEnd.setValue(null);
            loadPromotions();
        } else {
            setError(ChatClient.lastPromotionSubmitFail != null
                ? ChatClient.lastPromotionSubmitFail
                : "Submission failed. Please try again.");
        }
    }

    /** @param event the button-click event */
    @FXML
    public void handleRefresh(ActionEvent event) {
        loadPromotions();
    }

    /** fetches this park's promotions from the server and repopulates the table. */
    private void loadPromotions() {
        UserSession s = UserSession.getInstance();
        if (s == null || s.getParkId() == null) return;
        ChatClient.lastParkPromotions = null;
        ClientUI.chat.accept(new Message("GET_PROMOTIONS_FOR_PARK", s.getParkId()));
        List<Promotion> promos = ChatClient.lastParkPromotions;
        if (promos != null) {
            tablePromotions.getItems().setAll(promos);
        } else {
            tablePromotions.getItems().clear();
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
        Parent root = FXMLLoader.load(getClass().getResource("/gui/PromotionsFrame.fxml"));
        stage.setTitle("GoNature — Promotions");
        Scene scene = new Scene(root);
        ThemeManager.register(scene);
        stage.setScene(scene);
        stage.show();
    }
}
