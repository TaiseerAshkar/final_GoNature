package gui;

import Server.ServerUI;
import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * FXML controller for the server control panel ({@code ServerPort.fxml}).
 *
 * <p>shows the port field, client table, connection log, and simulation log.
 * the static helper methods ({@link #addClient}, {@link #markClientDisconnected},
 * {@link #logSimulation}, etc.) are safe to call from any thread; they all marshal
 * updates to the JavaFX thread via {@link Platform#runLater}.
 */
public class ServerPortFrameController {

    @FXML private TextField   portxt;
    @FXML private Label       lblServerStatus;
    @FXML private Button      btnStart;
    @FXML private Button      btnStop;
    @FXML private Button      btnTheme;
    @FXML private TextArea    txtLog;
    @FXML private TextArea    txtSimLog;
    @FXML private TableView<String[]>             clientTable;
    @FXML private TableColumn<String[], String>   colIP;
    @FXML private TableColumn<String[], String>   colHost;
    @FXML private TableColumn<String[], String>   colStatus;

    /** singleton reference used by static helpers to reach the FXML-injected fields. */
    private static ServerPortFrameController instance;

    /** the running EchoServer; null when the server is stopped. */
    private static Server.EchoServer activeServer;

    /** whether the dark theme is currently active on the control panel. */
    private boolean darkMode = false;

    /** observable backing list for the connected-clients table. */
    private static final ObservableList<String[]> clientList =
        FXCollections.observableArrayList();

    /**
     * order IDs that have a "queued" line in the notification panel and are waiting
     * for the 5-second flip to "sent". ConcurrentHashMap so OCSF and scheduler threads are safe.
     */
    private static final java.util.Set<Integer> pendingReminderOrders =
        java.util.Collections.newSetFromMap(new java.util.concurrent.ConcurrentHashMap<>());

    /**
     * stores a reference to this instance so static helpers can reach the FXML fields,
     * then wires up the client table columns.
     */
    @FXML
    public void initialize() {
        instance = this;
        portxt.setText("5555");
        colIP.setCellValueFactory(d     -> new SimpleStringProperty(d.getValue()[0]));
        colHost.setCellValueFactory(d   -> new SimpleStringProperty(d.getValue()[1]));
        colStatus.setCellValueFactory(d -> new SimpleStringProperty(d.getValue()[2]));
        clientTable.setItems(clientList);
        clientTable.setPlaceholder(emptyState());
    }

    /**
     * toggles the control panel between the light and dark "Night Trail" themes by adding
     * or removing the {@code dark-mode} style class on the scene root. the button glyph
     * flips between a moon (currently light) and a sun (currently dark).
     *
     * @param event the button-click event
     */
    @FXML
    public void toggleTheme(ActionEvent event) {
        if (btnTheme == null || btnTheme.getScene() == null) return;
        Parent root = btnTheme.getScene().getRoot();
        darkMode = !darkMode;
        if (darkMode) {
            if (!root.getStyleClass().contains("dark-mode"))
                root.getStyleClass().add("dark-mode");
            btnTheme.setText("☀");
        } else {
            root.getStyleClass().remove("dark-mode");
            btnTheme.setText("🌙");
        }
    }

    /**
     * builds the designed empty state for the clients table: a pine vector monitor icon,
     * a short headline, and a hint, replacing the bare "No clients connected." text.
     * styled via the {@code empty-title} / {@code empty-hint} classes in {@code ServerPort.css}.
     */
    private static javafx.scene.Node emptyState() {
        javafx.scene.shape.SVGPath icon = new javafx.scene.shape.SVGPath();
        icon.setContent("M4.5 5 H19.5 A1.5 1.5 0 0 1 21 6.5 V15 A1.5 1.5 0 0 1 19.5 16.5 "
            + "H4.5 A1.5 1.5 0 0 1 3 15 V6.5 A1.5 1.5 0 0 1 4.5 5 Z M9 20 H15 M12 16.5 V20");
        icon.setFill(javafx.scene.paint.Color.TRANSPARENT);
        icon.setStrokeWidth(1.7);
        icon.setStrokeLineCap(javafx.scene.shape.StrokeLineCap.ROUND);
        icon.setStrokeLineJoin(javafx.scene.shape.StrokeLineJoin.ROUND);
        icon.getStyleClass().add("ikon-stroke");
        double s = 34.0 / 24.0;
        icon.setScaleX(s);
        icon.setScaleY(s);

        Label title = new Label("No clients connected");
        title.getStyleClass().add("empty-title");
        Label hint = new Label("Connected GoNature clients will appear here.");
        hint.getStyleClass().add("empty-hint");

        javafx.scene.layout.VBox box =
            new javafx.scene.layout.VBox(7, new javafx.scene.Group(icon), title, hint);
        box.setAlignment(javafx.geometry.Pos.CENTER);
        return box;
    }

    /**
     * adds a row to the client table and logs the connection. safe to call from any thread.
     *
     * @param ip   the client's IP address
     * @param host the client's resolved hostname
     */
    public static void addClient(String ip, String host) {
        if (instance == null) return;
        Platform.runLater(() -> {
            clientList.add(new String[]{ip, host, "Connected"});
            instance.txtLog.appendText("Client connected: " + ip + " (" + host + ")\n");
        });
    }

    /**
     * removes the row for the given IP from the client table and logs the disconnection.
     * safe to call from any thread.
     *
     * @param ip the client's IP address as recorded when it connected
     */
    public static void markClientDisconnected(String ip) {
        if (instance == null) return;
        Platform.runLater(() -> {
            clientList.removeIf(row -> row[0].equals(ip));
            instance.txtLog.appendText("Client disconnected: " + ip + "\n");
        });
    }

    /**
     * appends a line to the simulation log. falls back to stdout if the controller isn't
     * initialised yet (e.g. during server startup). safe to call from any thread.
     *
     * @param message the simulation line to append; a newline is added automatically
     */
    public static void logSimulation(String message) {
        if (instance == null) { System.out.println("[SIM] " + message); return; }
        Platform.runLater(() -> {
            if (instance.txtSimLog != null)
                instance.txtSimLog.appendText(message + "\n");
        });
    }

    /**
     * registers an order ID as pending so the 5-second upgrade task knows whether
     * to replace the "queued" line or skip (if the order was already cancelled).
     *
     * @param orderId the newly booked order that will visit tomorrow
     */
    public static void addPendingReminder(int orderId) {
        pendingReminderOrders.add(orderId);
    }

    /**
     * removes the pending reminder for a cancelled order so the 5-second upgrade becomes a no-op,
     * then erases the notification line from the sim log. safe to call from any thread.
     *
     * @param orderId the order that was just canceled
     */
    public static void removeReminderForOrder(int orderId) {
        pendingReminderOrders.remove(orderId); // stop the scheduled upgrade before touching the UI
        if (instance == null) return;
        Platform.runLater(() -> {
            if (instance.txtSimLog == null) return;
            String text = instance.txtSimLog.getText();
            if (!text.contains("Order #" + orderId)) return; // nothing to remove
            // filter out the matching line; it ends with "  |  Order #<id>"
            StringBuilder sb = new StringBuilder();
            boolean removed = false;
            for (String line : text.split("\n", -1)) {
                if (line.matches(".*\\|\\s+Order #" + orderId + "\\s*")) {
                    removed = true;
                } else {
                    sb.append(line).append("\n");
                }
            }
            if (!removed) return;
            String result = sb.toString();
            if (result.endsWith("\n")) result = result.substring(0, result.length() - 1);
            instance.txtSimLog.setText(result);
            instance.txtSimLog.appendText(
                "\n🚫 Reminder removed — Order #" + orderId + " was canceled by visitor");
        });
    }

    /**
     * called by the 5-second countdown in {@code EchoServer}. replaces the "queued" line
     * with "✅ Reminder sent", unless the order was cancelled in the meantime, in which
     * case {@link #removeReminderForOrder} already removed it from the pending set and this
     * method returns silently. safe to call from any thread.
     *
     * @param orderId  the order whose reminder is being confirmed
     * @param email    the visitor's email address
     * @param parkName the name of the park
     * @param date     the visit date string (yyyy-MM-dd)
     * @param time     the visit time string (HH:mm)
     */
    public static void upgradeReminderToSent(int orderId, String email,
                                              String parkName, String date, String time) {
        if (!pendingReminderOrders.remove(orderId)) return; // canceled before 5s elapsed
        if (instance == null) return;
        Platform.runLater(() -> {
            if (instance.txtSimLog == null) return;
            String text = instance.txtSimLog.getText();
            StringBuilder sb = new StringBuilder();
            for (String line : text.split("\n", -1)) {
                if (!line.matches(".*\\|\\s+Order #" + orderId + "\\s*")) {
                    sb.append(line).append("\n");
                }
            }
            String result = sb.toString();
            if (result.endsWith("\n")) result = result.substring(0, result.length() - 1);
            instance.txtSimLog.setText(result);
            instance.txtSimLog.appendText(
                "\n✅ Reminder sent → " + email +
                "  |  " + parkName + "  |  " + date + " at " + time +
                "  |  Order #" + orderId);
        });
    }

    /**
     * saves the connection log and live-notifications log to a timestamped .txt file
     * chosen by the operator via a file-save dialog.
     *
     * @param event the button-click event
     */
    public void handleExportLogs(ActionEvent event) {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Export Server Logs");
        String ts = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
        chooser.setInitialFileName("gonature_logs_" + ts + ".txt");
        chooser.getExtensionFilters().add(
            new FileChooser.ExtensionFilter("Text files", "*.txt"));
        Stage stage = (Stage) txtLog.getScene().getWindow();
        File file = chooser.showSaveDialog(stage);
        if (file == null) return;

        String content =
            "=== GoNature Server Logs — " + ts + " ===\n\n" +
            "--- Connection Log ---\n" +
            (txtLog.getText().isEmpty() ? "(empty)\n" : txtLog.getText()) +
            "\n--- Live Notifications ---\n" +
            (txtSimLog.getText().isEmpty() ? "(empty)\n" : txtSimLog.getText());

        try {
            Files.writeString(file.toPath(), content);
            Alert ok = new Alert(Alert.AlertType.INFORMATION);
            ok.setTitle("Export Successful");
            ok.setHeaderText(null);
            ok.setContentText("Logs saved to:\n" + file.getAbsolutePath());
            ok.showAndWait();
        } catch (IOException e) {
            Alert err = new Alert(Alert.AlertType.ERROR);
            err.setTitle("Export Failed");
            err.setHeaderText(null);
            err.setContentText("Could not write file: " + e.getMessage());
            err.showAndWait();
        }
    }

    /**
     * reads the port field and starts the server.
     *
     * @param event the button-click event
     * @throws Exception if the server cannot be started
     */
    public void Done(ActionEvent event) throws Exception {
        String p = portxt.getText().trim();
        if (p.isEmpty()) { System.out.println("Enter a port!"); return; }
        // Show a neutral "Starting…" state while the background thread connects.
        // setRunning() / setServerFailed() are called from EchoServer.serverStarted()
        // via Platform.runLater once the DB connection result is known.
        if (btnStart != null) btnStart.setDisable(true);
        lblServerStatus.setText("● Starting…");
        lblServerStatus.getStyleClass().setAll("card-status-stopped");
        ServerUI.runServer(p);
    }

    /** called from {@code EchoServer.serverStarted()} when the server and DB are both up. */
    public static void setServerRunning(String port) {
        if (instance == null) return;
        Platform.runLater(() -> instance.setRunning(port));
    }

    /** called from {@code EchoServer.serverStarted()} when the DB connection failed. */
    public static void setServerFailed(String reason) {
        if (instance == null) return;
        Platform.runLater(() -> {
            instance.lblServerStatus.setText("● Failed — " + reason);
            instance.lblServerStatus.getStyleClass().setAll("card-status-stopped");
            instance.portxt.setDisable(false);
            if (instance.btnStart != null) instance.btnStart.setDisable(false);
            if (instance.btnStop  != null) instance.btnStop.setDisable(true);
        });
    }

    /**
     * flips the status badge to the green "running" state, locks the port field, and
     * disables further start clicks (the server is already up). safe to call repeatedly.
     *
     * @param port the port the server is now listening on
     */
    private void setRunning(String port) {
        if (lblServerStatus == null) return;
        lblServerStatus.setText("● Running on port " + port);
        lblServerStatus.getStyleClass().setAll("card-status-running");
        portxt.setDisable(true);
        if (btnStart != null) btnStart.setDisable(true);
        if (btnStop  != null) btnStop.setDisable(false);
    }

    private void setStopped() {
        if (lblServerStatus == null) return;
        lblServerStatus.setText("● Stopped");
        lblServerStatus.getStyleClass().setAll("card-status-stopped");
        portxt.setDisable(false);
        if (btnStart != null) btnStart.setDisable(false);
        if (btnStop  != null) btnStop.setDisable(true);
        clientList.clear();
    }

    /**
     * stores the running EchoServer so it can be stopped later.
     * called by {@link ServerUI} after starting the server thread.
     */
    public static void setActiveServer(Server.EchoServer server) {
        activeServer = server;
    }

    /**
     * stops the running server, clears the client table, and resets the UI to the
     * stopped state so the operator can start again on a different port.
     */
    public void stopServer(ActionEvent event) {
        if (activeServer != null) {
            try {
                activeServer.stopListening();
                activeServer.close();
            } catch (Exception e) {
                System.out.println("[ServerUI] Error stopping server: " + e.getMessage());
            }
            activeServer = null;
        }
        if (instance != null) {
            Platform.runLater(instance::setStopped);
        }
        System.out.println("[ServerUI] Server stopped.");
    }

    /**
     * exits the server process immediately.
     *
     * @param event the button-click event
     */
    public void getExitBtn(ActionEvent event) {
        System.exit(0);
    }

    /**
     * @param primaryStage the stage in which to show the panel
     * @throws Exception if the FXML file cannot be loaded
     */
    public void start(Stage primaryStage) throws Exception {
        Parent root = FXMLLoader.load(getClass().getResource("/gui/ServerPort.fxml"));
        Scene scene = new Scene(root);
        primaryStage.setTitle("G9 — Server");
        primaryStage.setScene(scene);
        primaryStage.show();
    }
}
