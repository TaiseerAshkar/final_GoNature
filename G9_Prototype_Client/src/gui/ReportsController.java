package gui;

import client.ChatClient;
import client.ClientUI;
import logic.Message;
import logic.Park;
import logic.ReportCancelRow;
import logic.ReportCancelDistribution;
import logic.ReportDurationRow;
import logic.ReportRequest;
import logic.ReportUsageRow;
import logic.ReportVisitorRow;
import client.UserSession;
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
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

/**
 * FXML controller for the reports dashboard ({@code ReportsFrame.fxml}).
 *
 * <p>three tabs: visitor bar chart, cancellation table, and hourly-usage line chart.
 * a shared period selector (month + year) at the top controls which calendar month
 * is shown. park managers are locked to their park; dept. managers see all.
 */
public class ReportsController {

    // ── Month names in order ─────────────────────────────────────────────────
    private static final List<String> MONTH_NAMES = Arrays.asList(
        "January", "February", "March", "April", "May", "June",
        "July", "August", "September", "October", "November", "December"
    );

    /**
     * set by {@link EmployeeController} before opening this window to lock the dashboard
     * to a specific park. reset to {@code null} in {@link #initialize()} so dept-manager
     * opens are always unrestricted.
     */
    public static Integer lockedParkId = null;

    private Integer myParkId   = null;  // captured from lockedParkId at init; null = no restriction
    private String  myParkName = null;  // display name of the locked park, used to filter the usage chart

    @FXML private Label  lblManagerName;
    @FXML private Button btnTheme;

    // ── Period selector (shared across all tabs) ─────────────────────────────
    @FXML private ComboBox<String>  cboMonth;
    @FXML private ComboBox<Integer> cboYear;
    @FXML private Label             lblPeriodBadge;

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
    @FXML private BarChart<String, Number>              chartCancelDist;
    @FXML private Label                                 lblCancelDistStatus;

    // ── Tab 3: Usage Report ──────────────────────────────────────────────────
    @FXML private Label                     lblUsageStatus;
    @FXML private Label                     lblUsageError;
    @FXML private LineChart<String, Number> chartUsage;

    // ── Tab 4: Visit Duration Report ─────────────────────────────────────────
    @FXML private Label                    lblDurationStatus;
    @FXML private Label                    lblDurationError;
    @FXML private BarChart<String, Number> chartDuration;

    /**
     * captures and clears the static park lock, sets up the period selector to the
     * current month, wires table columns, populates park filters, then auto-loads all
     * three reports for the current month.
     */
    @FXML
    public void initialize() {
        ThemeManager.installToggle(btnTheme);
        // clear the static lock immediately so the next open is unrestricted
        myParkId     = lockedParkId;
        lockedParkId = null;

        UserSession s = UserSession.getInstance();
        if (s != null) {
            String name = s.getFullName();
            if (myParkId != null) name += "  —  Park #" + myParkId + " (locked)";
            lblManagerName.setText(name);
        }

        setupPeriodSelector();
        setupCancelTable();
        loadParksIntoFilters();
        loadVisitorReport();
        loadCancelReport();
        loadUsageReport();
        loadDurationReport();
    }

    // ── Period selector ───────────────────────────────────────────────────────

    /**
     * populates month and year ComboBoxes and defaults to the current calendar month.
     */
    private void setupPeriodSelector() {
        cboMonth.getItems().addAll(MONTH_NAMES);

        LocalDate today = LocalDate.now();
        int currentYear = today.getYear();
        for (int y = currentYear - 2; y <= currentYear + 1; y++) {
            cboYear.getItems().add(y);
        }

        // default to current month / year
        cboMonth.setValue(MONTH_NAMES.get(today.getMonthValue() - 1));
        cboYear.setValue(currentYear);
    }

    /** @return the 1-based month number from the month ComboBox */
    private int getSelectedMonth() {
        String m = cboMonth.getValue();
        if (m == null) return LocalDate.now().getMonthValue();
        int idx = MONTH_NAMES.indexOf(m);
        return idx < 0 ? LocalDate.now().getMonthValue() : idx + 1;
    }

    /** @return the selected year (four-digit) from the year ComboBox */
    private int getSelectedYear() {
        Integer y = cboYear.getValue();
        return y != null ? y : LocalDate.now().getYear();
    }

    /** @return a human-readable label such as {@code "June 2026"} */
    private String periodLabel() {
        return cboMonth.getValue() + " " + getSelectedYear();
    }

    /**
     * builds a {@link ReportRequest} for the given park ID and the currently
     * selected month/year.
     *
     * @param parkId the park ID, or {@code 0} for all parks
     */
    private ReportRequest buildRequest(int parkId) {
        return new ReportRequest(parkId, getSelectedYear(), getSelectedMonth());
    }

    /**
     * refreshes all three reports for the currently selected period.
     *
     * @param e the button-click event
     */
    @FXML
    public void handleGenerate(ActionEvent e) {
        lblPeriodBadge.setText("Showing: " + periodLabel());
        loadVisitorReport();
        loadCancelReport();
        loadUsageReport();
        loadDurationReport();
    }

    // ── Cancel table setup ────────────────────────────────────────────────────

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
        UiCells.applyStatusChip(colCancelStatus);
    }

    // ── Park filter setup ─────────────────────────────────────────────────────

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
            // park manager: lock both combos to the single assigned park
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
            // dept. manager / service rep: show all parks with an "All Parks" sentinel
            Park allParks = new Park(0, "All Parks", 0, 0, 0, 0);
            for (ComboBox<Park> cbo : new ComboBox[]{cboVisitorPark, cboCancelPark}) {
                cbo.setConverter(conv);
                cbo.getItems().add(allParks);
                if (parks != null) cbo.getItems().addAll(parks);
                cbo.setValue(allParks);
            }
        }
    }

    // ── Tab load methods ──────────────────────────────────────────────────────

    /** @param e the button-click event */
    @FXML public void handleRefreshVisitor(ActionEvent e) { loadVisitorReport(); }

    /**
     * loads visitor report data for the selected month and populates the bar chart.
     * INDIVIDUAL and SUBSCRIBER types are merged into one series so the chart stays readable.
     */
    private void loadVisitorReport() {
        lblVisitorError.setText("");
        lblVisitorStatus.setText("Loading…");

        Integer parkId = (myParkId != null) ? myParkId : null;
        if (myParkId == null) {
            Park sel = cboVisitorPark.getValue();
            parkId = (sel == null || sel.getId() == 0) ? null : sel.getId();
        }

        ChatClient.lastVisitorReport = null;
        ClientUI.chat.accept(new Message("GET_VISITOR_REPORT",
            buildRequest(parkId != null ? parkId : 0)));
        List<ReportVisitorRow> rows = ChatClient.lastVisitorReport;
        chartVisitors.getData().clear();

        String period = periodLabel();
        chartVisitors.setTitle("Visitor Report — " + period);

        if (rows == null) {
            lblVisitorError.setText("Could not load visitor data. Is the server running?");
            lblVisitorStatus.setText("");
            return;
        }
        if (rows.isEmpty()) {
            lblVisitorStatus.setText("No completed or confirmed visits in " + period + ".");
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
            total + " visitor(s) across " + byDay.size() + " day(s) — " + period);
    }

    /** @param e the button-click event */
    @FXML public void handleRefreshCancel(ActionEvent e) { loadCancelReport(); }

    /**
     * loads cancellation report data for the selected month and populates the table,
     * then loads and renders the day-of-week distribution chart for the same filter.
     * shows cancelled vs no-show counts separately in the status label.
     */
    private void loadCancelReport() {
        lblCancelError.setText("");
        lblCancelStatus.setText("Loading…");

        Integer parkId = (myParkId != null) ? myParkId : null;
        if (myParkId == null) {
            Park sel = cboCancelPark.getValue();
            parkId = (sel == null || sel.getId() == 0) ? null : sel.getId();
        }
        int parkFilter = (parkId != null) ? parkId : 0;   // 0 = whole region

        ChatClient.lastCancelReport = null;
        ClientUI.chat.accept(new Message("GET_CANCEL_REPORT", buildRequest(parkFilter)));
        List<ReportCancelRow> rows = ChatClient.lastCancelReport;
        tableCancels.getItems().clear();

        String period = periodLabel();

        if (rows == null) {
            lblCancelError.setText("Could not load cancellation data. Is the server running?");
            lblCancelStatus.setText("");
            chartCancelDist.getData().clear();
            lblCancelDistStatus.setText("");
            return;
        }
        tableCancels.getItems().addAll(rows);
        long cancelled = rows.stream().filter(r -> "CANCELLED".equals(r.getStatus())).count();
        long noShow    = rows.stream().filter(r -> "NO_SHOW".equals(r.getStatus())).count();
        lblCancelStatus.setText(
            rows.size() + " record(s) — " + cancelled + " cancelled, "
            + noShow + " no-show — " + period);

        loadCancelDistribution(parkFilter, period);
    }

    /**
     * loads the cancellation day-of-week distribution for the same park filter and period
     * as the cancellation table, and renders it as a bar chart (one bar per weekday,
     * Sunday-first). the per-weekday average and per-active-day average are shown in the
     * status label; the average bar height is highlighted via the label as a reference value.
     *
     * @param parkFilter the park ID, or {@code 0} for the whole region
     * @param period     human-readable period label such as {@code "June 2026"}
     */
    private void loadCancelDistribution(int parkFilter, String period) {
        ChatClient.lastCancelDistribution = null;
        ClientUI.chat.accept(new Message("GET_CANCEL_DISTRIBUTION", buildRequest(parkFilter)));
        ReportCancelDistribution dist = ChatClient.lastCancelDistribution;

        chartCancelDist.getData().clear();
        chartCancelDist.setTitle("Cancellations by Day of Week — " + period);

        if (dist == null) {
            lblCancelDistStatus.setText("");
            return;
        }
        if (dist.getTotal() == 0) {
            lblCancelDistStatus.setText("No cancellations to distribute in " + period + ".");
            return;
        }

        // one bar per weekday (Sunday-first); zero-count days still appear as empty slots.
        XYChart.Series<String, Number> series = new XYChart.Series<>();
        series.setName("Cancellations");
        List<String>  labels = dist.getDayLabels();
        List<Integer> counts = dist.getCounts();
        for (int i = 0; i < labels.size(); i++) {
            series.getData().add(new XYChart.Data<>(labels.get(i), counts.get(i)));
        }
        chartCancelDist.getData().add(series);

        lblCancelDistStatus.setText(String.format(
            "Avg %.2f/weekday  ·  %.2f per active day  ·  peak %s (%d)  ·  %d total over %d active day(s)",
            dist.getAvgPerWeekday(), dist.getAvgPerWorkday(),
            dist.getPeakDay(), dist.getPeakCount(),
            dist.getTotal(), dist.getDistinctWorkdays()));
    }

    /** @param e the button-click event */
    @FXML public void handleRefreshUsage(ActionEvent e) { loadUsageReport(); }

    /**
     * loads usage report data for the selected month and plots one line-chart series per park.
     * each point is {@code (hourSlot, pct-of-capacity)}, capped at 100%.
     */
    private void loadUsageReport() {
        lblUsageError.setText("");
        lblUsageStatus.setText("Loading…");

        ChatClient.lastUsageReport = null;
        ClientUI.chat.accept(new Message("GET_USAGE_REPORT", buildRequest(0)));
        List<ReportUsageRow> rows = ChatClient.lastUsageReport;
        chartUsage.getData().clear();

        String period = periodLabel();
        chartUsage.setTitle("Usage Report — " + period);

        if (rows == null) {
            lblUsageError.setText("Could not load usage data. Is the server running?");
            lblUsageStatus.setText("");
            return;
        }
        if (rows.isEmpty()) {
            lblUsageStatus.setText("No usage data for " + period + ".");
            return;
        }

        LinkedHashMap<String, List<ReportUsageRow>> byPark = new LinkedHashMap<>();
        for (ReportUsageRow r : rows) {
            byPark.computeIfAbsent(r.getParkName(), k -> new ArrayList<>()).add(r);
        }
        // drop all other parks when locked; park managers should only see their park
        if (myParkName != null) {
            byPark.entrySet().removeIf(e -> !e.getKey().equals(myParkName));
        }

        // Collect all hour slots across every park, sort them (zero-padded strings sort
        // chronologically), then pre-seed the CategoryAxis so JavaFX registers the order
        // before any series data is added; otherwise categories appear in first-encounter order.
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
        lblUsageStatus.setText(byPark.size() + " park(s) — " + period);
    }

    /** @param e the button-click event */
    @FXML public void handleRefreshDuration(ActionEvent e) { loadDurationReport(); }

    /**
     * loads the average-visit-duration data for the selected month and plots one bar per
     * visitor type (Individual, Group). each bar's height is the mean length of stay in
     * hours for completed visits (those with both an entry and an exit recorded).
     */
    private void loadDurationReport() {
        lblDurationError.setText("");
        lblDurationStatus.setText("Loading…");

        ChatClient.lastDurationReport = null;
        ClientUI.chat.accept(new Message("GET_DURATION_REPORT", buildRequest(0)));
        List<ReportDurationRow> rows = ChatClient.lastDurationReport;
        chartDuration.getData().clear();

        String period = periodLabel();
        chartDuration.setTitle("Average Visit Duration by Visitor Type — " + period);

        if (rows == null) {
            lblDurationError.setText("Could not load duration data. Is the server running?");
            lblDurationStatus.setText("");
            return;
        }
        if (rows.isEmpty()) {
            lblDurationStatus.setText("No completed visits with entry and exit times in " + period + ".");
            return;
        }

        XYChart.Series<String, Number> series = new XYChart.Series<>();
        series.setName("Avg Duration (hours)");
        for (ReportDurationRow r : rows) {
            series.getData().add(new XYChart.Data<>(r.getVisitorType(), r.getAvgHours()));
        }
        chartDuration.getData().add(series);
        lblDurationStatus.setText(rows.size() + " visitor type(s) — " + period);
    }

    // ── Navigation ────────────────────────────────────────────────────────────

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
        Scene scene = new Scene(root);
        ThemeManager.register(scene);
        stage.setScene(scene);
        stage.centerOnScreen();
        stage.show();
    }
}

