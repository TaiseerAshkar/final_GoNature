package gui;

import client.ChatClient;
import client.ClientUI;
import logic.Message;
import logic.RegisterRequest;
import logic.RegisterResult;
import client.UserSession;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.stage.Modality;
import javafx.stage.Stage;

/**
 * FXML controller for the visitor registration screen ({@code RegisterFrame.fxml}).
 *
 * <p>collects a new visitor's personal details (ID, name, email, phone) and registers a
 * plain visitor account. subscriber (Family Member Club) sign-up is intentionally NOT here:
 * per the GoNature spec it is performed by a service representative, via
 * {@code ServiceRepController.handleRegisterSubscriber}. client-side validation mirrors the
 * server rules so errors are shown before any network round-trip. on success, shows a
 * confirmation popup and navigates back to the login screen.
 */
public class RegisterController {

    @FXML private TextField txtIdNumber;
    @FXML private TextField txtFirstName;
    @FXML private TextField txtLastName;
    @FXML private TextField txtEmail;
    @FXML private TextField txtPhone;
    @FXML private Label     lblError;
    @FXML private Button    btnTheme;

    /** wires the header dark-mode toggle and phone real-time validation. */
    @FXML
    public void initialize() {
        ThemeManager.installToggle(btnTheme);

        // Real-time phone validation: clear error once the field is valid, flag immediately if non-digit typed.
        txtPhone.textProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal.isEmpty()) return;
            if (!newVal.matches("\\d*")) {
                lblError.setText("Phone number must contain numbers only.");
            } else if (newVal.length() > 10) {
                // Strip the extra character so the field stays at 10 digits max.
                txtPhone.setText(oldVal);
            } else {
                lblError.setText("");
            }
        });
    }

    /**
     * validates the form, sends {@code REGISTER_VISITOR}, then shows a success popup or an inline error.
     *
     * @param event the button-click event
     */
    @FXML
    public void handleRegister(ActionEvent event) {
        lblError.setText("");

        // â”€â”€ Personal information validation â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
        String idNumber = txtIdNumber.getText().trim();
        if (idNumber.isEmpty())           { setError("ID number is required.");         return; }
        if (!idNumber.matches("\\d+"))  { setError("ID number must contain numbers only."); return; }
        if (idNumber.length() != 9)     { setError("ID number must be exactly 9 digits."); return; }

        String firstName = UserSession.capitalize(txtFirstName.getText().trim());
        if (firstName.isEmpty()) { setError("First name is required."); return; }
        if (!firstName.matches("[\\p{L} '-]+")) { setError("Name must contain letters only."); return; }
        if (firstName.length() > 50) { setError("First name must be 50 characters or fewer."); return; }

        String lastName = UserSession.capitalize(txtLastName.getText().trim());
        if (lastName.isEmpty()) { setError("Last name is required."); return; }
        if (!lastName.matches("[\\p{L} '-]+")) { setError("Name must contain letters only."); return; }
        if (lastName.length() > 50) { setError("Last name must be 50 characters or fewer."); return; }

        String email = txtEmail.getText().trim();
        if (email.isEmpty()) { setError("Email address is required."); return; }
        if (!email.matches("^[\\w._%+\\-]+@[\\w.\\-]+\\.[a-zA-Z]{2,}$")) {
            setError("Please enter a valid email address."); return;
        }

        String phone = txtPhone.getText().trim();
        if (phone.isEmpty())           { setError("Phone number is required.");                      return; }
        if (!phone.matches("\\d+"))    { setError("Phone number must contain numbers only.");        return; }
        if (phone.length() != 10)      { setError("Phone number must be exactly 10 digits.");        return; }

        // â”€â”€ Send to server â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
        // Plain visitor account only. Subscriber sign-up is handled by a service rep
        // (ServiceRepController.handleRegisterSubscriber), so these are always non-subscriber.
        RegisterRequest req = new RegisterRequest(
            idNumber, firstName, lastName, email, phone,
            false, null, 0);

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
        msg.append("\n\nYou can now log in with your ID number.");

        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("Registration Successful");
        alert.setHeaderText("Welcome to GoNature!");
        alert.setContentText(msg.toString());
        ThemeManager.styleDialog(alert);
        alert.showAndWait();

        closeOrReturn();
    }

    /** navigates back to the login screen, reusing the current window. */
    private void navigateToLogin() {
        try {
            new LoginController().start((Stage) lblError.getScene().getWindow());
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * leaves the registration form. when the form was opened in its own modal window
     * (e.g. a service rep registering a visitor from the desk) it simply closes that
     * window; when it was opened inside the main login window (the "Create an Account"
     * flow) it returns to the login screen.
     */
    private void closeOrReturn() {
        Stage stage = (Stage) lblError.getScene().getWindow();
        if (stage != null && stage.getModality() != Modality.NONE) {
            stage.close();        // standalone modal, just close it
        } else {
            navigateToLogin();    // opened in the login window, go back to login
        }
    }

    /**
     * cancels registration: closes the modal if opened standalone, otherwise
     * returns to the login screen.
     *
     * @param event the button-click event
     */
    @FXML
    public void handleCancel(ActionEvent event) {
        closeOrReturn();
    }

    /** @param msg the error text to display */
    private void setError(String msg) { lblError.setText(msg); }

    /**
     * @param stage the stage in which to show the form
     * @throws Exception if the FXML file cannot be loaded
     */
    public void start(Stage stage) throws Exception {
        Parent root = FXMLLoader.load(getClass().getResource("/gui/RegisterFrame.fxml"));
        stage.setTitle("GoNature â€” Create Account");
        Scene scene = new Scene(root);
        ThemeManager.register(scene);
        stage.setScene(scene);
        stage.centerOnScreen();
        stage.show();
    }
}

