package gui;

import javafx.animation.FadeTransition;
import javafx.animation.Interpolator;
import javafx.animation.ParallelTransition;
import javafx.animation.TranslateTransition;
import javafx.scene.Node;
import javafx.util.Duration;

/**
 * tiny animation helpers used to add subtle motion to the GoNature client.
 *
 * <p>JavaFX has no CSS transitions, so these wrap {@code Transition} objects that screens
 * can fire imperatively (e.g. on load or on a theme change).
 */
public final class Animations {

    /** utility class, no instances. */
    private Animations() {}

    /**
     * plays a quick fade-and-rise intro on a freshly shown screen root, so screens settle
     * in rather than snapping into place. safe to call once after {@code stage.show()}.
     *
     * @param node the screen root to animate; ignored if {@code null}
     */
    public static void introduce(Node node) {
        if (node == null) return;
        node.setOpacity(0);
        FadeTransition fade = new FadeTransition(Duration.millis(300), node);
        fade.setFromValue(0);
        fade.setToValue(1);
        TranslateTransition rise = new TranslateTransition(Duration.millis(300), node);
        rise.setFromY(12);
        rise.setToY(0);
        rise.setInterpolator(Interpolator.EASE_OUT);
        new ParallelTransition(fade, rise).play();
    }

    /**
     * plays a brief dim-then-restore fade on a node, used to signal a theme switch so the
     * recolour reads as intentional rather than a flicker.
     *
     * @param node the scene root to flash; ignored if {@code null}
     */
    public static void themeFlash(Node node) {
        if (node == null) return;
        FadeTransition fade = new FadeTransition(Duration.millis(220), node);
        fade.setFromValue(0.55);
        fade.setToValue(1);
        fade.play();
    }
}
