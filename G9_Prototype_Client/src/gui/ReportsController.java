package gui;

import client.ChatClient;
import client.ClientUI;
import logic.Message;
import logic.Park;
import logic.ReportCancelRow;
import logic.ReportUsageRow;
import logic.ReportVisitorRow;
import logic.UserSession;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.chart.BarChart;
import javafx.scene.chart.LineChart;
import javafx.scene.chart.XYChart;
import javafx.scene.control.*;
import javafx.stage.Stage;
import javafx.util.StringConverter;

import javafx.collections.FXCollections;
import javafx.scene.chart.CategoryAxis;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

/**
 * FXML controller for the reports dashboard ({@code ReportsFrame.fxml}).
 *
 * <p>three tabs: visitor bar chart, cancellation table, and hourly-usage line chart.
 * all three auto-load on open. park managers are locked to their park; dept. managers see all.
 */
public class ReportsController {

    /**
     * set by {@link EmployeeController} before opening this window to lock the dashboard
     * to a specific park. reset to {@code null} in {@link #initialize()} so dept-manager
     * opens are always unrestricted.
     */
    public static Integer lockedParkId = null;

    private Integer myParkId   = null;  // captured from lockedParkId at init; null = no restriction
    private String  myParkName = null;  // display name of the locked park, used to filter the usage chart

    @FXML private Label lblManagerName;

    // ── Tab 1: Visitor Report ────────────────────────────────────────────────
    @FXML private ComboBox<Park>           cboVisitorPark;
    @FXML private Label                    lblVisitorStatus;
    @FXML private Label                    lblVisitorError;
    @FXML private BarChart<String, Number> chartVisitors;

    // ── Tab 2: Cancellation Report ───────────────────────────────────────────
    @FXML private ComboBox<Park>               cboCancelPark;
    @FXML private Label                        lblCancelStatus;
    @FXML private Label                        lblCancelError;
    @FXML private TableView<ReportCancelRow>   tableCancels;
    @FXML private TableColumn<ReportCancelRow, Integer> colCancelId;
    @FXML private TableColumn<ReportCancelRow, String>  colCancelPark;
    @FXML private TableColumn<ReportCancelRow, String>  colCancelVisitor;
    @FXML private TableColumn<ReportCancelRow, String>  colCancelDate;
    @FXML private TableColumn<ReportCancelRow, String>  colCancelTime;
    @FXML private TableColumn<ReportCancelRow, String>  colCancelType;
    @FXML private TableColumn<ReportCancelRow, String>  colCancelStatus;
    @FXML private TableColumn<ReportCancelRow, Integer> colCancelVisitors;
    @FXML private TableColumn<ReportCancelRow, String>  colCancelCreated;

    // ── Tab 3: Usage Report ──────────────────────────────────────────────────
    @FXML private Label                     lblUsageStatus;
    @FXML private Label                     lblUsageError;
    @FXML private LineChart<String, Number> chartUsage;

    /**
     * captures and clears the static park lock, wires table columns, populates park filters,
     * then auto-loads all three reports.
     */
    @FXML
    public void initialize() {
        // clear the static lock immediately so the next open (from the management dashboard) is unrestricted
        myParkId      = lockedParkId;
        lockedParkId  = null;

        UserSession s = UserSession.getInstance();
        if (s != null) {
            String name = s.getFullName();
            if (myParkId != null) {
                name += "  —  Park #" + myParkId + " (locked)";
            }
            lblManagerName.setText(name);
        }
        setupCancelTable();
        loadParksIntoFilters();
        loadVisitorReport();
        loadCancelReport();
        loadUsageReport();
    }

    /**
     * wires cell-value factories for the cancellation table.
     * status cells are colour-coded so CANCELLED vs NO_SHOW is obvious at a glance.
     */
    private void setupCancelTable() {
        colCancelId.setCellValueFactory(c ->
            new SimpleIntegerProperty(c.getValue().getId()).asObject());
        colCancelPark.setCellValueFactory(c ->
            new SimpleStringProperty(c.getValue().getParkName()));
        colCancelVisitor.setCellValueFactory(c ->
            new SimpleStringProperty(c.getValue().getVisitorId()));
        colCancelDate.setCellValueFactory(c ->
            new SimpleStringProperty(c.getValue().getVisitDate()));
        colCancelTime.setCellValueFactory(c ->
            new SimpleStringProperty(c.getValue().getVisitTime()));
        colCancelType.setCellValueFactory(c ->
            new SimpleStringProperty(c.getValue().getOrderType()));
        colCancelVisitors.setCellValueFactory(c ->
            new SimpleIntegerProperty(c.getValue().getNumVisitors()).asObject());
        colCancelCreated.setCellValueFactory(c ->
            new SimpleStringProperty(c.getValue().getCreatedAt()));

        colCancelStatus.setCellValueFactory(c ->
            new SimpleStringProperty(c.getValue().getStatus()));
        colCancelStatus.setCellFactory(col -> new TableCell<ReportCancelRow, String>() {
            @Override
            protected void updateItem(String status, boolean empty) {
                super.updateItem(status, empty);
                if (empty || status == null) { setText(null); setStyle(""); return; }
                setText(status);
                setStyle("CANCELLED".equals(status)
                    ? "-fx-text-fill: #b71c1c; -fx-font-weight: bold;"
                    : "-fx-text-fill: #e65100; -fx-font-weight: bold;");
            }
        });
    }

    /**
     * loads parks and populates both filter combos.
     * park managers get a single locked entry; dept. managers get all parks plus an "All Parks" sentinel.
     */
    @SuppressWarnings("unchecked")
    private void loadParksIntoFilters() {
        ChatClient.lastParkList = null;
        ClientUI.chat.accept(new Message("GET_PARKS", null));
        List<Park> parks = ChatClient.lastParkList;

        StringConverter<Park> conv = new StringConverter<Park>() {
            @Override public String toString(Park p)     { return p == null ? "" : p.getName(); }
            @Override public Park   fromString(String s) { return null; }
        };

        if (myParkId != null) {
            // park manager — lock both combos to the single assigned park
            Park myPark = null;
            if (parks != null) {
                for (Park p : parks) {
                    if (p.getId() == myParkId) { myPark = p; break; }
                }
            }
            myParkName = (myPark != null) ? myPark.getName() : null;

            for (ComboBox<Park> cbo : new ComboBox[]{cboVisitorPark, cboCancelPark}) {
                cbo.setConverter(conv);
                if (myPark != null) {
                    cbo.getItems().add(myPark);
                    cbo.setValue(myPark);
                }
                cbo.setDisable(true);   // park managers can't change their filter
            }
        } else {
            // dept. manager / service rep — show all parks with an "All Parks" sentinel
            Park allParks = new Park(0, "All Parks", 0, 0, 0, 0);
            for (ComboBox<Park> cbo : new ComboBox[]{cboVisitorPark, cboCancelPark}) {
                cbo.setConverter(conv);
                cbo.getItems().add(allParks);
                if (parks != null) cbo.getItems().addAll(parks);
                cbo.setValue(allParks);
            }
        }
    }

    /** @param e the button-click event */
    @FXML public void handleRefreshVisitor(ActionEvent e) { loadVisitorReport(); }

    /**
     * loads visitor report data and populates the bar chart.
     * INDIVIDUAL and SUBSCRIBER types are merged into one series so the chart stays readable.
     */
    private void loadVisitorReport() {
        lblVisitorError.setText("");
        lblVisitorStatus.setText("Loading…");
        // park managers are locked; everyone else uses the combo selection
        Integer parkId = (myParkId != null) ? myParkId : null;
        if (myParkId == null) {
            Park sel = cboVisitorPark.getValue();
            parkId = (sel == null || sel.getId() == 0) ? null : sel.getId();
        }

        ChatClient.lastVisitorReport = null;
        ClientUI.chat.accept(new Message("GET_VISITOR_REPORT", parkId != null ? parkId : 0));
        List<ReportVisitorRow> rows = ChatClient.lastVisitorReport;
        chartVisitors.getData().clear();

        if (rows == null) {
            lblVisitorError.setText("Could not load visitor data. Is the server running?");
            lblVisitorStatus.setText("");
            return;
        }
        if (rows.isEmpty()) {
            lblVisitorStatus.setText("No completed or confirmed visits in the last 30 days.");
            return;
        }

        LinkedHashMap<String, int[]> byDay = new LinkedHashMap<>();
        for (ReportVisitorRow r : rows) {
            byDay.putIfAbsent(r.getDayLabel(), new int[]{0, 0});
            int[] counts = byDay.get(r.getDayLabel());
            if ("GROUP".equals(r.getOrderType())) counts[1] += r.getTotalVisitors();
            else                                   counts[0] += r.getTotalVisitors();
        }

        XYChart.Series<String, Number> indSeries = new XYChart.Series<>();
        indSeries.setName("Individual / Subscriber");
        XYChart.Series<String, Number> grpSeries = new XYChart.Series<>();
        grpSeries.setName("Group");
        int total = 0;
        for (Map.Entry<String, int[]> entry : byDay.entrySet()) {
            indSeries.getData().add(new XYChart.Data<>(entry.getKey(), entry.getValue()[0]));
            grpSeries.getData().add(new XYChart.Data<>(entry.getKey(), entry.getValue()[1]));
            total += entry.getValue()[0] + entry.getValue()[1];
        }
        chartVisitors.getData().addAll(indSeries, grpSeries);
        lblVisitorStatus.setText(
            total + " visitors across " + byDay.size() + " day(s) — last 30 days");
    }

    /** @param e the button-click event */
    @FXML public void handleRefreshCancel(ActionEvent e) { loadCancelReport(); }

    /**
     * loads cancellation report data and populates the table.
     * shows cancelled vs no-show counts separately in the status label.
     */
    private void loadCancelReport() {
        lblCancelError.setText("");
        lblCancelStatus.setText("Loading…");
        // park managers are locked; everyone else uses the combo selection
        Integer parkId = (myParkId != null) ? myParkId : null;
        if (myParkId == null) {
            Park sel = cboCancelPark.getValue();
            parkId = (sel == null || sel.getId() == 0) ? null : sel.getId();
        }

        ChatClient.lastCancelReport = null;
        ClientUI.chat.accept(new Message("GET_CANCEL_REPORT", parkId != null ? parkId : 0));
        List<ReportCancelRow> rows = ChatClient.lastCancelReport;
        tableCancels.getItems().clear();

        if (rows == null) {
            lblCancelError.setText("Could not load cancellation data. Is the server running?");
            lblCancelStatus.setText("");
            return;
        }
        tableCancels.getItems().addAll(rows);
        long cancelled = rows.stream().filter(r -> "CANCELLED".equals(r.getStatus())).count();
        long noShow    = rows.stream().filter(r -> "NO_SHOW".equals(r.getStatus())).count();
        lblCancelStatus.setText(
            rows.size() + " record(s) — " + cancelled + " cancelled, " + noShow + " no-show");
    }

    /** @param e the button-click event */
    @FXML public void handleRefreshUsage(ActionEvent e) { loadUsageReport(); }

    /**
     * loads usage report data and plots one line-chart series per park.
     * each point is {@code (hourSlot, pct-of-capacity)}, capped at 100%.
     */
    private void loadUsageReport() {
        lblUsageError.setText("");
        lblUsageStatus.setText("Loading…");

        ChatClient.lastUsageReport = null;
        ClientUI.chat.accept(new Message("GET_USAGE_REPORT", null));
        List<ReportUsageRow> rows = ChatClient.lastUsageReport;
        chartUsage.getData().clear();

        if (rows == null) {
            lblUsageError.setText("Could not load usage data. Is the server running?");
            lblUsageStatus.setText("");
            return;
        }
        if (rows.isEmpty()) {
            lblUsageStatus.setText("No usage data for the last 30 days.");
            return;
        }

        LinkedHashMap<String, List<ReportUsageRow>> byPark = new LinkedHashMap<>();
        for (ReportUsageRow r : rows) {
            byPark.computeIfAbsent(r.getParkName(), k -> new ArrayList<>()).add(r);
        }
        // drop all other parks when locked — park managers should only see their park
        if (myParkName != null) {
            byPark.entrySet().removeIf(e -> !e.getKey().equals(myParkName));
        }

        // Collect all hour slots across every park, sort them (zero-padded strings sort
        // chronologically), then pre-seed the CategoryAxis so JavaFX registers the order
        // before any series data is added — otherwise categories appear in first-encounter order.
        TreeSet<String> allHours = new TreeSet<>();
        for (List<ReportUsageRow> parkRows : byPark.values()) {
            for (ReportUsageRow r : parkRows) allHours.add(r.getHourSlot());
        }
        CategoryAxis xAxis = (CategoryAxis) chartUsage.getXAxis();
        xAxis.setCategories(FXCollections.observableArrayList(allHours));
        xAxis.setAutoRanging(false);

        for (Map.Entry<String, List<ReportUsageRow>> entry : byPark.entrySet()) {
            XYChart.Series<String, Number> series = new XYChart.Series<>();
            series.setName(entry.getKey());
            for (ReportUsageRow r : entry.getValue()) {
                double pct = r.getCapacity() > 0
                    ? Math.min(100.0, (r.getAvgPerDay() / r.getCapacity()) * 100.0)
                    : 0.0;
                series.getData().add(new XYChart.Data<>(
                    r.getHourSlot(), Math.round(pct * 10.0) / 10.0));
            }
            chartUsage.getData().add(series);
        }
        lblUsageStatus.setText(byPark.size() + " park(s) — last 30 days");
    }

    /**
     * closes the reports dashboard window.
     *
     * @param event the button-click event
     */
    @FXML
    public void handleClose(ActionEvent event) {
        ((Stage) lblManagerName.getScene().getWindow()).close();
    }

    /**
     * @param stage the stage in which to show the dashboard
     * @throws Exception if the FXML file cannot be loaded
     */
    public void start(Stage stage) throws Exception {
        Parent root = FXMLLoader.load(getClass().getResource("/gui/ReportsFrame.fxml"));
        stage.setTitle("GoNature — Reports Dashboard");
        stage.setScene(new Scene(root));
        stage.show();
    }
}
