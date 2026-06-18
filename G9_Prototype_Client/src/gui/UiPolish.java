package gui;

import javafx.animation.Interpolator;
import javafx.animation.ScaleTransition;
import javafx.animation.TranslateTransition;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.input.MouseEvent;
import javafx.util.Duration;

/**
 * global, idempotent scene decorator. the polish layer that upgrades every screen without
 * touching any FXML. {@link ThemeManager} calls {@link #decorate(Scene)} for every window
 * it themes (it already watches all of them), so the pass applies to every current and
 * future screen and dialog automatically.
 *
 * <p>two upgrades per pass:
 * <ul>
 *   <li><b>brand icon</b>: every {@code header-logo} label swaps its leading emoji
 *       (inconsistent, monochrome on Windows) for the crisp vector pine logomark;</li>
 *   <li><b>motion</b>: every button gets a smooth hover-lift and a quick press-scale,
 *       so the whole app feels responsive rather than static.</li>
 * </ul>
 *
 * each node is marked via its properties map after decoration, so re-runs (every theme
 * toggle re-applies) are cheap no-ops.
 */
public final class UiPolish {

    /** properties-map marker so a node is only decorated once. */
    private static final Object DONE = new Object();
    private static final String KEY_LOGO   = "gonature.polish.logo";
    private static final String KEY_MOTION = "gonature.polish.motion";

    /** utility class, no instances. */
    private UiPolish() {}

    /**
     * applies the polish pass to a scene; safe to call repeatedly.
     *
     * @param scene the scene to decorate; ignored if {@code null} or root-less
     */
    public static void decorate(Scene scene) {
        if (scene == null || scene.getRoot() == null) return;

        for (Node n : scene.getRoot().lookupAll(".header-logo")) {
            if (n instanceof Label && !n.getProperties().containsKey(KEY_LOGO)) {
                n.getProperties().put(KEY_LOGO, DONE);
                brandify((Label) n);
            }
        }
        for (Node n : scene.getRoot().lookupAll(".button")) {
            if (n instanceof Button && !n.getProperties().containsKey(KEY_MOTION)) {
                n.getProperties().put(KEY_MOTION, DONE);
                addMotion((Button) n);
            }
        }
    }

    /** swaps a header label's leading emoji for the vector pine logomark. */
    private static void brandify(Label logo) {
        String text = logo.getText();
        if (text != null) {
            // strip any leading emoji/symbol cluster ("🌿 GoNature" → "GoNature")
            logo.setText(text.replaceFirst("^[^\\p{L}\\p{N}]+", ""));
        }
        logo.setGraphic(Icons.pine(20, Icons.CREAM));
        logo.setGraphicTextGap(9);
    }

    /** installs hover-lift + press-scale micro-interactions on a button. */
    private static void addMotion(Button btn) {
        TranslateTransition lift = new TranslateTransition(Duration.millis(110), btn);
        lift.setInterpolator(Interpolator.EASE_OUT);
        btn.addEventHandler(MouseEvent.MOUSE_ENTERED, e -> {
            lift.stop();
            lift.setToY(-1.5);
            lift.play();
        });
        btn.addEventHandler(MouseEvent.MOUSE_EXITED, e -> {
            lift.stop();
            lift.setToY(0);
            lift.play();
        });

        ScaleTransition press = new ScaleTransition(Duration.millis(70), btn);
        btn.addEventHandler(MouseEvent.MOUSE_PRESSED, e -> {
            press.stop();
            press.setToX(0.96);
            press.setToY(0.96);
            press.play();
        });
        btn.addEventHandler(MouseEvent.MOUSE_RELEASED, e -> {
            press.stop();
            press.setToX(1.0);
            press.setToY(1.0);
            press.play();
        });
    }
}
