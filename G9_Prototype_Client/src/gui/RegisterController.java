package gui;

import client.ChatClient;
import client.ClientUI;
import logic.Message;
import logic.RegisterRequest;
import logic.RegisterResult;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

/**
 * FXML controller for the visitor registration screen ({@code RegisterFrame.fxml}).
 *
 * <p>collects personal details and optionally subscriber info (family size, credit card).
 * client-side validation mirrors server rules so errors are shown before any network round-trip.
 * on success, shows a confirmation popup and navigates back to the login screen.
 */
public class RegisterController {

    @FXML private TextField txtIdNumber;
    @FXML private TextField txtFirstName;
    @FXML private TextField txtLastName;
    @FXML private TextField txtEmail;
    @FXML private TextField txtPhone;
    @FXML private CheckBox  chkSubscriber;
    @FXML private VBox      subscriberFields;
    @FXML private TextField txtFamilySize;
    @FXML private TextField txtCreditCard;
    @FXML private Label     lblError;

    /**
     * shows/hides the subscriber fields when the Family Member Club checkbox is toggled.
     *
     * @param event the checkbox action event
     */
    @FXML
    public void onSubscriberToggle(ActionEvent event) {
        boolean checked = chkSubscriber.isSelected();
        subscriberFields.setVisible(checked);
        subscriberFields.setManaged(checked);
        lblError.setText("");
    }

    /**
     * validates the form, sends {@code REGISTER_VISITOR}, then shows a success popup or an inline error.
     *
     * @param event the button-click event
     */
    @FXML
    public void handleRegister(ActionEvent event) {
        lblError.setText("");

        // ── Personal information validation ──────────────────────────────────
        String idNumber = txtIdNumber.getText().trim();
        if (idNumber.isEmpty())           { setError("ID number is required.");         return; }
        if (!idNumber.matches("\\d{5,15}")) { setError("ID number must be 5–15 digits."); return; }

        String firstName = txtFirstName.getText().trim();
        if (firstName.isEmpty()) { setError("First name is required."); return; }
        if (firstName.length() > 50) { setError("First name must be 50 characters or fewer."); return; }

        String lastName = txtLastName.getText().trim();
        if (lastName.isEmpty()) { setError("Last name is required."); return; }
        if (lastName.length() > 50) { setError("Last name must be 50 characters or fewer."); return; }

        String email = txtEmail.getText().trim();
        if (email.isEmpty()) { setError("Email address is required."); return; }
        if (!email.matches("^[\\w._%+\\-]+@[\\w.\\-]+\\.[a-zA-Z]{2,}$")) {
            setError("Please enter a valid email address."); return;
        }

        String phone = txtPhone.getText().trim();
        if (phone.isEmpty())             { setError("Phone number is required.");         return; }
        if (!phone.matches("\\d{9,15}")) { setError("Phone must be 9–15 digits only.");   return; }

        // ── Subscriber validation ────────────────────────────────────────────
        boolean subscriber = chkSubscriber.isSelected();
        String  creditCard = null;
        int     familySize = 0;

        if (subscriber) {
            String fs = txtFamilySize.getText().trim();
            if (fs.isEmpty()) { setError("Please enter the family size."); return; }
            try {
                familySize = Integer.parseInt(fs);
                if (familySize < 1 || familySize > 10) {
                    setError("Family size must be between 1 and 10."); return;
                }
            } catch (NumberFormatException e) {
                setError("Family size must be a whole number."); return;
            }

            String cc = txtCreditCard.getText().trim();
            if (!cc.isEmpty()) {
                if (!cc.matches("\\d{16}")) {
                    setError("Credit card must be exactly 16 digits (or leave it blank)."); return;
                }
                creditCard = cc;
            }
        }

        // ── Send to server ───────────────────────────────────────────────────
        RegisterRequest req = new RegisterRequest(
            idNumber, firstName, lastName, email, phone,
            subscriber, creditCard, familySize);

        ChatClient.lastRegisterResult = null;
        ChatClient.lastRegisterError  = null;
        ClientUI.chat.accept(new Message("REGISTER_VISITOR", req));

        RegisterResult result = ChatClient.lastRegisterResult;
        if (result != null && result.isSuccess()) {
            showSuccessAndReturn(result, idNumber);
        } else {
            setError(ChatClient.lastRegisterError != null
                ? ChatClient.lastRegisterError
                : "Registration failed. Please check your details and try again.");
        }
    }

    /**
     * shows a success alert with the new subscriber ID (if any), then navigates back to login.
     *
     * @param result   the successful registration result from the server
     * @param idNumber the visitor's ID number (used in the success message)
     */
    private void showSuccessAndReturn(RegisterResult result, String idNumber) {
        StringBuilder msg = new StringBuilder();
        msg.append("Your account has been created successfully!\n\n");
        msg.append("Your ID number: ").append(idNumber).append("\n");
        if (result.getSubscriberId() != null) {
            msg.append("Subscriber ID:  ").append(result.getSubscriberId()).append("\n");
            msg.append("\nPlease save your subscriber ID — you will need it\n")
               .append("when booking as a Family Member Club subscriber.");
        }
        msg.append("\n\nYou can now log in with your ID number.");

        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("Registration Successful");
        alert.setHeaderText("Welcome to GoNature!");
        alert.setContentText(msg.toString());
        alert.showAndWait();

        navigateToLogin();
    }

    /** navigates back to the login screen. */
    private void navigateToLogin() {
        try {
            new LoginController().start((Stage) lblError.getScene().getWindow());
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * cancels registration and returns to the login screen.
     *
     * @param event the button-click event
     */
    @FXML
    public void handleCancel(ActionEvent event) {
        navigateToLogin();
    }

    /** @param msg the error text to display */
    private void setError(String msg) { lblError.setText(msg); }

    /**
     * @param stage the stage in which to show the form
     * @throws Exception if the FXML file cannot be loaded
     */
    public void start(Stage stage) throws Exception {
        Parent root = FXMLLoader.load(getClass().getResource("/gui/RegisterFrame.fxml"));
        stage.setTitle("GoNature — Create Account");
        stage.setScene(new Scene(root));
        stage.show();
    }
}
