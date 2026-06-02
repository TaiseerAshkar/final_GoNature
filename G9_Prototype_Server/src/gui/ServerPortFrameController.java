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
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.stage.Stage;

/**
 * FXML controller for the server control panel ({@code ServerPort.fxml}).
 *
 * <p>shows the port field, client table, connection log, and simulation log.
 * the static helper methods ({@link #addClient}, {@link #markClientDisconnected},
 * {@link #logSimulation}, etc.) are safe to call from any thread — they all marshal
 * updates to the JavaFX thread via {@link Platform#runLater}.
 */
public class ServerPortFrameController {

    @FXML private TextField   portxt;
    @FXML private TextArea    txtLog;
    @FXML private TextArea    txtSimLog;
    @FXML private TableView<String[]>             clientTable;
    @FXML private TableColumn<String[], String>   colIP;
    @FXML private TableColumn<String[], String>   colHost;
    @FXML private TableColumn<String[], String>   colStatus;

    /** singleton reference used by static helpers to reach the FXML-injected fields. */
    private static ServerPortFrameController instance;

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
     * removes the row for the given IP and logs the disconnection. safe to call from any thread.
     *
     * @param ip the client's IP address as recorded when it connected
     */
    public static void removeClient(String ip) {
        if (instance == null) return;
        Platform.runLater(() -> {
            clientList.removeIf(row -> row[0].equals(ip));
            instance.txtLog.appendText("Client disconnected: " + ip + "\n");
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
            // filter out the matching line — it ends with "  |  Order #<id>"
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
     * with "✅ Reminder sent" — unless the order was cancelled in the meantime, in which
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
     * reads the port field and starts the server.
     *
     * @param event the button-click event
     * @throws Exception if the server cannot be started
     */
    public void Done(ActionEvent event) throws Exception {
        String p = portxt.getText().trim();
        if (p.isEmpty()) { System.out.println("Enter a port!"); return; }
        ServerUI.runServer(p);
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
