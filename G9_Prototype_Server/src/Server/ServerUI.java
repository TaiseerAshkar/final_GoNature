package Server;

import gui.ServerPortFrameController;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
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

        // ── branded pine hero banner (matches the client "Connect to GoNature" dialog) ──
        Label brand = new Label("GoNature");
        brand.setGraphic(pineLogo(26));
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
        hero.setStyle("-fx-background-color: linear-gradient(to right, -park-pine-dark, #0C5F51, -park-pine); "
            + "-fx-background-radius: 12 12 0 0; "
            + "-fx-border-color: transparent transparent -park-gold transparent; "
            + "-fx-border-width: 0 0 3 0;");

        // ── password field + show/hide toggle ──
        Label fieldLabel = new Label("MYSQL PASSWORD");
        fieldLabel.getStyleClass().add("section-label");

        PasswordField passwordField = new PasswordField();
        passwordField.setPromptText("Password");
        passwordField.getStyleClass().add("text-field");
        passwordField.setMaxWidth(Double.MAX_VALUE);
        passwordField.setStyle("-fx-font-size: 14px; -fx-padding: 9 12;");

        // plain text field shares the same binding — shown when "Show password" is ticked
        TextField plainField = new TextField();
        plainField.setPromptText("Password");
        plainField.getStyleClass().add("text-field");
        plainField.setMaxWidth(Double.MAX_VALUE);
        plainField.setStyle("-fx-font-size: 14px; -fx-padding: 9 12;");
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

        Label hint = new Label("Enter the MySQL database password to start the server.");
        hint.getStyleClass().add("lbl-hint");
        hint.setWrapText(true);

        VBox form = new VBox(9, fieldLabel, passwordField, plainField, showPw, hint);
        form.setPadding(new Insets(18, 22, 18, 22));

        // ── card wrapper: pine hero on top, parchment form below ──
        VBox card = new VBox(hero, form);
        card.setMaxWidth(Double.MAX_VALUE);
        card.setStyle("-fx-background-color: -park-surface; -fx-background-radius: 12; "
            + "-fx-border-color: -park-border; -fx-border-radius: 12; -fx-border-width: 1;");
        dialog.getDialogPane().setContent(card);

        ButtonType connectBtn = new ButtonType("Connect", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(connectBtn, ButtonType.CANCEL);
        dialog.setResultConverter(btn -> btn == connectBtn ? passwordField.getText() : null);

        // themed popup: pine/gold accents, themed Connect/Cancel buttons (matches client)
        DialogPane pane = dialog.getDialogPane();
        pane.setMinWidth(460);
        java.net.URL css = getClass().getResource("/gui/ServerPort.css");
        if (css != null) pane.getStylesheets().add(css.toExternalForm());
        pane.getStyleClass().add("help-dialog");

        Platform.runLater(passwordField::requestFocus);

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
     * builds the warm-cream vector pine logomark used in the hero banner — the same 24-grid
     * path as the client's {@code Icons.pine}, inlined here so the server stays dependency-free.
     *
     * @param size rendered size in px
     * @return the scaled logomark node
     */
    private static javafx.scene.Node pineLogo(double size) {
        javafx.scene.shape.SVGPath p = new javafx.scene.shape.SVGPath();
        p.setContent("M12 2 L17.5 9.5 L14.5 9.5 L19.5 16.5 L13.2 16.5 L13.2 21 L10.8 21 "
            + "L10.8 16.5 L4.5 16.5 L9.5 9.5 L6.5 9.5 Z");
        p.setFill(javafx.scene.paint.Color.web("#F5F1E4"));
        double s = size / 24.0;
        p.setScaleX(s);
        p.setScaleY(s);
        // Group so the scale is reflected in layout bounds (a bare scaled path misaligns)
        return new javafx.scene.Group(p);
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