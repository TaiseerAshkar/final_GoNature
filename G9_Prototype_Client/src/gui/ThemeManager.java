package gui;

import javafx.scene.Scene;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Set;
import java.util.WeakHashMap;

/**
 * holds the application-wide light/dark theme preference for the lifetime of a client session.
 *
 * <p>the preference is a single static flag, so it persists as the user moves between screens
 * without any restart. every screen that wants to honour the theme registers its {@link Scene}
 * via {@link #register(Scene)} in its {@code start(...)} method; {@link #toggle()} then flips the
 * flag and live-updates every still-open scene by adding/removing the {@code dark-mode} style
 * class on the scene root (the CSS in {@code gonature.css} does the actual recolouring).
 *
 * <p>scenes are held through weak references, so closed windows are garbage-collected normally
 * and never leak.
 */
public final class ThemeManager {

    /** style class toggled on the scene root; matched by the {@code .dark-mode} rules in the CSS. */
    public static final String DARK_CLASS = "dark-mode";

    /** {@code true} when dark mode is active. Defaults to light mode at startup. */
    private static boolean darkMode = false;

    /** every scene currently honouring the theme, held weakly so closed windows are collected. */
    private static final Set<Scene> scenes =
        Collections.newSetFromMap(new WeakHashMap<>());

    /** utility class — no instances. */
    private ThemeManager() {}

    /** @return {@code true} when dark mode is currently active */
    public static boolean isDarkMode() { return darkMode; }

    /**
     * registers a scene and immediately applies the current theme to it. call once from each
     * screen's {@code start(...)} method, right after the {@link Scene} is created, so the
     * screen opens already matching the session-wide preference.
     *
     * @param scene the scene to track and theme; ignored if {@code null}
     */
    public static void register(Scene scene) {
        if (scene == null) return;
        scenes.add(scene);
        applyTo(scene);
    }

    /**
     * flips between light and dark mode and live-updates every registered, still-open scene.
     * safe to call from any toggle button on any screen.
     */
    public static void toggle() {
        darkMode = !darkMode;
        // iterate a snapshot so the weak set can't be mutated underneath us
        for (Scene s : new ArrayList<>(scenes)) {
            applyTo(s);
            if (s.getRoot() != null) Animations.themeFlash(s.getRoot());  // signal the switch
        }
    }

    /**
     * adds or removes the {@code dark-mode} style class on the scene root so it matches the
     * current preference. a no-op if the scene or its root is {@code null}.
     *
     * @param scene the scene to update
     */
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
     * @return the glyph a toggle button should show: a sun while dark mode is on
     *         (click to go light) and a moon while light mode is on (click to go dark)
     */
    public static String iconText() { return darkMode ? "☀" : "🌙"; }
}
