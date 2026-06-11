package client;

import gui.LoginController;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.scene.control.TextInputDialog;
import javafx.stage.Stage;
import java.util.Optional;

/**
 * JavaFX application entry point for the GoNature client.
 *
 * <p>on startup, prompts for the server IP, opens the connection, and navigates
 * to the login screen. the static {@link #chat} field is the single
 * {@link ClientController} instance shared by all GUI controllers.
 */
public class ClientUI extends Application {

    /** shared controller used by all GUI screens; set before any screen is shown. */
    public static ClientController chat;

    /** server hostname or IP entered by the user at startup. */
    public static String serverHost = "localhost";

    /**
     * @param args command-line arguments (not used)
     * @throws Exception if the JavaFX runtime cannot be initialised
     */
    public static void main(String[] args) throws Exception {
        launch(args);
    }

    /**
     * prompts for the server IP, opens the connection, then navigates to the login screen.
     *
     * @param primaryStage the primary window provided by the JavaFX runtime
     * @throws Exception if the login screen FXML cannot be loaded
     */
    @Override
    public void start(Stage primaryStage) throws Exception {
        TextInputDialog dialog = new TextInputDialog("localhost");
        dialog.setTitle("Connect to Server");
        dialog.setHeaderText("Enter Server IP Address");
        dialog.setContentText("IP:");
        gui.ThemeManager.styleDialog(dialog);

        Optional<String> result = dialog.showAndWait();
        if (!result.isPresent()) { Platform.exit(); return; }

        serverHost = result.get().trim().isEmpty() ? "localhost" : result.get().trim();
        chat = new ClientController(serverHost, 5555);

        // disconnect cleanly when the user closes the window via the X button
        primaryStage.setOnCloseRequest(event -> {
            if (chat != null) chat.disconnect();
            Platform.exit();
        });

        new LoginController().start(primaryStage);
    }
}
