package client;

import gui.LoginController;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;
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
        Dialog<String> dialog = new Dialog<>();
        dialog.setTitle("Connect to GoNature");

        // ── branded pine hero banner (echoes the login screen the user lands on next) ──
        Label brand = new Label("GoNature");
        brand.setGraphic(gui.Icons.pine(26, gui.Icons.CREAM));
        brand.setGraphicTextGap(11);
        brand.setStyle("-fx-font-family: -park-font-display; -fx-font-size: 23px; "
            + "-fx-font-weight: bold; -fx-text-fill: #F5F1E4;");
        Label tagline = new Label("NATIONAL PARK RESERVATION SYSTEM");
        tagline.setStyle("-fx-font-size: 11px; -fx-font-weight: bold; "
            + "-fx-text-fill: rgba(245,241,228,0.82);");
        VBox hero = new VBox(3, brand, tagline);
        hero.setAlignment(Pos.CENTER_LEFT);
        hero.setPadding(new Insets(18, 22, 16, 22));
        hero.setMaxWidth(Double.MAX_VALUE);
        hero.setStyle("-fx-background-color: linear-gradient(to right, -park-pine-dark, #164A33, -park-pine); "
            + "-fx-background-radius: 12 12 0 0; "
            + "-fx-border-color: transparent transparent -park-gold transparent; "
            + "-fx-border-width: 0 0 3 0;");

        // ── form area ──
        Label fieldLabel = new Label("SERVER IP ADDRESS");
        fieldLabel.getStyleClass().add("section-label");

        TextField ipField = new TextField("localhost");
        ipField.setPromptText("e.g. 192.168.1.1  or  localhost");
        ipField.getStyleClass().add("text-field");
        ipField.setMaxWidth(Double.MAX_VALUE);
        ipField.setStyle("-fx-font-size: 14px; -fx-padding: 9 12;");

        Label hint = new Label("Enter the address of the GoNature server you want to connect to.");
        hint.getStyleClass().add("lbl-hint");
        hint.setWrapText(true);

        VBox form = new VBox(9, fieldLabel, ipField, hint);
        form.setPadding(new Insets(18, 22, 18, 22));

        // ── card wrapper: pine hero on top, parchment form below ──
        VBox card = new VBox(hero, form);
        card.setMaxWidth(Double.MAX_VALUE);
        card.setStyle("-fx-background-color: -park-surface; -fx-background-radius: 12; "
            + "-fx-border-color: -park-border; -fx-border-radius: 12; -fx-border-width: 1;");
        dialog.getDialogPane().setContent(card);

        ButtonType connectBtn = new ButtonType("Connect", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(connectBtn, ButtonType.CANCEL);
        dialog.setResultConverter(btn -> btn == connectBtn ? ipField.getText() : null);

        // themed popup: pine/gold accents, always-light parchment, themed Connect/Cancel buttons
        DialogPane pane = dialog.getDialogPane();
        pane.setMinWidth(460);
        java.net.URL css = getClass().getResource("/gui/gonature.css");
        if (css != null) pane.getStylesheets().add(css.toExternalForm());
        pane.getStyleClass().add("help-dialog");

        Platform.runLater(ipField::requestFocus);

        Optional<String> result = dialog.showAndWait();
        if (!result.isPresent() || result.get() == null) { Platform.exit(); return; }

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
