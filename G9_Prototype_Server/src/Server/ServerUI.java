package Server;

import gui.ServerPortFrameController;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import java.util.Optional;

/**
 * JavaFX application entry point for the GoNature server.
 *
 * <p>shows a password dialog at startup so the DB password is never stored on disk.
 * {@link #runServer(String)} is called by the control panel and spins {@link EchoServer}
 * on a daemon thread.
 */
public class ServerUI extends Application {

    /** the default port used when no port is configured. */
    public static final int DEFAULT_PORT = 5555;

    /**
     * @param args command-line arguments (not used)
     * @throws Exception if the JavaFX runtime cannot be initialised
     */
    public static void main(String[] args) throws Exception {
        launch(args);
    }

    /**
     * shows the password dialog; exits if the operator cancels.
     * on confirmation, stores the password and opens the server control panel.
     *
     * @param primaryStage the primary window provided by the JavaFX runtime
     * @throws Exception if the control panel FXML cannot be loaded
     */
    @Override
    public void start(Stage primaryStage) throws Exception {
        Dialog<String> dialog = new Dialog<>();
        dialog.setTitle("Database Login");
        dialog.setHeaderText("Enter MySQL Password");

        PasswordField passwordField = new PasswordField();
        passwordField.setPromptText("Password");
        passwordField.getStyleClass().add("text-field");

        // plain text field shares the same binding — shown when "Show password" is ticked
        TextField plainField = new TextField();
        plainField.setPromptText("Password");
        plainField.getStyleClass().add("text-field");
        plainField.setVisible(false);
        plainField.setManaged(false);
        plainField.textProperty().bindBidirectional(passwordField.textProperty());

        CheckBox showPw = new CheckBox("Show password");
        showPw.setStyle("-fx-font-size: 12px; -fx-text-fill: -park-text-mid;");
        showPw.setOnAction(e -> {
            boolean show = showPw.isSelected();
            passwordField.setVisible(!show);
            passwordField.setManaged(!show);
            plainField.setVisible(show);
            plainField.setManaged(show);
        });

        Label pwLabel = new Label("MySQL Password:");
        pwLabel.getStyleClass().add("field-label");

        VBox content = new VBox(8, pwLabel, passwordField, plainField, showPw);
        content.setPadding(new Insets(10));
        dialog.getDialogPane().setContent(content);

        ButtonType connectBtn = new ButtonType("Connect", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(connectBtn, ButtonType.CANCEL);
        dialog.setResultConverter(btn -> btn == connectBtn ? passwordField.getText() : null);

        // theme the popup to match the server control panel (pine header, gold accent, themed buttons)
        DialogPane pane = dialog.getDialogPane();
        pane.setMinWidth(400);
        java.net.URL css = getClass().getResource("/gui/ServerPort.css");
        if (css != null) pane.getStylesheets().add(css.toExternalForm());
        pane.getStyleClass().add("help-dialog");

        Optional<String> result = dialog.showAndWait();
        if (!result.isPresent() || result.get() == null) {
            Platform.exit();
            return;
        }

        DBController.setPassword(result.get());

        ServerPortFrameController frame = new ServerPortFrameController();
        frame.start(primaryStage);
    }

    /**
     * parses the port string and starts {@link EchoServer} on a daemon thread.
     *
     * @param p the port number as a string (e.g., {@code "5555"})
     */
    public static void runServer(String p) {
        int port;
        try {
            port = Integer.parseInt(p);
        } catch (Throwable t) {
            System.out.println("ERROR - Could not parse port!");
            return;
        }
        final int finalPort = port;
        Thread t = new Thread(() -> {
            EchoServer sv = new EchoServer(finalPort);
            gui.ServerPortFrameController.setActiveServer(sv);
            try {
                sv.listen();
            } catch (Exception ex) {
                System.out.println("ERROR - Could not listen for clients! " + ex.getMessage());
            }
        });
        t.setDaemon(true);
        t.start();
    }
}
