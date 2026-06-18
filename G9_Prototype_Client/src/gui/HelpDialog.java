package gui;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.DialogPane;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

import java.net.URL;

/**
 * shared builder for the in-app Help popups shown from every screen's "Help ?" control.
 *
 * <p>every controller routes through {@link #show(String, String)}, so the popup's look is
 * defined once, here. it renders the same premium card as the Connect / Login dialogs: a deep-pine
 * hero banner with a trail-marker gold accent line and a gold "?" badge, a short context line
 * (derived from the caller's title), and a scrollable reading panel below.
 *
 * <p>built on a plain {@link Dialog} (not {@link javafx.scene.control.Alert}) so no stray
 * "message" label is injected. unlike the launch dialogs, the Help popup <em>follows the active
 * theme</em>: the {@code help-adaptive} marker class lets it render dark in dark mode and light in
 * light mode (see the {@code .dark-mode.help-dialog.help-adaptive} rule in {@code gonature.css}).
 */
public final class HelpDialog {

    /** utility class, no instances. */
    private HelpDialog() {}

    /**
     * shows a scrollable, themed Help popup with the branded hero banner.
     *
     * @param title   the caller's title, such as a "Help (VISITOR)" screen title; the
     *                leading "GoNature" prefix is stripped to form the hero's context line
     * @param content the help body text shown in the scrollable reading panel
     */
    public static void show(String title, String content) {
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("GoNature — Help");

        // derive a short context line by stripping the leading "GoNature" prefix from the title
        String context = (title == null) ? "" : title.trim();
        if (context.startsWith("GoNature — ")) context = context.substring("GoNature — ".length());
        if (context.isEmpty()) context = "National Park Reservation System";

        // ── trail-marker "?" badge ──
        Label badge = new Label("?");
        badge.setStyle("-fx-min-width: 36; -fx-min-height: 36; -fx-pref-width: 36; -fx-pref-height: 36; "
            + "-fx-alignment: center; -fx-font-size: 20px; -fx-font-weight: bold; "
            + "-fx-text-fill: #0B4A40; -fx-background-color: #F0E4BE; -fx-background-radius: 18; "
            + "-fx-border-color: rgba(217,164,65,0.65); -fx-border-radius: 18; -fx-border-width: 1;");

        Label heroTitle = new Label("Help & Guidance");
        heroTitle.setStyle("-fx-font-family: -park-font-display; -fx-font-size: 21px; "
            + "-fx-font-weight: bold; -fx-text-fill: #F5F1E4;");
        Label heroSub = new Label(context);
        heroSub.setStyle("-fx-font-size: 11px; -fx-font-weight: bold; "
            + "-fx-text-fill: rgba(245,241,228,0.82);");
        VBox heroText = new VBox(2, heroTitle, heroSub);
        heroText.setAlignment(Pos.CENTER_LEFT);

        HBox hero = new HBox(13, badge, heroText);
        hero.setAlignment(Pos.CENTER_LEFT);
        hero.setPadding(new Insets(16, 22, 15, 22));
        hero.setMaxWidth(Double.MAX_VALUE);
        hero.setStyle("-fx-background-color: linear-gradient(to right, -park-pine-dark, #0C5F51, -park-pine); "
            + "-fx-background-radius: 12 12 0 0; "
            + "-fx-border-color: transparent transparent -park-gold transparent; "
            + "-fx-border-width: 0 0 3 0;");

        // ── scrollable help body (inset reading panel via .help-text) ──
        TextArea ta = new TextArea(content);
        ta.setEditable(false);
        ta.setWrapText(true);
        ta.setPrefRowCount(18);
        ta.setPrefWidth(470);
        ta.getStyleClass().add("help-text");

        VBox body = new VBox(ta);
        body.setPadding(new Insets(16, 18, 16, 18));

        // ── card wrapper: pine hero on top, themed body below ──
        VBox card = new VBox(hero, body);
        card.setMaxWidth(Double.MAX_VALUE);
        card.setStyle("-fx-background-color: -park-surface; -fx-background-radius: 12; "
            + "-fx-border-color: -park-border; -fx-border-radius: 12; -fx-border-width: 1;");

        DialogPane pane = dialog.getDialogPane();
        pane.setContent(card);
        pane.setMinWidth(540);
        // help-dialog = themed look · help-adaptive = follow light/dark theme (opt out of force-light)
        pane.getStyleClass().addAll("help-dialog", "help-adaptive");

        // attach the app stylesheet so the .help-dialog rules (and theme tokens) resolve
        URL css = HelpDialog.class.getResource("/gui/gonature.css");
        if (css != null) pane.getStylesheets().add(css.toExternalForm());

        // single pine "Got it" button; CANCEL_CLOSE so Esc / window-X also dismiss the popup
        ButtonType gotIt = new ButtonType("Got it", ButtonBar.ButtonData.CANCEL_CLOSE);
        pane.getButtonTypes().add(gotIt);
        Button gotItBtn = (Button) pane.lookupButton(gotIt);
        if (gotItBtn != null) gotItBtn.getStyleClass().add("btn-gotit");

        // open already matching the current light/dark preference, and keep following theme changes
        ThemeManager.register(pane.getScene());
        pane.sceneProperty().addListener((o, ov, sc) -> { if (sc != null) ThemeManager.register(sc); });

        dialog.showAndWait();
    }
}
