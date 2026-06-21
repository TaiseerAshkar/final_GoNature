package gui;

import client.ChatClient;
import client.ClientUI;
import logic.GuideDetail;
import logic.ParkSettingsRequest;
import logic.Promotion;
import logic.Message;
import logic.Order;
import logic.OrderDetail;
import logic.UserSession;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.util.Duration;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Arrays;

/**
 * controller for the academic/admin screen ({@code AcademicFrame.fxml}).
 *
 * <p>used exclusively by DEPARTMENT_MANAGER. loads live orders via {@code GET_LIVE_ORDERS}
 * and lets the user update visit date or visitor count for any order by ID.
 */
public class AcademicFrameController {

    @FXML private Label lblUserName;
    @FXML private Label lblUserRole;

    // header controls
    @FXML private Label  lblSessionTimer;
    @FXML private Button btnTheme;

    /** ticks once a second to refresh the logged-in-duration label in the header. */
    private Timeline sessionTimeline;

    // ── live-orders table ─────────────────────────────────────────────────────
    @FXML private TableView<OrderDetail>            tableOrders;
    @FXML private TableColumn<OrderDetail, Integer> colId;
    @FXML private TableColumn<OrderDetail, String>  colPark;
    @FXML private TableColumn<OrderDetail, String>  colVisitorId;
    @FXML private TableColumn<OrderDetail, String>  colDate;
    @FXML private TableColumn<OrderDetail, String>  colTime;
    @FXML private TableColumn<OrderDetail, Integer> colVisitors;
    @FXML private TableColumn<OrderDetail, String>  colType;
    @FXML private TableColumn<OrderDetail, String>  colStatus;
    @FXML private TableColumn<OrderDetail, String>  colBookedOn;

    // ── update-order form ─────────────────────────────────────────────────────
    @FXML private TextField        txtOrderNum;
    @FXML private TextField        txtNewDate;
    @FXML private ComboBox<String> cboNewOrderType;
    @FXML private TextField        txtNewVisitors;
    @FXML private Label            lblResult;

    /**
     * populates header labels from {@link UserSession}, wires column cell-value factories,
     * sets up the visit-type dropdown, and installs a table-row selection listener that
     * pre-fills the update form when a row is clicked.
     */
    @FXML
    public void initialize() {
        UserSession session = UserSession.getInstance();
        if (session != null && session.getRole() != null) {
            lblUserName.setText(session.getFullName());
            lblUserRole.setText(session.getRole());
        }

        ThemeManager.installToggle(btnTheme);
        startSessionTimer();

        colId.setCellValueFactory(d ->
            new SimpleIntegerProperty(d.getValue().getId()).asObject());
        colPark.setCellValueFactory(d ->
            new SimpleStringProperty(d.getValue().getParkName()));
        colVisitorId.setCellValueFactory(d ->
            new SimpleStringProperty(d.getValue().getVisitorId()));
        colDate.setCellValueFactory(d ->
            new SimpleStringProperty(d.getValue().getVisitDate()));
        colTime.setCellValueFactory(d ->
            new SimpleStringProperty(d.getValue().getVisitTime()));
        colVisitors.setCellValueFactory(d ->
            new SimpleIntegerProperty(d.getValue().getNumVisitors()).asObject());
        colType.setCellValueFactory(d ->
            new SimpleStringProperty(d.getValue().getOrderType()));
        colStatus.setCellValueFactory(d ->
            new SimpleStringProperty(d.getValue().getStatus()));
        UiCells.applyStatusChip(colStatus);
        colBookedOn.setCellValueFactory(d ->
            new SimpleStringProperty(d.getValue().getCreatedAt()));

        // Visit-type dropdown: SOLO locks visitors to 1; FAMILY/GROUP unlock it.
        cboNewOrderType.getItems().addAll("SOLO", "FAMILY", "GROUP");
        cboNewOrderType.setValue("SOLO");
        applyTypeSideEffects("SOLO");
        cboNewOrderType.valueProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null) applyTypeSideEffects(newVal);
        });

        // Clicking a table row pre-fills the update form.
        tableOrders.getSelectionModel().selectedItemProperty().addListener(
            (obs, oldSel, sel) -> {
                if (sel == null) return;
                txtOrderNum.setText(String.valueOf(sel.getId()));
                txtNewDate.setText(sel.getVisitDate());
                txtNewVisitors.setText(String.valueOf(sel.getNumVisitors()));
                String type = sel.getOrderType();
                if (Arrays.asList("SOLO", "FAMILY", "GROUP").contains(type))
                    cboNewOrderType.setValue(type);
                else
                    cboNewOrderType.setValue("SOLO");
                lblResult.setText("");
            });
    }

    // ── Header: theme toggle + session timer ──────────────────────────────────

    /**
     * flips the whole app between light and dark mode and updates this button's icon.
     *
     * @param event the button-click event
     */
    @FXML
    public void handleToggleTheme(ActionEvent event) {
        ThemeManager.toggle();
        if (btnTheme != null) btnTheme.setText(ThemeManager.iconText());
    }

    /**
     * starts a 1-second ticker that keeps the "Session: HH:MM:SS" label current, and stops
     * it automatically if the window is closed.
     */
    private void startSessionTimer() {
        if (lblSessionTimer == null) return;
        updateSessionTimer(); // show 00:00:00 immediately
        sessionTimeline = new Timeline(new KeyFrame(Duration.seconds(1), e -> updateSessionTimer()));
        sessionTimeline.setCycleCount(Timeline.INDEFINITE);
        sessionTimeline.play();

        lblSessionTimer.sceneProperty().addListener((obs, oldScene, newScene) -> {
            if (newScene != null) {
                newScene.windowProperty().addListener((o2, oldWin, newWin) -> {
                    if (newWin != null) newWin.setOnHidden(ev -> stopSessionTimer());
                });
            }
        });
    }

    /** writes the current elapsed session time into the header label. */
    private void updateSessionTimer() {
        UserSession s = UserSession.getInstance();
        if (s != null && lblSessionTimer != null)
            lblSessionTimer.setText("Session: " + s.getSessionDuration());
    }

    /** stops and releases the session-timer ticker if it's running. */
    private void stopSessionTimer() {
        if (sessionTimeline != null) {
            sessionTimeline.stop();
            sessionTimeline = null;
        }
    }

    /** Locks/unlocks the visitors field and resets its value when the type changes. */
    private void applyTypeSideEffects(String type) {
        if ("SOLO".equals(type)) {
            txtNewVisitors.setText("1");
            txtNewVisitors.setDisable(true);
            txtNewVisitors.setStyle("-fx-background-color:-park-surface-2; -fx-background-radius:8;");
        } else {
            txtNewVisitors.setDisable(false);
            txtNewVisitors.setStyle("");
            // Reset to minimum valid value when switching away from SOLO
            if ("1".equals(txtNewVisitors.getText().trim()))
                txtNewVisitors.setText("2");
        }
    }

    /**
     * loads all live orders from the server and populates the table, newest first.
     *
     * @param event the button-click event
     */
    public void loadOrders(ActionEvent event) {
        lblResult.setText("Loading…");
        ChatClient.lastLiveOrders = null;
        ClientUI.chat.accept(new Message("GET_LIVE_ORDERS", null));
        List<OrderDetail> orders = ChatClient.lastLiveOrders;
        if (orders != null) {
            tableOrders.getItems().setAll(orders);
            lblResult.setText("Loaded " + orders.size() + " orders.");
        } else {
            tableOrders.getItems().clear();
            lblResult.setText("Could not load orders — is the server running?");
        }
    }

    /**
     * validates and sends an {@code UPDATE_ORDER} request.
     * <ul>
     *   <li>SOLO: visitor count forced to 1.</li>
     *   <li>FAMILY: visitor count must be 2-15.</li>
     *   <li>GROUP: visitor count must be 2-15; server additionally checks that
     *               the order's visitor_id is an approved guide.</li>
     * </ul>
     *
     * @param event the button-click event
     */
    public void updateOrder(ActionEvent event) {
        try {
            // ── Order number ─────────────────────────────────────────────────
            String orderNumText = txtOrderNum.getText().trim();
            if (orderNumText.isEmpty()) {
                lblResult.setText("Please enter an order number.");
                return;
            }
            int num = Integer.parseInt(orderNumText);

            // ── Date ─────────────────────────────────────────────────────────
            String date = txtNewDate.getText().trim();
            if (date.isEmpty()) { lblResult.setText("Please enter a new date."); return; }
            try {
                LocalDate newDate = LocalDate.parse(date, DateTimeFormatter.ofPattern("yyyy-MM-dd"));
                if (!newDate.isAfter(LocalDate.now())) {
                    lblResult.setText("Date must be a future date (use YYYY-MM-DD).");
                    return;
                }
            } catch (DateTimeParseException e) {
                lblResult.setText("Invalid date format — please use YYYY-MM-DD.");
                return;
            }

            // ── Visit type ───────────────────────────────────────────────────
            String orderType = cboNewOrderType.getValue();
            if (orderType == null) {
                lblResult.setText("Please select a visit type.");
                return;
            }

            // ── Visitor count (type-specific rules) ──────────────────────────
            String visText = txtNewVisitors.getText().trim();
            if (visText.isEmpty()) {
                lblResult.setText("Please enter the number of visitors.");
                return;
            }
            int vis = Integer.parseInt(visText);

            switch (orderType) {
                case "SOLO":
                    if (vis != 1) {
                        lblResult.setText("SOLO orders must have exactly 1 visitor.");
                        return;
                    }
                    break;
                case "FAMILY":
                    if (vis < 2 || vis > 15) {
                        lblResult.setText("FAMILY orders require 2–15 visitors.");
                        return;
                    }
                    break;
                case "GROUP":
                    if (vis < 2 || vis > 15) {
                        lblResult.setText("GROUP orders require 2–15 visitors " +
                                          "(the guide enters free at the gate).");
                        return;
                    }
                    // Guide eligibility is validated server-side; we rely on the
                    // server's error message to explain why it was rejected.
                    break;
                default:
                    lblResult.setText("Unknown visit type selected.");
                    return;
            }

            Order o = new Order(num, date, vis, orderType);
            ClientUI.chat.accept(new Message("UPDATE_ORDER", o));
            lblResult.setText(ChatClient.lastResult != null ? ChatClient.lastResult : "No response from server.");

        } catch (NumberFormatException e) {
            lblResult.setText("Please enter valid numbers for Order ID and Visitor Count.");
        }
    }

    /**
     * opens a modal panel showing all registered guides.
     * the manager can Approve (sets {@code is_approved=1}) or Reject (deletes row) each guide.
     * the table refreshes automatically after each action.
     *
     * @param event the button-click event
     */
    @FXML
    public void handleManageGuides(ActionEvent event) {
        Stage stage = new Stage();
        stage.initModality(Modality.WINDOW_MODAL);
        stage.initOwner(lblUserName.getScene().getWindow());
        stage.setTitle("GoNature — Manage Guides");

        Label titleLbl = new Label("🧭  Guide Management");
        titleLbl.getStyleClass().add("screen-title");

        Label statusLbl = new Label("Loading…");
        statusLbl.getStyleClass().add("lbl-hint");

        // ── Table ────────────────────────────────────────────────────────────
        TableView<GuideDetail> table = new TableView<>();
        table.getStyleClass().add("table-view");
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        table.setPrefHeight(300);
        VBox.setVgrow(table, javafx.scene.layout.Priority.ALWAYS);

        TableColumn<GuideDetail, String> colId     = new TableColumn<>("ID Number");
        TableColumn<GuideDetail, String> colName   = new TableColumn<>("Name");
        TableColumn<GuideDetail, String> colEmail  = new TableColumn<>("Email");
        TableColumn<GuideDetail, String> colStatus = new TableColumn<>("Status");

        colId.setCellValueFactory(    new PropertyValueFactory<>("idNumber"));
        colName.setCellValueFactory(  d -> new SimpleStringProperty(d.getValue().getFullName()));
        colEmail.setCellValueFactory( new PropertyValueFactory<>("email"));
        colStatus.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().getStatusLabel()));

        // Status cell rendered as a themed chip (approved = green, pending = amber)
        colStatus.setCellFactory(col -> new TableCell<GuideDetail, String>() {
            @Override protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) { setText(null); setGraphic(null); return; }
                Label chip = new Label(item);
                chip.getStyleClass().add("APPROVED".equals(item) ? "chip-confirmed" : "chip-pending");
                setText(null);
                setGraphic(chip);
            }
        });

        // Actions column
        TableColumn<GuideDetail, Void> colActions = new TableColumn<>("Actions");
        colActions.setMinWidth(190);
        colActions.setCellFactory(col -> new TableCell<GuideDetail, Void>() {
            private final Button btnApprove = new Button("✅  Approve");
            private final Button btnReject  = new Button("❌  Reject");
            {
                btnApprove.getStyleClass().add("btn-primary");
                btnApprove.setStyle("-fx-font-size:11px; -fx-padding:4 10; -fx-background-radius:6;");
                btnReject.getStyleClass().add("btn-danger");
                btnReject.setStyle("-fx-font-size:11px; -fx-padding:4 10; -fx-background-radius:6;");

                btnApprove.setOnAction(e -> {
                    GuideDetail g = getTableView().getItems().get(getIndex());
                    ChatClient.lastGuideActionSuccess = null;
                    ChatClient.lastGuideActionFail    = null;
                    ClientUI.chat.accept(new Message("APPROVE_GUIDE", g.getIdNumber()));
                    if (ChatClient.lastGuideActionSuccess != null) {
                        statusLbl.getStyleClass().setAll("lbl-success");
                        statusLbl.setText("✅  Approved: " + g.getFullName());
                    } else {
                        statusLbl.getStyleClass().setAll("lbl-error");
                        statusLbl.setText("❌  " + (ChatClient.lastGuideActionFail != null
                            ? ChatClient.lastGuideActionFail : "Approval failed."));
                    }
                    refreshGuideTable(table, statusLbl);
                });

                btnReject.setOnAction(e -> {
                    GuideDetail g = getTableView().getItems().get(getIndex());
                    Alert confirm = new Alert(Alert.AlertType.CONFIRMATION,
                        "Remove guide " + g.getFullName() + " (ID: " + g.getIdNumber() + ")?",
                        ButtonType.YES, ButtonType.NO);
                    confirm.setTitle("Confirm Rejection");
                    confirm.setHeaderText("This will permanently delete the guide record.");
                    ThemeManager.styleDialog(confirm);
                    confirm.showAndWait().ifPresent(btn -> {
                        if (btn != ButtonType.YES) return;
                        ChatClient.lastGuideActionSuccess = null;
                        ChatClient.lastGuideActionFail    = null;
                        ClientUI.chat.accept(new Message("REJECT_GUIDE", g.getIdNumber()));
                        if (ChatClient.lastGuideActionSuccess != null) {
                            statusLbl.getStyleClass().setAll("lbl-hint");
                            statusLbl.setText("🗑  Removed: " + g.getFullName());
                        } else {
                            statusLbl.getStyleClass().setAll("lbl-error");
                            statusLbl.setText("❌  " + (ChatClient.lastGuideActionFail != null
                                ? ChatClient.lastGuideActionFail : "Rejection failed."));
                        }
                        refreshGuideTable(table, statusLbl);
                    });
                });
            }

            @Override protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                if (empty) { setGraphic(null); return; }
                GuideDetail g = getTableView().getItems().get(getIndex());
                btnApprove.setVisible(g.getIsApproved() == 0); // hide for already-approved
                HBox box = new HBox(8, btnApprove, btnReject);
                box.setAlignment(Pos.CENTER_LEFT);
                setGraphic(box);
            }
        });

        table.getColumns().addAll(colId, colName, colEmail, colStatus, colActions);
        table.setPlaceholder(EmptyState.of(Icons.inbox(34),
            "No guides registered yet",
            "Guides registered by service reps will appear here."));

        Button btnRefresh = new Button("🔄  Refresh");
        btnRefresh.getStyleClass().add("btn-teal");
        btnRefresh.setStyle("-fx-font-size:12px; -fx-padding:7 16; -fx-background-radius:8;");
        btnRefresh.setOnAction(e -> refreshGuideTable(table, statusLbl));

        Button btnClose = new Button("Close");
        btnClose.getStyleClass().add("btn-secondary");
        btnClose.setStyle("-fx-font-size:12px; -fx-padding:7 16; -fx-background-radius:8;");
        btnClose.setOnAction(e -> stage.close());

        javafx.scene.layout.Region spacer = new javafx.scene.layout.Region();
        HBox.setHgrow(spacer, javafx.scene.layout.Priority.ALWAYS);
        HBox btnBar = new HBox(10, btnRefresh, spacer, btnClose);
        btnBar.setAlignment(Pos.CENTER_LEFT);

        VBox header = new VBox(3, titleLbl, statusLbl);
        VBox root = new VBox(14, header, table, btnBar);
        root.getStyleClass().add("screen-bg");
        root.setPadding(new Insets(22));

        Scene scene = new Scene(root, 780, 470);
        java.net.URL css = getClass().getResource("/gui/gonature.css");
        if (css != null) scene.getStylesheets().add(css.toExternalForm());
        ThemeManager.register(scene);   // follow the active light/dark theme
        stage.setScene(scene);

        refreshGuideTable(table, statusLbl);
        stage.centerOnScreen();
        stage.show();
    }

    /** Sends GET_ALL_GUIDES and reloads the guide table, updating the status label. */
    private void refreshGuideTable(TableView<GuideDetail> table, Label statusLbl) {
        ChatClient.lastGuidesList = null;
        ClientUI.chat.accept(new Message("GET_ALL_GUIDES", null));
        List<GuideDetail> guides = ChatClient.lastGuidesList;
        if (guides != null) {
            table.getItems().setAll(guides);
            long pending  = guides.stream().filter(g -> g.getIsApproved() == 0).count();
            long approved = guides.stream().filter(g -> g.getIsApproved() == 1).count();
            statusLbl.getStyleClass().setAll("lbl-hint");
            statusLbl.setText(guides.size() + " guide(s)  —  " + pending
                + " pending  |  " + approved + " approved");
        } else {
            table.getItems().clear();
            statusLbl.getStyleClass().setAll("lbl-error");
            statusLbl.setText("Could not load guides — is the server running?");
        }
    }

    /**
     * opens a modal panel listing all pending park-settings change requests.
     * the department manager can Approve (applies values to the parks table)
     * or Reject (marks the request rejected without touching the parks table).
     * the table refreshes automatically after each action.
     *
     * @param event the button-click event
     */
    @FXML
    public void handlePendingApprovals(ActionEvent event) {
        Stage stage = new Stage();
        stage.initModality(Modality.WINDOW_MODAL);
        stage.initOwner(lblUserName.getScene().getWindow());
        stage.setTitle("GoNature — Pending Park Settings Approvals");

        Label titleLbl = new Label("⚙  Pending Park Settings Requests");
        titleLbl.getStyleClass().add("screen-title");

        Label statusLbl = new Label("Loading…");
        statusLbl.getStyleClass().add("lbl-hint");

        // ── Table ────────────────────────────────────────────────────────────
        TableView<ParkSettingsRequest> table = new TableView<>();
        table.getStyleClass().add("table-view");
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        table.setPrefHeight(320);
        VBox.setVgrow(table, javafx.scene.layout.Priority.ALWAYS);

        TableColumn<ParkSettingsRequest, String> colPark      = new TableColumn<>("Park");
        TableColumn<ParkSettingsRequest, String> colBy        = new TableColumn<>("Requested By");
        TableColumn<ParkSettingsRequest, String> colValues    = new TableColumn<>("Proposed Values");
        TableColumn<ParkSettingsRequest, String> colDate      = new TableColumn<>("Submitted");

        colPark.setCellValueFactory(  d -> new SimpleStringProperty(d.getValue().getParkName()));
        colBy.setCellValueFactory(    d -> new SimpleStringProperty(d.getValue().getRequestedBy()));
        colValues.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().getSummary()));
        colDate.setCellValueFactory(  d -> new SimpleStringProperty(d.getValue().getRequestedAt()));

        // Actions column
        TableColumn<ParkSettingsRequest, Void> colActions = new TableColumn<>("Actions");
        colActions.setMinWidth(190);
        colActions.setCellFactory(col -> new TableCell<ParkSettingsRequest, Void>() {
            private final Button btnApprove = new Button("✅  Approve");
            private final Button btnReject  = new Button("❌  Reject");
            {
                btnApprove.getStyleClass().add("btn-primary");
                btnApprove.setStyle("-fx-font-size:11px; -fx-padding:4 10; -fx-background-radius:6;");
                btnReject.getStyleClass().add("btn-danger");
                btnReject.setStyle("-fx-font-size:11px; -fx-padding:4 10; -fx-background-radius:6;");

                btnApprove.setOnAction(e -> {
                    ParkSettingsRequest r = getTableView().getItems().get(getIndex());
                    ChatClient.lastSettingsActionSuccess = null;
                    ChatClient.lastSettingsActionFail    = null;
                    ClientUI.chat.accept(new Message("APPROVE_SETTINGS", r.getId()));
                    if (ChatClient.lastSettingsActionSuccess != null) {
                        statusLbl.getStyleClass().setAll("lbl-success");
                        statusLbl.setText("✅  Approved request #" + r.getId()
                            + " — " + r.getParkName() + " settings updated.");
                    } else {
                        statusLbl.getStyleClass().setAll("lbl-error");
                        statusLbl.setText("❌  " + (ChatClient.lastSettingsActionFail != null
                            ? ChatClient.lastSettingsActionFail : "Approval failed."));
                    }
                    refreshRequestsTable(table, statusLbl);
                });

                btnReject.setOnAction(e -> {
                    ParkSettingsRequest r = getTableView().getItems().get(getIndex());
                    Alert confirm = new Alert(Alert.AlertType.CONFIRMATION,
                        "Reject settings request #" + r.getId() + " from " + r.getRequestedBy()
                        + " for " + r.getParkName() + "?",
                        ButtonType.YES, ButtonType.NO);
                    confirm.setTitle("Confirm Rejection");
                    confirm.setHeaderText("The proposed values will not be applied.");
                    ThemeManager.styleDialog(confirm);
                    confirm.showAndWait().ifPresent(btn -> {
                        if (btn != ButtonType.YES) return;
                        ChatClient.lastSettingsActionSuccess = null;
                        ChatClient.lastSettingsActionFail    = null;
                        ClientUI.chat.accept(new Message("REJECT_SETTINGS", r.getId()));
                        if (ChatClient.lastSettingsActionSuccess != null) {
                            statusLbl.getStyleClass().setAll("lbl-hint");
                            statusLbl.setText("🗑  Rejected request #" + r.getId()
                                + " from " + r.getRequestedBy() + ".");
                        } else {
                            statusLbl.getStyleClass().setAll("lbl-error");
                            statusLbl.setText("❌  " + (ChatClient.lastSettingsActionFail != null
                                ? ChatClient.lastSettingsActionFail : "Rejection failed."));
                        }
                        refreshRequestsTable(table, statusLbl);
                    });
                });
            }

            @Override protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                if (empty) { setGraphic(null); return; }
                HBox box = new HBox(8, btnApprove, btnReject);
                box.setAlignment(Pos.CENTER_LEFT);
                setGraphic(box);
            }
        });

        table.getColumns().addAll(colPark, colBy, colValues, colDate, colActions);
        table.setPlaceholder(EmptyState.of(Icons.inbox(34),
            "All caught up",
            "Pending park-settings requests will appear here."));

        Button btnRefresh = new Button("🔄  Refresh");
        btnRefresh.getStyleClass().add("btn-teal");
        btnRefresh.setStyle("-fx-font-size:12px; -fx-padding:7 16; -fx-background-radius:8;");
        btnRefresh.setOnAction(e -> refreshRequestsTable(table, statusLbl));

        Button btnClose = new Button("Close");
        btnClose.getStyleClass().add("btn-secondary");
        btnClose.setStyle("-fx-font-size:12px; -fx-padding:7 16; -fx-background-radius:8;");
        btnClose.setOnAction(e -> stage.close());

        javafx.scene.layout.Region spacer = new javafx.scene.layout.Region();
        HBox.setHgrow(spacer, javafx.scene.layout.Priority.ALWAYS);
        HBox btnBar = new HBox(10, btnRefresh, spacer, btnClose);
        btnBar.setAlignment(Pos.CENTER_LEFT);

        VBox header = new VBox(3, titleLbl, statusLbl);
        VBox root = new VBox(14, header, table, btnBar);
        root.getStyleClass().add("screen-bg");
        root.setPadding(new Insets(22));

        Scene scene = new Scene(root, 860, 480);
        java.net.URL css = getClass().getResource("/gui/gonature.css");
        if (css != null) scene.getStylesheets().add(css.toExternalForm());
        ThemeManager.register(scene);   // follow the active light/dark theme
        stage.setScene(scene);

        refreshRequestsTable(table, statusLbl);
        stage.centerOnScreen();
        stage.show();
    }

    /**
     * opens a modal panel listing all pending promotion requests across every park.
     * the department manager can Approve (sets the promotion ACTIVE so it affects pricing)
     * or Reject (marks it REJECTED). the table refreshes automatically after each action.
     *
     * @param event the button-click event
     */
    @FXML
    public void handlePendingPromotions(ActionEvent event) {
        Stage stage = new Stage();
        stage.initModality(Modality.WINDOW_MODAL);
        stage.initOwner(lblUserName.getScene().getWindow());
        stage.setTitle("GoNature — Pending Promotions");

        Label titleLbl = new Label("🎉  Pending Promotions");
        titleLbl.getStyleClass().add("screen-title");

        Label statusLbl = new Label("Loading…");
        statusLbl.getStyleClass().add("lbl-hint");

        TableView<Promotion> table = new TableView<>();
        table.getStyleClass().add("table-view");
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        table.setPrefHeight(320);
        VBox.setVgrow(table, javafx.scene.layout.Priority.ALWAYS);

        TableColumn<Promotion, String> colPark = new TableColumn<>("Park");
        TableColumn<Promotion, String> colDesc = new TableColumn<>("Description");
        TableColumn<Promotion, String> colDisc = new TableColumn<>("Discount");
        TableColumn<Promotion, String> colFrom = new TableColumn<>("Start");
        TableColumn<Promotion, String> colTo   = new TableColumn<>("End");
        TableColumn<Promotion, String> colBy   = new TableColumn<>("Submitted By");

        colPark.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().getParkName()));
        colDesc.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().getDescription()));
        colDisc.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().getDiscountLabel()));
        colFrom.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().getStartDate()));
        colTo.setCellValueFactory(  d -> new SimpleStringProperty(d.getValue().getEndDate()));
        colBy.setCellValueFactory(  d -> new SimpleStringProperty(d.getValue().getSubmittedBy()));

        TableColumn<Promotion, Void> colActions = new TableColumn<>("Actions");
        colActions.setMinWidth(190);
        colActions.setCellFactory(col -> new TableCell<Promotion, Void>() {
            private final Button btnApprove = new Button("✅  Approve");
            private final Button btnReject  = new Button("❌  Reject");
            {
                btnApprove.getStyleClass().add("btn-primary");
                btnApprove.setStyle("-fx-font-size:11px; -fx-padding:4 10; -fx-background-radius:6;");
                btnReject.getStyleClass().add("btn-danger");
                btnReject.setStyle("-fx-font-size:11px; -fx-padding:4 10; -fx-background-radius:6;");

                btnApprove.setOnAction(e -> {
                    Promotion p = getTableView().getItems().get(getIndex());
                    ChatClient.lastPromotionActionSuccess = null;
                    ChatClient.lastPromotionActionFail    = null;
                    ClientUI.chat.accept(new Message("APPROVE_PROMOTION", p.getId()));
                    if (ChatClient.lastPromotionActionSuccess != null) {
                        statusLbl.getStyleClass().setAll("lbl-success");
                        statusLbl.setText("✅  Approved: " + p.getDiscountLabel()
                            + " at " + p.getParkName() + " is now active.");
                    } else {
                        statusLbl.getStyleClass().setAll("lbl-error");
                        statusLbl.setText("❌  " + (ChatClient.lastPromotionActionFail != null
                            ? ChatClient.lastPromotionActionFail : "Approval failed."));
                    }
                    refreshPromotionsTable(table, statusLbl);
                });

                btnReject.setOnAction(e -> {
                    Promotion p = getTableView().getItems().get(getIndex());
                    Alert confirm = new Alert(Alert.AlertType.CONFIRMATION,
                        "Reject the " + p.getDiscountLabel() + " promotion for "
                        + p.getParkName() + "?",
                        ButtonType.YES, ButtonType.NO);
                    confirm.setTitle("Confirm Rejection");
                    confirm.setHeaderText("The promotion will not be applied.");
                    ThemeManager.styleDialog(confirm);
                    confirm.showAndWait().ifPresent(btn -> {
                        if (btn != ButtonType.YES) return;
                        ChatClient.lastPromotionActionSuccess = null;
                        ChatClient.lastPromotionActionFail    = null;
                        ClientUI.chat.accept(new Message("REJECT_PROMOTION", p.getId()));
                        if (ChatClient.lastPromotionActionSuccess != null) {
                            statusLbl.getStyleClass().setAll("lbl-hint");
                            statusLbl.setText("🗑  Rejected the promotion for " + p.getParkName() + ".");
                        } else {
                            statusLbl.getStyleClass().setAll("lbl-error");
                            statusLbl.setText("❌  " + (ChatClient.lastPromotionActionFail != null
                                ? ChatClient.lastPromotionActionFail : "Rejection failed."));
                        }
                        refreshPromotionsTable(table, statusLbl);
                    });
                });
            }

            @Override protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                if (empty) { setGraphic(null); return; }
                HBox box = new HBox(8, btnApprove, btnReject);
                box.setAlignment(Pos.CENTER_LEFT);
                setGraphic(box);
            }
        });

        table.getColumns().addAll(colPark, colDesc, colDisc, colFrom, colTo, colBy, colActions);
        table.setPlaceholder(EmptyState.of(Icons.inbox(34),
            "All caught up",
            "Pending promotions submitted by park managers will appear here."));

        Button btnRefresh = new Button("🔄  Refresh");
        btnRefresh.getStyleClass().add("btn-teal");
        btnRefresh.setStyle("-fx-font-size:12px; -fx-padding:7 16; -fx-background-radius:8;");
        btnRefresh.setOnAction(e -> refreshPromotionsTable(table, statusLbl));

        Button btnClose = new Button("Close");
        btnClose.getStyleClass().add("btn-secondary");
        btnClose.setStyle("-fx-font-size:12px; -fx-padding:7 16; -fx-background-radius:8;");
        btnClose.setOnAction(e -> stage.close());

        javafx.scene.layout.Region spacer = new javafx.scene.layout.Region();
        HBox.setHgrow(spacer, javafx.scene.layout.Priority.ALWAYS);
        HBox btnBar = new HBox(10, btnRefresh, spacer, btnClose);
        btnBar.setAlignment(Pos.CENTER_LEFT);

        VBox header = new VBox(3, titleLbl, statusLbl);
        VBox root = new VBox(14, header, table, btnBar);
        root.getStyleClass().add("screen-bg");
        root.setPadding(new Insets(22));

        Scene scene = new Scene(root, 920, 480);
        java.net.URL css = getClass().getResource("/gui/gonature.css");
        if (css != null) scene.getStylesheets().add(css.toExternalForm());
        ThemeManager.register(scene);
        stage.setScene(scene);

        refreshPromotionsTable(table, statusLbl);
        stage.centerOnScreen();
        stage.show();
    }

    /** Sends GET_PENDING_PROMOTIONS and reloads the promotions table. */
    private void refreshPromotionsTable(TableView<Promotion> table, Label statusLbl) {
        ChatClient.lastPendingPromotions = null;
        ClientUI.chat.accept(new Message("GET_PENDING_PROMOTIONS", null));
        List<Promotion> promos = ChatClient.lastPendingPromotions;
        if (promos != null) {
            table.getItems().setAll(promos);
            statusLbl.getStyleClass().setAll("lbl-hint");
            statusLbl.setText(promos.isEmpty()
                ? "No pending promotions."
                : promos.size() + " pending promotion(s) awaiting approval.");
        } else {
            table.getItems().clear();
            statusLbl.getStyleClass().setAll("lbl-error");
            statusLbl.setText("Could not load promotions — is the server running?");
        }
    }

    /** Sends GET_PENDING_SETTINGS and reloads the requests table. */
    private void refreshRequestsTable(TableView<ParkSettingsRequest> table, Label statusLbl) {
        ChatClient.lastPendingSettings = null;
        ClientUI.chat.accept(new Message("GET_PENDING_SETTINGS", null));
        List<ParkSettingsRequest> reqs = ChatClient.lastPendingSettings;
        if (reqs != null) {
            table.getItems().setAll(reqs);
            statusLbl.getStyleClass().setAll("lbl-hint");
            statusLbl.setText(reqs.isEmpty()
                ? "No pending requests."
                : reqs.size() + " pending request(s) awaiting approval.");
        } else {
            table.getItems().clear();
            statusLbl.getStyleClass().setAll("lbl-error");
            statusLbl.setText("Could not load requests — is the server running?");
        }
    }

    /**
     * shows the department-manager help dialog.
     *
     * @param event the button-click event
     */
    public void handleHelp(ActionEvent event) {
        UserSession s = UserSession.getInstance();
        String content =
            "DEPARTMENT MANAGER — QUICK GUIDE\n\n" +
            "MANAGING ORDERS\n" +
            "  • Click 'Load All Orders' to view all live bookings.\n" +
            "  • Columns: Order #, Park, Visitor ID, Date, Time, Visitors, Type, Status, Booked On.\n" +
            "  • To update: click a row to pre-fill the form, or enter the Order Number manually.\n" +
            "    Set a new Date (YYYY-MM-DD), choose a Visit Type, and adjust visitor count:\n" +
            "      SOLO   → exactly 1 visitor.\n" +
            "      FAMILY → 2–15 visitors.\n" +
            "      GROUP  → 2–15 visitors; only allowed when the visitor is an approved guide.\n" +
            "    Then click 'Update Order'.\n\n" +
            "REPORTS DASHBOARD\n" +
            "  • Click 'View Reports Dashboard' to open the analytics panel.\n\n" +
            "  Visitor Report — bar chart of daily visitors for the last 30 days,\n" +
            "    split by Solo vs Group bookings.\n\n" +
            "  Cancellation Report — table of all cancelled and no-show orders,\n" +
            "    plus a day-of-week distribution chart with the average cancellations per weekday.\n\n" +
            "  Usage Report — line chart of hourly park capacity utilisation.\n\n" +
            "  • Use the park filter to narrow any report to a specific park.";
        showHelp("GoNature — Help (DEPARTMENT_MANAGER)", content);
    }

    /**
     * @param title   the dialog header text
     * @param content the help body text shown in the scrollable text area
     */
    private void showHelp(String title, String content) {
        HelpDialog.show(title, content);
    }

    /**
     * opens the Reports Dashboard as a modal (no park lock for dept. managers).
     *
     * @param event the button-click event
     */
    public void handleViewReports(ActionEvent event) {
        try {
            Stage reportsStage = new Stage();
            reportsStage.initModality(Modality.WINDOW_MODAL);
            reportsStage.initOwner(lblUserName.getScene().getWindow());
            new ReportsController().start(reportsStage);
        } catch (Exception e) {
            lblResult.setText("We couldn't open the reports dashboard. Please try again.");
            e.printStackTrace();
        }
    }

    /**
     * notifies the server, clears the session, and returns to the login screen.
     *
     * @param event the button-click event
     */
    @FXML
    public void handleLogout(ActionEvent event) {
        stopSessionTimer();   // stop before clearing the session so the clock doesn't tick on a null user
        if (ClientUI.chat != null) ClientUI.chat.sendLogout();
        UserSession.clear();
        Stage stage = (Stage) lblUserName.getScene().getWindow();
        try {
            new LoginController().start(stage);
        } catch (Exception e) {
            if (ClientUI.chat != null) ClientUI.chat.disconnect();
            Platform.exit();
        }
    }

    /** @param event the button-click event */
    public void getExitBtn(ActionEvent event) {
        stopSessionTimer();
        if (ClientUI.chat != null) ClientUI.chat.disconnect();
        Platform.exit();
    }

    /**
     * @param primaryStage the stage in which to show the screen
     * @throws Exception if the FXML file cannot be loaded
     */
    public void start(Stage primaryStage) throws Exception {
        Parent root = FXMLLoader.load(getClass().getResource("/gui/AcademicFrame.fxml"));
        Scene scene = new Scene(root);
        ThemeManager.register(scene);   // apply the current light/dark theme to this screen
        primaryStage.setTitle("GoNature — Management Dashboard");
        primaryStage.setScene(scene);
        primaryStage.centerOnScreen();
        primaryStage.show();
        Animations.introduce(root);     // subtle fade-and-rise on load
    }
}
