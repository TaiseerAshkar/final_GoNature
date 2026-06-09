package gui;

import client.ChatClient;
import client.ClientUI;
import javafx.application.Platform;
import logic.LoginResult;
import logic.Message;
import logic.UserSession;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.input.MouseEvent;
import javafx.stage.Stage;

/**
 * FXML controller for the login screen ({@code LoginFrame.fxml}).
 *
 * <p>single form: visitors and guides enter their ID and leave the password blank;
 * staff enter a username and password. the system identifies the user type automatically
 * after the server returns the role — no manual selection needed.
 */
public class LoginController {

    @FXML private TextField     txtIdentifier;
    @FXML private PasswordField txtPassword;
    @FXML private TextField     txtPasswordVisible;
    @FXML private CheckBox      chkShowPassword;
    @FXML private Label         lblError;
    @FXML private Button        btnLogin;

    /**
     * routes to visitor or staff login based on whether a password was supplied.
     * visitors leave the password blank; staff always supply one.
     * reads from the visible TextField when "Show password" is checked,
     * otherwise reads from the PasswordField.
     *
     * @param event the button-click event
     */
    @FXML
    public void handleLogin(ActionEvent event) {
        lblError.setText("");
        String identifier = txtIdentifier.getText().trim();
        String password   = chkShowPassword.isSelected()
                            ? txtPasswordVisible.getText()
                            : txtPassword.getText();

        if (identifier.isEmpty()) {
            setError("Please enter your ID number or username to sign in.");
            return;
        }

        if (password.isEmpty()) {
            loginAsVisitor(identifier);
        } else {
            loginAsStaff(identifier, password);
        }
    }

    /**
     * toggles the password field between masked ({@link PasswordField}) and
     * plain-text ({@link TextField}).
     * syncs the text content so the value is preserved across toggles.
     *
     * @param event the checkbox action event
     */
    @FXML
    public void handleShowPassword(ActionEvent event) {
        if (chkShowPassword.isSelected()) {
            // Copy masked text into the plain field and swap visibility
            txtPasswordVisible.setText(txtPassword.getText());
            txtPassword.setVisible(false);
            txtPassword.setManaged(false);
            txtPasswordVisible.setVisible(true);
            txtPasswordVisible.setManaged(true);
            txtPasswordVisible.requestFocus();
            txtPasswordVisible.positionCaret(txtPasswordVisible.getText().length());
        } else {
            // Copy plain text back into the password field and swap back
            txtPassword.setText(txtPasswordVisible.getText());
            txtPasswordVisible.setVisible(false);
            txtPasswordVisible.setManaged(false);
            txtPassword.setVisible(true);
            txtPassword.setManaged(true);
            txtPassword.requestFocus();
            txtPassword.positionCaret(txtPassword.getText().length());
        }
    }

    /**
     * validates the ID format and sends a {@code LOGIN_VISITOR} message.
     * the server checks guides first, then visitors, so the role is determined automatically.
     *
     * @param id the raw identifier entered by the user
     */
    private void loginAsVisitor(String id) {
        if (!id.matches("\\d{5,15}")) {
            setError("If you're a staff member, please enter your password too. "
                   + "Visitors should enter a 5–15 digit ID number.");
            return;
        }
        ChatClient.lastLoginResult = null;
        ChatClient.lastLoginError  = null;
        ClientUI.chat.accept(new Message("LOGIN_VISITOR", id));
        if (ChatClient.lastLoginResult != null) onLoginSuccess();
        else setError(ChatClient.lastLoginError != null
                ? ChatClient.lastLoginError
                : "That ID number isn't registered. Please check it, or create an account to get started.");
    }

    /**
     * sends a {@code LOGIN_USER} message with the supplied credentials.
     *
     * @param username the username entered by the user
     * @param password the password entered by the user
     */
    private void loginAsStaff(String username, String password) {
        ChatClient.lastLoginResult = null;
        ChatClient.lastLoginError  = null;
        ClientUI.chat.accept(new Message("LOGIN_USER", new String[]{username, password}));
        if (ChatClient.lastLoginResult != null) onLoginSuccess();
        else setError(ChatClient.lastLoginError != null
                ? ChatClient.lastLoginError
                : "Incorrect password");
    }

    /**
     * stores the login result in {@link UserSession} and routes to the role-appropriate home screen.
     */
    private void onLoginSuccess() {
        LoginResult result = ChatClient.lastLoginResult;
        UserSession.set(result);
        Stage stage = (Stage) btnLogin.getScene().getWindow();
        try {
            switch (result.getRole()) {
                case "VISITOR":
                case "GUIDE":
                case "SUBSCRIBER":
                    new VisitorController().start(stage);
                    break;
                case "PARK_EMPLOYEE":
                case "PARK_MANAGER":
                    new EmployeeController().start(stage);
                    break;
                case "DEPARTMENT_MANAGER":
                    new AcademicFrameController().start(stage);
                    break;
                case "SERVICE_REP":
                    new ServiceRepController().start(stage);
                    break;
                default:
                    setError("Your account role isn't recognised. Please contact support for help.");
            }
        } catch (Exception e) {
            setError("Something went wrong while opening your home screen. Please try signing in again.");
            e.printStackTrace();
        }
    }

    /**
     * disconnects cleanly and exits the application.
     *
     * @param event the button-click event
     */
    @FXML
    public void handleExit(ActionEvent event) {
        if (ClientUI.chat != null) ClientUI.chat.disconnect();
        Platform.exit();
    }

    /**
     * opens the registration screen on the same stage.
     *
     * @param event the button-click event
     */
    @FXML
    public void handleSignUp(ActionEvent event) {
        try {
            new RegisterController().start((Stage) btnLogin.getScene().getWindow());
        } catch (Exception e) {
            setError("We couldn't open the registration form. Please try again.");
        }
    }

    /**
     * shows the login-screen help dialog.
     *
     * @param event the mouse-click event
     */
    @FXML
    public void handleHelp(MouseEvent event) {
        showHelp("GoNature — Help",
            "Welcome to GoNature — National Park Reservation System\n\n" +
            "VISITOR / GUIDE LOGIN\n" +
            "  • Enter your government ID number (5–15 digits).\n" +
            "  • Leave the password field blank.\n" +
            "  • Guides are recognised automatically by their registered ID.\n\n" +
            "STAFF LOGIN\n" +
            "  • Enter your assigned username and password.\n" +
            "  • The system identifies your role automatically after login.\n\n" +
            "NEW USER?\n" +
            "  • Click 'Create an Account' to register as a visitor.\n" +
            "  • You can also join the Family Member Club during registration.");
    }

    /**
     * shows a scrollable help popup.
     *
     * @param title   the dialog title
     * @param content the help text to display
     */
    private void showHelp(String title, String content) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("Help");
        alert.setHeaderText(title);
        TextArea ta = new TextArea(content);
        ta.setEditable(false);
        ta.setWrapText(true);
        ta.setPrefRowCount(14);
        ta.setPrefWidth(440);
        ta.setStyle("-fx-font-family: 'Segoe UI', Arial, sans-serif; -fx-font-size: 12px;");
        alert.getDialogPane().setContent(ta);
        alert.getDialogPane().setMinWidth(480);
        alert.showAndWait();
    }

    /** @param msg the error text to display */
    private void setError(String msg) { lblError.setText(msg); }

    /**
     * @param primaryStage the stage in which to show the login screen
     * @throws Exception if the FXML file cannot be loaded
     */
    public void start(Stage primaryStage) throws Exception {
        Parent root = FXMLLoader.load(getClass().getResource("/gui/LoginFrame.fxml"));
        primaryStage.setTitle("GoNature — Login");
        Scene scene = new Scene(root);
        ThemeManager.register(scene);   // keep the chosen light/dark theme across logout
        primaryStage.setScene(scene);
        primaryStage.show();
    }
}
