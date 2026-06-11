package gui;

import javafx.collections.ListChangeListener;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Dialog;
import javafx.scene.control.DialogPane;
import javafx.stage.Window;

import java.net.URL;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Set;
import java.util.WeakHashMap;

/**
 * holds the application-wide light/dark theme preference for the lifetime of a client session.
 *
 * <p>the preference is a single static flag. rather than tracking individual scenes, the manager
 * watches {@link Window#getWindows()} — the live list of every open window — so <em>every</em>
 * screen and dialog (current or opened later) automatically follows the current theme. applying
 * the theme means adding/removing the {@code dark-mode} style class on each scene root; the
 * {@code .dark-mode} rules in {@code gonature.css} do the actual recolouring.
 *
 * <p>{@link #installToggle(Button)} wires any button as a theme switch, and all such buttons are
 * kept in sync so their sun/moon icon always reflects the live state.
 */
public final class ThemeManager {

    /** style class toggled on each scene root; matched by the {@code .dark-mode} rules in the CSS. */
    public static final String DARK_CLASS = "dark-mode";

    /** {@code true} when dark mode is active. Defaults to light mode at startup. */
    private static boolean darkMode = false;

    /** whether the global window-list listener has been installed yet. */
    private static boolean installed = false;

    /** every toggle button, held weakly, so their icons can be kept in sync on a switch. */
    private static final Set<Button> toggles =
        Collections.newSetFromMap(new WeakHashMap<>());

    /** utility class — no instances. */
    private ThemeManager() {}

    /** @return {@code true} when dark mode is currently active */
    public static boolean isDarkMode() { return darkMode; }

    /**
     * starts watching every window (once) so all current and future windows follow the theme.
     */
    private static synchronized void ensureInstalled() {
        if (installed) return;
        installed = true;
        Window.getWindows().addListener((ListChangeListener<Window>) change -> {
            while (change.next())
                for (Window w : change.getAddedSubList()) hook(w);
        });
        for (Window w : Window.getWindows()) hook(w);
    }

    /** applies the theme to a window now and again whenever its scene changes. */
    private static void hook(Window w) {
        if (w == null) return;
        applyTo(w.getScene());
        w.sceneProperty().addListener((o, ov, nv) -> applyTo(nv));
    }

    /**
     * registers a scene with the theme system and applies the current theme immediately, so a
     * screen opens already matching the preference (avoids a first-frame flash). optional now
     * that windows are themed globally, but cheap and flash-free.
     *
     * @param scene the scene to theme; ignored if {@code null}
     */
    public static void register(Scene scene) {
        ensureInstalled();
        applyTo(scene);
    }

    /** flips between light and dark mode, live-updating every open window and every toggle icon. */
    public static void toggle() {
        ensureInstalled();
        darkMode = !darkMode;
        for (Window w : Window.getWindows()) {
            applyTo(w.getScene());
            if (w.getScene() != null && w.getScene().getRoot() != null)
                Animations.themeFlash(w.getScene().getRoot());
        }
        for (Button b : new ArrayList<>(toggles)) refreshIcon(b);
    }

    /** adds or removes the {@code dark-mode} class on the scene root to match the current mode. */
    private static void applyTo(Scene scene) {
        if (scene == null || scene.getRoot() == null) return;
        var classes = scene.getRoot().getStyleClass();
        if (darkMode) {
            if (!classes.contains(DARK_CLASS)) classes.add(DARK_CLASS);
        } else {
            classes.remove(DARK_CLASS);
        }
    }

    /**
     * wires a button as a theme toggle: sets its icon to the current state, flips the theme on
     * click, and keeps it in sync with every other toggle. call once from a screen's
     * {@code initialize()}.
     *
     * @param btn the header button to turn into a theme switch; ignored if {@code null}
     */
    public static void installToggle(Button btn) {
        if (btn == null) return;
        ensureInstalled();
        toggles.add(btn);
        refreshIcon(btn);
        btn.setOnAction(e -> toggle());
    }

    /** updates a single toggle button's glyph to match the live state. */
    private static void refreshIcon(Button btn) {
        if (btn != null) btn.setText(iconText());
    }

    /**
     * @return the glyph a toggle button should show: a sun while dark mode is on
     *         (click to go light) and a moon while light mode is on (click to go dark)
     */
    public static String iconText() { return darkMode ? "☀" : "🌙"; }

    /**
     * applies the shared topographic theme to a popup dialog — works for {@link javafx.scene.control.Alert},
     * {@link javafx.scene.control.TextInputDialog}, {@link javafx.scene.control.ChoiceDialog}, and any
     * custom {@link Dialog}. attaches {@code gonature.css} to the dialog pane (so the looked-up colour
     * tokens and {@code .dark-mode} rules resolve), tags it with the {@code help-dialog} style class for the
     * pine/gold header strip and themed buttons, and registers its scene so it follows the active
     * light/dark theme. call once, right after creating the dialog and before {@code showAndWait()}.
     *
     * @param dialog the dialog to theme; ignored if {@code null}
     */
    public static void styleDialog(Dialog<?> dialog) {
        if (dialog == null) return;
        DialogPane pane = dialog.getDialogPane();
        if (pane == null) return;

        URL css = ThemeManager.class.getResource("/gui/gonature.css");
        if (css != null) {
            String href = css.toExternalForm();
            if (!pane.getStylesheets().contains(href)) pane.getStylesheets().add(href);
        }
        if (!pane.getStyleClass().contains("help-dialog")) pane.getStyleClass().add("help-dialog");

        // the dialog's scene only exists once it's shown — register now if present, and on attach
        if (pane.getScene() != null) register(pane.getScene());
        pane.sceneProperty().addListener((o, ov, sc) -> { if (sc != null) register(sc); });
    }
}
