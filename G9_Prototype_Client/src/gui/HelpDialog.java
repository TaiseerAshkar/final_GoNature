package gui;

import javafx.scene.control.Alert;
import javafx.scene.control.DialogPane;
import javafx.scene.control.TextArea;

import java.net.URL;

/**
 * shared builder for the in-app Help popups shown from every screen's "Help ?" control.
 *
 * <p>previously each controller built its own plain-white {@link Alert} with an unstyled
 * {@link TextArea}. this helper centralises that into one themed popup: it attaches the
 * application stylesheet ({@code gonature.css}) to the dialog pane and tags it with the
 * {@code help-dialog} / {@code help-text} style classes, so the popup picks up the
 * topographic look — pine header, parchment body, trail-marker gold accent — and follows
 * the active light/dark theme automatically (the global watcher in {@link ThemeManager}
 * adds the {@code dark-mode} class to every open window, this dialog included).
 */
public final class HelpDialog {

    /** utility class — no instances. */
    private HelpDialog() {}

    /**
     * shows a scrollable, themed Help popup.
     *
     * @param title   the header text (e.g. {@code "GoNature — Help (VISITOR)"})
     * @param content the help body text shown in the scrollable area
     */
    public static void show(String title, String content) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("GoNature — Help");
        alert.setHeaderText(title);
        alert.setGraphic(null);   // drop the default blue 'i' icon — the pine header carries the look

        TextArea ta = new TextArea(content);
        ta.setEditable(false);
        ta.setWrapText(true);
        ta.setPrefRowCount(18);
        ta.setPrefWidth(470);
        ta.getStyleClass().add("help-text");

        DialogPane pane = alert.getDialogPane();
        pane.setContent(ta);
        pane.setMinWidth(510);
        pane.getStyleClass().add("help-dialog");

        // attach the app stylesheet so the .help-dialog rules (and theme tokens) resolve
        URL css = HelpDialog.class.getResource("/gui/gonature.css");
        if (css != null) pane.getStylesheets().add(css.toExternalForm());

        // make sure the global theme watcher is live so the popup opens already matching
        // the current light/dark preference rather than flashing the wrong palette
        ThemeManager.register(pane.getScene());

        alert.showAndWait();
    }
}
