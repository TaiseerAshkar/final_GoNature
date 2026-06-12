package gui;

import javafx.animation.FadeTransition;
import javafx.animation.PauseTransition;
import javafx.animation.SequentialTransition;
import javafx.animation.TranslateTransition;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.stage.Popup;
import javafx.stage.Window;
import javafx.util.Duration;

import java.net.URL;
import java.util.ArrayList;
import java.util.List;

/**
 * lightweight toast notifications for transient outcomes — "Booking confirmed",
 * "Could not cancel order" — shown as a floating pill in the top-right corner of the
 * active window. slides in, lingers ~3.2s, fades out; click to dismiss early.
 *
 * <p>replaces the scattered per-screen inline status labels for one-shot results, and uses
 * fixed colour pills (pine / rust / lake with white text) so it is equally readable in
 * light and dark mode — unlike the old hardcoded-red label styles. multiple toasts stack
 * downward and reflow when earlier ones close.
 */
public final class Toast {

    /** toasts currently on screen, top-to-bottom, used to stack new ones below. */
    private static final List<Popup> active = new ArrayList<>();

    /** vertical gap between the window's top edge and the first toast. */
    private static final double TOP_OFFSET = 58;
    private static final double STACK_STEP = 54;
    private static final double RIGHT_MARGIN = 22;

    /** utility class — no instances. */
    private Toast() {}

    /** shows a pine success toast. @param message the one-line outcome text */
    public static void success(String message) {
        show(message, "toast-success", Icons.checkCircle(17, Icons.CREAM));
    }

    /** shows a rust error toast. @param message the one-line outcome text */
    public static void error(String message) {
        show(message, "toast-error", Icons.alertTriangle(17, Icons.CREAM));
    }

    /** shows a lake info toast. @param message the one-line outcome text */
    public static void info(String message) {
        show(message, "toast-info", Icons.infoCircle(17, Icons.CREAM));
    }

    /** builds the pill, positions it under any visible toasts, and runs the in/wait/out cycle. */
    private static void show(String message, String styleClass, javafx.scene.Node icon) {
        Window owner = findOwner();
        if (owner == null || message == null || message.isEmpty()) return;

        Label text = new Label(message);
        text.setWrapText(true);
        text.setMaxWidth(360);

        HBox pill = new HBox(10, icon, text);
        pill.setAlignment(Pos.CENTER_LEFT);
        pill.getStyleClass().addAll("toast", styleClass);
        URL css = Toast.class.getResource("/gui/gonature.css");
        if (css != null) pill.getStylesheets().add(css.toExternalForm());

        Popup popup = new Popup();
        popup.getContent().add(pill);
        popup.setAutoFix(true);
        active.add(popup);
        pill.setOnMouseClicked(e -> close(popup, pill));

        // measure by showing off-screen-ish then positioning; width known after show
        popup.show(owner);
        double x = owner.getX() + owner.getWidth() - pill.getWidth() - RIGHT_MARGIN;
        double y = owner.getY() + TOP_OFFSET + (active.size() - 1) * STACK_STEP;
        popup.setX(x);
        popup.setY(y);

        // slide-in + fade-in, hold, fade-out
        pill.setOpacity(0);
        pill.setTranslateY(-10);
        FadeTransition in = new FadeTransition(Duration.millis(160), pill);
        in.setToValue(1);
        TranslateTransition drop = new TranslateTransition(Duration.millis(160), pill);
        drop.setToY(0);
        in.play();
        drop.play();

        PauseTransition hold = new PauseTransition(Duration.millis(3200));
        FadeTransition out = new FadeTransition(Duration.millis(260), pill);
        out.setToValue(0);
        SequentialTransition cycle = new SequentialTransition(hold, out);
        cycle.setOnFinished(e -> close(popup, pill));
        cycle.play();
    }

    /** hides a toast and reflows any toasts that were stacked below it. */
    private static void close(Popup popup, HBox pill) {
        int idx = active.indexOf(popup);
        if (idx < 0) return;            // already closed (click + timer race)
        active.remove(idx);
        popup.hide();
        // shift remaining toasts up into the freed slot
        for (int i = 0; i < active.size(); i++) {
            Popup p = active.get(i);
            Window owner = p.getOwnerWindow();
            if (owner != null) p.setY(owner.getY() + TOP_OFFSET + i * STACK_STEP);
        }
    }

    /** @return the focused window, or any showing window as a fallback */
    private static Window findOwner() {
        Window focused = null;
        for (Window w : Window.getWindows()) {
            if (w.isShowing() && w.isFocused()) { focused = w; break; }
        }
        if (focused != null) return focused;
        for (Window w : Window.getWindows()) {
            if (w.isShowing()) return w;
        }
        return null;
    }
}
