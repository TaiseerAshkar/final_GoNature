package gui;

import client.ChatClient;
import client.ClientUI;
import client.NotificationCenter;
import logic.ExitRequest;
import logic.Message;
import logic.OrderDetail;
import logic.Park;
import client.UserSession;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ChoiceDialog;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.DialogPane;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.Separator;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.shape.Rectangle;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.util.Duration;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * controller for the visitor / guide home screen ({@code VisitorFrame.fxml}).
 *
 * <p>shows a personalised welcome message, action buttons, and the simulated
 * notification inbox populated by {@link NotificationCenter}.
 * on load it also checks for any confirmed bookings tomorrow and adds
 * a reminder notification automatically.
 */
public class VisitorController {

    @FXML private StackPane       heroBanner;
    @FXML private Label           lblEyebrow;
    @FXML private Label           lblWelcome;
    @FXML private Label           lblRoleBadge;
    @FXML private Label           lblInfo;
    @FXML private Label           lblUpcoming;

    // header controls
    @FXML private Label           lblSessionTimer;
    @FXML private Button          btnTheme;

    // action tiles (built in code → see buildActionTiles)
    @FXML private GridPane        actionGrid;
    private Button                btnConfirmVisit;   // kept so we can mark it done
    private Label                 lblConfirmTitle;
    private Label                 lblConfirmDesc;

    // notification panel
    @FXML private Label           lblNotifTitle;
    @FXML private Label           lblNotifBadge;
    @FXML private Button          btnRefreshNotif;
    @FXML private VBox            boxNoNotif;
    @FXML private ListView<String> lstNotifications;

    /** ticks once a second to refresh the logged-in-duration label in the header. */
    private Timeline sessionTimeline;

    private static final DateTimeFormatter DATE_FMT =
        DateTimeFormatter.ofPattern("yyyy-MM-dd");

    /**
     * populates welcome/role labels, wires the notification list cell factory,
     * checks for upcoming-visit reminders, and renders the notification inbox.
     */
    @FXML
    public void initialize() {
        UserSession s = UserSession.getInstance();
        lblWelcome.setText("Welcome, " + s.getFullName() + "!");
        lblRoleBadge.setText(s.getRole());
        switch (s.getRole()) {
            case "GUIDE":
                lblEyebrow.setText("GUIDE DASHBOARD");
                lblInfo.setText(
                    "As a certified guide you can lead group visits and book solo visits.\n" +
                    "You enter free when leading a group.");
                break;
            case "SUBSCRIBER":
                lblEyebrow.setText("MEMBER DASHBOARD");
                lblInfo.setText(
                    "Family Member Club — your ID gives you a 15% discount on walk-in visits,\n" +
                    "on top of the standard 15% advance-booking discount.");
                break;
            default: // VISITOR
                lblEyebrow.setText("VISITOR DASHBOARD");
                lblInfo.setText(
                    "As a visitor you can browse available parks,\n" +
                    "book visits, and manage your existing reservations.");
        }

        // round the hero banner's corners and clip the ridgeline inside them
        if (heroBanner != null) {
            Rectangle clip = new Rectangle();
            clip.widthProperty().bind(heroBanner.widthProperty());
            clip.heightProperty().bind(heroBanner.heightProperty());
            clip.setArcWidth(32);
            clip.setArcHeight(32);
            heroBanner.setClip(clip);
        }

        // build the professional action-tile grid (icon chip + title + description)
        buildActionTiles();

        // notification card chrome
        lblNotifTitle.setGraphic(Icons.inbox(18));
        lblNotifTitle.setGraphicTextGap(8);
        btnRefreshNotif.setGraphic(Icons.refresh(13));
        btnRefreshNotif.setGraphicTextGap(6);

        // designed empty state for the inbox
        boxNoNotif.getChildren().setAll(EmptyState.of(
            Icons.inbox(34),
            "No notifications yet",
            "They'll appear here after booking, cancellation, or when a waiting-list spot opens."));

        // wrap long notification lines so they don't get clipped
        lstNotifications.setCellFactory(lv -> new ListCell<>() {
            { setWrapText(true); }
            @Override protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : item);
                setPrefWidth(0); // lets the cell fill ListView width
            }
        });

        ThemeManager.installToggle(btnTheme);
        startSessionTimer();

        checkUpcomingReminders();
        updateUpcomingCount();
        refreshNotifications();
        // Defer the waitlist dialog until after the stage is shown and the scene is fully
        // attached.  initialize() fires during FXMLLoader.load() — before primaryStage.show()
        // — so showing a dialog here would (a) pop up before the visitor UI is visible and
        // (b) have a null scene owner, breaking dark-mode styling.  Platform.runLater()
        // queues the call for the next JavaFX pulse after show() completes.
        Platform.runLater(this::checkWaitlistPromotion);
    }

    // ── Action-tile grid ──────────────────────────────────────────────────────

    /**
     * builds the home-screen action grid: a compact 2-up grid of tiles, each a small
     * colour icon-chip beside a bold title and a short muted description. "Book a Visit"
     * carries a filled pine chip to read as the primary action without changing size.
     */
    private void buildActionTiles() {
        if (actionGrid == null) return;

        Button book = tile("Book a Visit", "Reserve a park visit",
            Icons.pine(20, Icons.CREAM), "tile-chip-pine", this::handleBookVisit);
        book.getStyleClass().add("action-tile-green");

        Button view = tile("My Reservations", "See and manage bookings",
            Icons.clipboard(20, "ikon-teal"), "tile-chip-teal", this::handleViewOrders);
        view.getStyleClass().add("action-tile-green");

        // confirm tile keeps label refs so it can switch to a "done" state after confirming
        lblConfirmTitle = new Label("Confirm My Visit");
        lblConfirmTitle.getStyleClass().add("tile-title");
        lblConfirmDesc = new Label("Lock in tomorrow's visit");
        lblConfirmDesc.getStyleClass().add("tile-desc");
        btnConfirmVisit = assembleTile(Icons.checkCircle(20, "ikon-lake"),
            "tile-chip-lake", lblConfirmTitle, lblConfirmDesc);
        btnConfirmVisit.getStyleClass().add("action-tile-lake");
        btnConfirmVisit.setOnAction(this::handleConfirmVisit);

        Button exit = tile("Register My Exit", "Log your departure",
            Icons.exit(20, "ikon-gold"), "tile-chip-gold", this::handleRegisterExit);
        exit.getStyleClass().add("action-tile-gold");

        Button cancel = tile("Cancel a Reservation", "Cancel an upcoming booking",
            Icons.xCircle(20, "ikon-rust"), "tile-chip-rust", this::handleViewOrders);
        cancel.getStyleClass().add("action-tile-rust");

        actionGrid.add(book,            0, 0);
        actionGrid.add(view,            1, 0);
        actionGrid.add(btnConfirmVisit, 0, 1);
        actionGrid.add(exit,            1, 1);
        actionGrid.add(cancel,          0, 2);
        GridPane.setColumnSpan(cancel, 2);
    }

    /** builds a compact action tile (chip + title + short description). */
    private Button tile(String title, String desc, Node icon, String chipClass,
                        EventHandler<ActionEvent> handler) {
        Label t = new Label(title);
        t.getStyleClass().add("tile-title");
        Label d = new Label(desc);
        d.getStyleClass().add("tile-desc");
        Button b = assembleTile(icon, chipClass, t, d);
        b.setOnAction(handler);
        return b;
    }

    /** assembles a chip + (title over description) into a horizontal {@code .action-tile}. */
    private Button assembleTile(Node icon, String chipClass, Label title, Label desc) {
        StackPane chip = new StackPane(icon);
        chip.getStyleClass().addAll("tile-chip", chipClass);

        // keep titles at their full preferred width so the tile label never ellipsizes
        // (e.g. "View My Reservati…") when the grid cell is tight.
        title.setMinWidth(Region.USE_PREF_SIZE);

        VBox text = new VBox(1, title, desc);
        text.setAlignment(Pos.CENTER_LEFT);

        HBox row = new HBox(11, chip, text);
        row.setAlignment(Pos.CENTER_LEFT);

        Button b = new Button();
        b.setGraphic(row);
        b.getStyleClass().add("action-tile");
        b.setMaxWidth(Double.MAX_VALUE);
        return b;
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

    /**
     * counts the visitor's active upcoming reservations (status PENDING or CONFIRMED
     * with a visit date today or later) and shows a friendly summary on the home card.
     * reuses the order list already fetched by {@link #checkUpcomingReminders()}.
     */
    private void updateUpcomingCount() {
        if (lblUpcoming == null) return;
        List<OrderDetail> orders = ChatClient.lastMyOrders;
        if (orders == null) { lblUpcoming.setText("No upcoming reservations."); return; }

        String today = LocalDate.now().format(DATE_FMT);
        List<OrderDetail> upcoming = orders.stream()
            .filter(o -> ("PENDING".equals(o.getStatus()) || "CONFIRMED".equals(o.getStatus()))
                && o.getVisitDate() != null
                && o.getVisitDate().compareTo(today) >= 0)
            .sorted(Comparator.comparing(OrderDetail::getVisitDate)
                .thenComparing(OrderDetail::getVisitTime))
            .collect(Collectors.toList());

        if (upcoming.isEmpty()) {
            lblUpcoming.setText("No upcoming reservations.");
            return;
        }

        OrderDetail next = upcoming.get(0);
        long daysUntil = LocalDate.parse(next.getVisitDate(), DATE_FMT)
            .toEpochDay() - LocalDate.now().toEpochDay();
        String when = daysUntil == 0 ? "Today"
                    : daysUntil == 1 ? "Tomorrow"
                    : "In " + daysUntil + " days";
        String summary = "Next: " + next.getParkName() + "  •  " + when
            + " at " + next.getVisitTime()
            + "  (" + next.getNumVisitors()
            + " visitor" + (next.getNumVisitors() == 1 ? "" : "s") + ")";
        if (upcoming.size() > 1)
            summary += "  +" + (upcoming.size() - 1) + " more";
        lblUpcoming.setText(summary);
    }

    // ── Notification panel ────────────────────────────────────────────────────

    /**
     * refreshes the notification panel from {@link NotificationCenter}.
     * re-checks for upcoming-visit reminders before rendering.
     *
     * @param event the button-click event
     */
    @FXML
    public void handleRefreshNotifications(ActionEvent event) {
        checkUpcomingReminders();
        checkWaitlistPromotion();
        updateUpcomingCount();
        refreshNotifications();
    }

    /**
     * syncs the ListView (and badge count) with the current contents of
     * {@link NotificationCenter}. toggles the empty-state label as needed.
     */
    private void refreshNotifications() {
        List<String> all = NotificationCenter.getAll();
        int count = all.size();

        lblNotifBadge.setText(String.valueOf(count));
        lblNotifBadge.setVisible(count > 0);
        lblNotifBadge.setManaged(count > 0);

        boolean empty = count == 0;
        boxNoNotif.setVisible(empty);
        boxNoNotif.setManaged(empty);
        lstNotifications.setVisible(!empty);
        lstNotifications.setManaged(!empty);

        if (!empty) {
            lstNotifications.getItems().setAll(all);
        }
    }

    /**
     * fetches pending waitlist-promotion orders for this visitor. for each one shows a
     * styled "Good News!" popup offering immediate confirmation. also adds an inbox entry
     * and updates the Confirm tile. safe to call multiple times (deduplicates by order ID).
     */
    private void checkWaitlistPromotion() {
        UserSession s = UserSession.getInstance();
        if (s == null || s.getUserId() == null) return;

        ChatClient.lastPendingWaitlistOrders = null;
        ClientUI.chat.accept(new Message("GET_PENDING_WAITLIST_ORDERS", s.getUserId()));
        List<OrderDetail> pending = ChatClient.lastPendingWaitlistOrders;
        if (pending == null || pending.isEmpty()) return;

        boolean anyConfirmedNow = false;
        for (OrderDetail o : pending) {
            // always add an inbox entry (deduplicated per order per session)
            String marker = "Order #" + o.getId();
            boolean alreadyAdded = NotificationCenter.getAll().stream()
                .anyMatch(n -> n.contains(marker) && n.contains("waitlist spot"));
            if (!alreadyAdded) {
                String deadline = o.getConfirmationDeadline() != null
                    ? o.getConfirmationDeadline() : "soon";
                NotificationCenter.add("🎉",
                    "A spot opened up for you! Your waitlist spot for "
                    + o.getParkName() + " on " + o.getVisitDate()
                    + " at " + o.getVisitTime()
                    + " (" + marker + ") is waiting. Please confirm before " + deadline + ".");
            }
            // show the "Good News!" styled dialog and confirm inline if the visitor chooses
            if (showWaitlistPromotionDialog(o)) anyConfirmedNow = true;
        }

        // update the Confirm tile
        if (anyConfirmedNow && btnConfirmVisit != null) {
            btnConfirmVisit.setDisable(true);
            if (lblConfirmTitle != null) lblConfirmTitle.setText("Visit Confirmed");
            if (lblConfirmDesc  != null) lblConfirmDesc.setText("Your booking is locked in");
        } else if (btnConfirmVisit != null) {
            btnConfirmVisit.setDisable(false);
            if (lblConfirmTitle != null) lblConfirmTitle.setText("Confirm Waitlist Spot!");
            if (lblConfirmDesc  != null) {
                String deadline = pending.get(0).getConfirmationDeadline();
                lblConfirmDesc.setText(deadline != null
                    ? "Spot waiting — confirm by " + deadline
                    : "A waitlist spot is waiting for you!");
            }
        }
    }

    /**
     * shows a GoNature-themed "Good News!" dialog for a single waitlist-promotion order.
     * "Confirm Now" immediately sends {@code CONFIRM_VISIT} and returns {@code true}.
     * "Later" dismisses without confirming and returns {@code false}.
     */
    private boolean showWaitlistPromotionDialog(OrderDetail o) {
        String deadline = o.getConfirmationDeadline() != null
            ? o.getConfirmationDeadline() : "soon";

        ButtonType confirmBtn = new ButtonType("Confirm Now",  ButtonBar.ButtonData.OK_DONE);
        ButtonType laterBtn   = new ButtonType("Later",        ButtonBar.ButtonData.CANCEL_CLOSE);

        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("GoNature — Waitlist Spot Available");
        alert.setHeaderText("🎉  Good News!");
        alert.getButtonTypes().setAll(confirmBtn, laterBtn);
        alert.setContentText(
            "A spot opened up for you!\n\n"
            + "Park:      " + o.getParkName()    + "\n"
            + "Date:      " + o.getVisitDate()   + "\n"
            + "Time:      " + o.getVisitTime()   + "\n"
            + "Visitors:  " + o.getNumVisitors() + "\n\n"
            + "Confirm your visit before " + deadline + ".\n"
            + "If you don't confirm in time, the spot will pass to the next visitor.\n\n"
            + "Pressing \"Later\" will dismiss this dialog — you can still confirm\n"
            + "by clicking the \"Confirm Waitlist Spot\" tile on your home screen.");

        if (lblWelcome.getScene() != null)
            alert.initOwner(lblWelcome.getScene().getWindow());
        alert.initModality(Modality.WINDOW_MODAL);
        ThemeManager.styleDialog(alert);
        alert.getDialogPane().getStyleClass().add("help-adaptive");

        Optional<ButtonType> result = alert.showAndWait();
        if (!result.isPresent() || result.get() != confirmBtn) return false;

        // visitor chose "Confirm Now"; send confirmation to server
        ChatClient.lastConfirmVisitOk    = null;
        ChatClient.lastConfirmVisitError = null;
        ClientUI.chat.accept(new Message("CONFIRM_VISIT", o.getId()));

        if (Boolean.TRUE.equals(ChatClient.lastConfirmVisitOk)) {
            NotificationCenter.add("✅",
                "Your visit is confirmed! Your waitlist spot for "
                + o.getParkName() + " on " + o.getVisitDate()
                + " at " + o.getVisitTime()
                + " (Order #" + o.getId() + ") is now locked in. See you there!");
            return true;
        } else {
            String err = ChatClient.lastConfirmVisitError != null
                ? ChatClient.lastConfirmVisitError
                : "Could not confirm Order #" + o.getId()
                  + ". Your window may have expired — please check My Reservations.";
            NotificationCenter.add("⚠️", err);
            return false;
        }
    }

    /**
     * fetches the visitor's orders and adds a reminder notification for any
     * confirmed/pending booking that falls tomorrow. only adds the reminder once
     * per session (checked by scanning existing entries).
     */
    private void checkUpcomingReminders() {
        UserSession s = UserSession.getInstance();
        if (s == null || s.getUserId() == null) return;

        ChatClient.lastMyOrders = null;
        ClientUI.chat.accept(new Message("GET_MY_ORDERS", s.getUserId()));
        List<OrderDetail> orders = ChatClient.lastMyOrders;
        if (orders == null || orders.isEmpty()) return;

        String tomorrow = LocalDate.now().plusDays(1).format(DATE_FMT);

        boolean hasTomorrowOrders = false;
        boolean anyUnconfirmed    = false;

        for (OrderDetail o : orders) {
            if (!tomorrow.equals(o.getVisitDate())) continue;
            if (!"CONFIRMED".equals(o.getStatus()) && !"PENDING".equals(o.getStatus())) continue;

            hasTomorrowOrders = true;

            // visitor already confirmed this order — add a one-time "already confirmed" notice
            if (NotificationCenter.isVisitConfirmed(o.getId())) {
                String confirmedTag = "(Order #" + o.getId() + " confirmed)";
                boolean alreadyNotified = NotificationCenter.getAll().stream()
                    .anyMatch(n -> n.contains(confirmedTag));
                if (!alreadyNotified) {
                    NotificationCenter.add("✅",
                        "Your visit to " + o.getParkName()
                        + " tomorrow at " + o.getVisitTime()
                        + " is confirmed — you're all set! " + confirmedTag);
                }
                continue;
            }

            anyUnconfirmed = true;

            // dedup per order ID — the tag "(Order #X)" is embedded in the notification text
            String orderTag = "(Order #" + o.getId() + ")";
            boolean alreadyAdded = NotificationCenter.getAll().stream()
                .anyMatch(n -> n.contains("🔔") && n.contains(orderTag));
            if (!alreadyAdded) {
                NotificationCenter.add("🔔",
                    "Reminder: your visit to " + o.getParkName()
                    + " is tomorrow at " + o.getVisitTime()
                    + " " + orderTag + ". Please arrive 15 minutes early.");
            }
        }

        // if all tomorrow visits are already confirmed, reflect that in the tile
        if (hasTomorrowOrders && !anyUnconfirmed && btnConfirmVisit != null) {
            btnConfirmVisit.setDisable(true);
            if (lblConfirmTitle != null) lblConfirmTitle.setText("Visit Confirmed");
            if (lblConfirmDesc  != null) lblConfirmDesc.setText("Your booking is locked in");
        }
    }

    // ── Button handlers ───────────────────────────────────────────────────────

    /**
     * opens the booking form as a modal. after it closes the notification panel
     * will show the new booking notification on the next refresh.
     *
     * @param event the button-click event
     */
    @FXML
    public void handleBookVisit(ActionEvent event) {
        try {
            Stage bookingStage = new Stage();
            bookingStage.initModality(Modality.WINDOW_MODAL);
            bookingStage.initOwner(lblWelcome.getScene().getWindow());
            // refresh notifications when the booking modal closes
            bookingStage.setOnHidden(e -> refreshNotifications());
            new BookingController().start(bookingStage);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * opens My Orders as a modal. after it closes the notification panel
     * will reflect any cancellation notifications that were added.
     *
     * @param event the button-click event
     */
    @FXML
    public void handleViewOrders(ActionEvent event) {
        try {
            Stage ordersStage = new Stage();
            ordersStage.initModality(Modality.WINDOW_MODAL);
            ordersStage.initOwner(lblWelcome.getScene().getWindow());
            // refresh notifications when the orders modal closes
            ordersStage.setOnHidden(e -> refreshNotifications());
            new MyOrdersController().start(ordersStage);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * confirms all of the visitor's tomorrow's PENDING/CONFIRMED bookings so the server
     * will not auto-cancel them at the end of the 2-hour reminder window.
     * if there are multiple eligible orders a choice dialog is shown.
     *
     * @param event the button-click event
     */
    @FXML
    public void handleConfirmVisit(ActionEvent event) {
        UserSession s = UserSession.getInstance();
        if (s == null || s.getUserId() == null) return;

        // ── Waitlist-promotion path (takes priority) ──────────────────────────
        // check for any pending orders that came from a waitlist promotion and
        // still have a live 1-hour confirmation window.
        ChatClient.lastPendingWaitlistOrders = null;
        ClientUI.chat.accept(new Message("GET_PENDING_WAITLIST_ORDERS", s.getUserId()));
        List<OrderDetail> waitlistPending = ChatClient.lastPendingWaitlistOrders;

        if (waitlistPending != null && !waitlistPending.isEmpty()) {
            List<OrderDetail> toConfirm = new ArrayList<>();
            if (waitlistPending.size() == 1) {
                toConfirm.add(waitlistPending.get(0));
            } else {
                List<String> labels = waitlistPending.stream()
                    .map(o -> "Order #" + o.getId()
                        + " — " + o.getParkName()
                        + " on " + o.getVisitDate()
                        + " at " + o.getVisitTime()
                        + " (confirm by " + o.getConfirmationDeadline() + ")")
                    .collect(Collectors.toList());
                ChoiceDialog<String> dlg = new ChoiceDialog<>(labels.get(0), labels);
                dlg.setTitle("Confirm Waitlist Spot");
                dlg.setHeaderText("You have " + waitlistPending.size()
                    + " waitlist spots waiting for confirmation.");
                dlg.setContentText("Select the spot to confirm:");
                ThemeManager.styleDialog(dlg);
                dlg.getDialogPane().getStyleClass().add("help-adaptive");
                dlg.showAndWait().ifPresent(chosen -> {
                    int idx = labels.indexOf(chosen);
                    if (idx >= 0) toConfirm.add(waitlistPending.get(idx));
                });
            }

            boolean anyConfirmed = false;
            for (OrderDetail order : toConfirm) {
                ChatClient.lastConfirmVisitOk    = null;
                ChatClient.lastConfirmVisitError = null;
                ClientUI.chat.accept(new Message("CONFIRM_VISIT", order.getId()));

                if (Boolean.TRUE.equals(ChatClient.lastConfirmVisitOk)) {
                    anyConfirmed = true;
                    NotificationCenter.markVisitConfirmed(order.getId());
                    NotificationCenter.add("✅",
                        "Your visit is confirmed! Your waitlist spot for "
                        + order.getParkName() + " on " + order.getVisitDate()
                        + " at " + order.getVisitTime()
                        + " (Order #" + order.getId() + ") is now locked in. See you there!");
                } else {
                    String err = ChatClient.lastConfirmVisitError != null
                        ? ChatClient.lastConfirmVisitError
                        : "Could not confirm Order #" + order.getId()
                          + ". Your window may have expired — please check My Reservations.";
                    NotificationCenter.add("⚠️", err);
                }
            }
            if (anyConfirmed && btnConfirmVisit != null) {
                btnConfirmVisit.setDisable(true);
                if (lblConfirmTitle != null) lblConfirmTitle.setText("Visit Confirmed");
                if (lblConfirmDesc != null)  lblConfirmDesc.setText("Your booking is locked in");
            }
            refreshNotifications();
            return;
        }

        // ── Day-before reminder path ──────────────────────────────────────────
        // no pending waitlist promotions; fall through to the ordinary reminder flow.
        ChatClient.lastMyOrders = null;
        ClientUI.chat.accept(new Message("GET_MY_ORDERS", s.getUserId()));
        List<OrderDetail> orders = ChatClient.lastMyOrders;

        if (orders == null || orders.isEmpty()) {
            NotificationCenter.add("💬", "No reservations found to confirm.");
            refreshNotifications();
            return;
        }

        String tomorrow = LocalDate.now().plusDays(1).format(DATE_FMT);
        List<OrderDetail> eligible = orders.stream()
            .filter(o -> tomorrow.equals(o.getVisitDate())
                && ("CONFIRMED".equals(o.getStatus()) || "PENDING".equals(o.getStatus())))
            .collect(Collectors.toList());

        if (eligible.isEmpty()) {
            NotificationCenter.add("💬",
                "No bookings for tomorrow that need confirmation.");
            refreshNotifications();
            return;
        }

        // if multiple eligible orders, let visitor choose which to confirm
        List<OrderDetail> toConfirm = new ArrayList<>();
        if (eligible.size() == 1) {
            toConfirm.add(eligible.get(0));
        } else {
            List<String> labels = eligible.stream()
                .map(o -> "Order #" + o.getId()
                    + " — " + o.getParkName()
                    + " at " + o.getVisitTime()
                    + " (" + o.getNumVisitors() + " visitor(s))")
                .collect(Collectors.toList());
            ChoiceDialog<String> dlg = new ChoiceDialog<>(labels.get(0), labels);
            dlg.setTitle("Confirm My Visit");
            dlg.setHeaderText("You have " + eligible.size() + " bookings for tomorrow.");
            dlg.setContentText("Select the booking to confirm:");
            ThemeManager.styleDialog(dlg);
            dlg.getDialogPane().getStyleClass().add("help-adaptive");
            dlg.showAndWait().ifPresent(chosen -> {
                int idx = labels.indexOf(chosen);
                if (idx >= 0) toConfirm.add(eligible.get(idx));
            });
        }

        int confirmedCount = 0;
        for (OrderDetail order : toConfirm) {
            ChatClient.lastConfirmVisitOk    = null;
            ChatClient.lastConfirmVisitError = null;
            ClientUI.chat.accept(new Message("CONFIRM_VISIT", order.getId()));

            if (Boolean.TRUE.equals(ChatClient.lastConfirmVisitOk)) {
                confirmedCount++;
                NotificationCenter.markVisitConfirmed(order.getId());
                NotificationCenter.add("✅",
                    "Visit confirmed — Order #" + order.getId()
                    + " at " + order.getParkName()
                    + " tomorrow at " + order.getVisitTime()
                    + ". Your booking will not be auto-cancelled. See you there!");
            } else {
                String err = ChatClient.lastConfirmVisitError != null
                    ? ChatClient.lastConfirmVisitError
                    : "Could not confirm Order #" + order.getId() + ". Please try again.";
                NotificationCenter.add("⚠️", err);
            }
        }
        // Only disable the confirm button when every eligible booking for tomorrow has
        // been confirmed in this pass.  If the visitor confirmed just one of several
        // bookings (ChoiceDialog picks one at a time), the button must stay active so
        // they can return and confirm the remaining ones.
        if (confirmedCount > 0 && btnConfirmVisit != null) {
            if (confirmedCount >= eligible.size()) {
                btnConfirmVisit.setDisable(true);
                if (lblConfirmTitle != null) lblConfirmTitle.setText("Visit Confirmed");
                if (lblConfirmDesc != null)  lblConfirmDesc.setText("Your booking is locked in");
            } else {
                int remaining = eligible.size() - confirmedCount;
                if (lblConfirmDesc != null)
                    lblConfirmDesc.setText(remaining + " booking(s) still need confirmation");
            }
        }
        refreshNotifications();
    }

    /**
     * lets the visitor register their own park exit without needing an employee.
     * loads the park list, asks the visitor to select which park they are leaving,
     * then sends {@code REGISTER_EXIT} using their own session ID.
     *
     * @param event the button-click event
     */
    @FXML
    public void handleRegisterExit(ActionEvent event) {
        // fetch parks so the visitor can select which one they're leaving
        ChatClient.lastParkList = null;
        ClientUI.chat.accept(new Message("GET_PARKS", null));
        List<Park> parks = ChatClient.lastParkList;

        if (parks == null || parks.isEmpty()) {
            NotificationCenter.add("⚠️", "Could not load park list. Please try again.");
            refreshNotifications();
            return;
        }

        Park park = showParkExitPicker(parks);
        if (park == null) return;   // visitor cancelled

        UserSession s = UserSession.getInstance();
        if (s == null || s.getUserId() == null) return;

        ChatClient.lastExitSuccess = false;
        ChatClient.lastExitError   = null;
        ClientUI.chat.accept(new Message("REGISTER_EXIT",
            new ExitRequest(s.getUserId(), park.getId())));

        if (ChatClient.lastExitSuccess) {
            NotificationCenter.add("🚗",
                "Exit from " + park.getName() + " registered — thank you for visiting! Have a great day.");
        } else {
            String reason = ChatClient.lastExitError != null
                ? ChatClient.lastExitError
                : "No active check-in found for today at " + park.getName() + ".";
            NotificationCenter.add("⚠️", "Exit registration failed — " + reason);
        }
        refreshNotifications();
    }

    /**
     * shows a GoNature-themed park picker for registering an exit. unlike the plain
     * {@code ChoiceDialog} it replaces, the content sits on an elevated card (drop shadow +
     * green accent border), uses a styled park selector with location pins, themed buttons,
     * and honours the current light/dark theme.
     *
     * @param parks the non-empty list of parks the visitor can choose from
     * @return the selected park, or {@code null} if the visitor cancelled
     */
    private Park showParkExitPicker(List<Park> parks) {
        Dialog<Park> dlg = new Dialog<>();
        dlg.setTitle("Register My Exit");
        dlg.initModality(Modality.WINDOW_MODAL);
        if (lblWelcome.getScene() != null)
            dlg.initOwner(lblWelcome.getScene().getWindow());

        DialogPane pane = dlg.getDialogPane();
        pane.setPrefWidth(400);
        ThemeManager.styleDialog(dlg);
        // opt into dark mode — without this, help-dialog forces the parchment palette regardless
        pane.getStyleClass().add("help-adaptive");

        ButtonType confirmType = new ButtonType("Register Exit", ButtonBar.ButtonData.OK_DONE);
        pane.getButtonTypes().addAll(confirmType, ButtonType.CANCEL);

        // ── elevated content card ──────────────────────────────────────────────
        Label title = new Label("🚗  Leaving a Park");
        title.getStyleClass().add("card-title");

        Label hint = new Label("Select the park you're leaving and we'll log your exit time.");
        hint.getStyleClass().add("screen-subtitle");
        hint.setWrapText(true);

        Label pickLbl = new Label("PARK");
        pickLbl.getStyleClass().add("section-label");

        ComboBox<Park> combo = new ComboBox<>();
        combo.getItems().setAll(parks);
        combo.getSelectionModel().selectFirst();
        combo.setMaxWidth(Double.MAX_VALUE);
        combo.setPromptText("Choose a park…");
        combo.setCellFactory(lv -> new ListCell<>() {
            @Override protected void updateItem(Park p, boolean empty) {
                super.updateItem(p, empty);
                setText(empty || p == null ? null : "📍  " + p.getName());
            }
        });
        combo.setButtonCell(new ListCell<>() {
            @Override protected void updateItem(Park p, boolean empty) {
                super.updateItem(p, empty);
                setText(empty || p == null ? null : "📍  " + p.getName());
            }
        });

        VBox card = new VBox(12, title, new Separator(), hint, pickLbl, combo);
        card.getStyleClass().add("card");          // .card supplies the drop-shadow elevation
        card.setFillWidth(true);

        // a little breathing room around the card so its shadow is visible against the pane
        VBox wrap = new VBox(card);
        wrap.setPadding(new Insets(8, 6, 4, 6));
        pane.setContent(wrap);

        // ── themed buttons ─────────────────────────────────────────────────────
        Node ok = pane.lookupButton(confirmType);
        ok.getStyleClass().add("btn-primary");
        Node cancel = pane.lookupButton(ButtonType.CANCEL);
        cancel.getStyleClass().add("btn-secondary");

        ok.setDisable(combo.getValue() == null);
        combo.valueProperty().addListener((o, a, b) -> ok.setDisable(b == null));

        dlg.setResultConverter(bt -> bt == confirmType ? combo.getValue() : null);
        return dlg.showAndWait().orElse(null);
    }

    /**
     * shows a role-specific help dialog.
     *
     * @param event the button-click event
     */
    @FXML
    public void handleHelp(ActionEvent event) {
        UserSession s = UserSession.getInstance();
        String content;
        if ("GUIDE".equals(s != null ? s.getRole() : "")) {
            content =
                "GROUP BOOKING (Guide)\n" +
                "  • Click 'Book a Visit' and select visit type GROUP.\n" +
                "  • Enter the total headcount including yourself (2–16 people).\n" +
                "  • You enter the park free of charge as the group leader.\n" +
                "  • A 25% group + 12% advance discount (about 34% off, stacked) applies to the rest of the group; you enter free.\n\n" +
                "SOLO BOOKING (Guide visiting alone)\n" +
                "  • Select SOLO — treated the same as any other solo visitor.\n" +
                "  • 15% pre-booked discount applied automatically.\n\n" +
                "MANAGING ORDERS\n" +
                "  • Use 'View My Reservations' to see and cancel bookings.\n\n" +
                "NOTIFICATIONS\n" +
                "  • The Notifications panel shows booking confirmations, cancellations,\n" +
                "    waiting-list alerts, and day-before reminders.\n" +
                "  • Click '↻ Refresh' to pull the latest events.\n\n" +
                "STATUS COLOURS\n" +
                "  GREEN = Confirmed  |  ORANGE = Pending  |  GREY = Completed\n" +
                "  RED = Cancelled";
        } else {
            content =
                "BOOKING A VISIT\n" +
                "  • Click 'Book a Visit' to open the reservation form.\n" +
                "  • SOLO: 1 visitor — 15% pre-booked discount.\n" +
                "  • FAMILY: 2-15 visitors, 15% pre-booked discount.\n\n" +
                "VIEWING YOUR RESERVATIONS\n" +
                "  • Click 'View My Reservations' to see all your bookings.\n\n" +
                "CANCELLING A RESERVATION\n" +
                "  • Select an order in 'View My Reservations' and click 'Cancel Order'.\n" +
                "  • Only PENDING or CONFIRMED orders can be cancelled.\n\n" +
                "WAITING LIST\n" +
                "  • If a park is fully booked, you can join the waiting list.\n" +
                "  • You'll get a notification here if a spot opens.\n\n" +
                "NOTIFICATIONS\n" +
                "  • The Notifications panel shows booking confirmations, cancellations,\n" +
                "    waiting-list alerts, and day-before reminders.\n" +
                "  • Click '↻ Refresh' to pull the latest events.";
        }
        showHelp("GoNature — Help (" + (s != null ? s.getRole() : "VISITOR") + ")", content);
    }

    /**
     * @param title   the dialog title
     * @param content the help body text
     */
    private void showHelp(String title, String content) {
        HelpDialog.show(title, content);
    }

    /**
     * clears notifications, clears the session, and returns to the login screen.
     *
     * @param event the button-click event
     */
    @FXML
    public void handleLogout(ActionEvent event) {
        stopSessionTimer();   // stop before clearing the session so the clock doesn't tick on a null user
        NotificationCenter.clear();
        if (ClientUI.chat != null) ClientUI.chat.sendLogout();
        UserSession.clear();
        Stage stage = (Stage) lblWelcome.getScene().getWindow();
        try {
            new LoginController().start(stage);
        } catch (Exception e) {
            if (ClientUI.chat != null) ClientUI.chat.disconnect();
            Platform.exit();
        }
    }

    /**
     * @param primaryStage the stage to display the screen in
     * @throws Exception if the FXML file cannot be loaded
     */
    public void start(Stage primaryStage) throws Exception {
        Parent root = FXMLLoader.load(getClass().getResource("/gui/VisitorFrame.fxml"));
        primaryStage.setTitle("GoNature — " + UserSession.getInstance().getRole());
        Scene scene = new Scene(root);
        ThemeManager.register(scene);   // apply the current light/dark theme to this screen
        primaryStage.setScene(scene);
        primaryStage.centerOnScreen();
        primaryStage.show();
        Animations.introduce(root);     // subtle fade-and-rise on load
    }
}

