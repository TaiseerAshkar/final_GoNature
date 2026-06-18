package gui;

import javafx.scene.Group;
import javafx.scene.Node;
import javafx.scene.paint.Color;
import javafx.scene.shape.SVGPath;
import javafx.scene.shape.StrokeLineCap;
import javafx.scene.shape.StrokeLineJoin;

/**
 * tiny vector-icon factory for the GoNature client.
 *
 * <p>replaces OS-dependent emoji (which render as thin monochrome glyphs on Windows JavaFX)
 * with crisp {@link SVGPath} shapes drawn on a 24×24 grid. colour comes from CSS via the
 * {@code ikon-stroke} / {@code ikon-fill} classes (theme-adaptive pine → mint in dark mode) or
 * {@code ikon-cream} (fixed warm-cream for icons sitting on the dark-pine header/hero surfaces),
 * so every icon follows the active palette with no per-icon wiring.
 */
public final class Icons {

    /** fixed warm-cream variant marker, for icons on the always-pine header surfaces. */
    public static final String CREAM = "ikon-cream";

    /** utility class, no instances. */
    private Icons() {}

    /* ── path data (24×24 grid) ─────────────────────────────────── */

    private static final String PINE =
        "M12 2 L17.5 9.5 L14.5 9.5 L19.5 16.5 L13.2 16.5 L13.2 21 L10.8 21 "
      + "L10.8 16.5 L4.5 16.5 L9.5 9.5 L6.5 9.5 Z";
    private static final String SUN =
        "M16.5 12 A4.5 4.5 0 1 1 7.5 12 A4.5 4.5 0 1 1 16.5 12 Z "
      + "M12 2 V4.5 M12 19.5 V22 M2 12 H4.5 M19.5 12 H22 "
      + "M4.93 4.93 L6.7 6.7 M17.3 17.3 L19.07 19.07 M19.07 4.93 L17.3 6.7 M6.7 17.3 L4.93 19.07";
    private static final String MOON =
        "M20.5 13.2 A8.7 8.7 0 1 1 10.8 3.5 A6.8 6.8 0 0 0 20.5 13.2 Z";
    private static final String CALENDAR =
        "M8 3.5 V7.5 M16 3.5 V7.5 M4.5 9.5 H19.5 "
      + "M6 5 H18 A1.5 1.5 0 0 1 19.5 6.5 V18.5 A1.5 1.5 0 0 1 18 20 H6 "
      + "A1.5 1.5 0 0 1 4.5 18.5 V6.5 A1.5 1.5 0 0 1 6 5 Z "
      + "M8 13 H10 M14 13 H16 M8 16.5 H10";
    private static final String HOURGLASS =
        "M6.5 3.5 H17.5 M6.5 20.5 H17.5 "
      + "M8 3.5 V7 L12 11 L16 7 V3.5 M8 20.5 V17 L12 13 L16 17 V20.5";
    private static final String INBOX =
        "M4.5 13.5 H8.5 L10.5 16 H13.5 L15.5 13.5 H19.5 "
      + "M6.5 5.5 H17.5 A2 2 0 0 1 19.5 7.5 V16.5 A2 2 0 0 1 17.5 18.5 H6.5 "
      + "A2 2 0 0 1 4.5 16.5 V7.5 A2 2 0 0 1 6.5 5.5 Z";
    private static final String CHECK_CIRCLE =
        "M8.5 12.5 L11 15 L15.5 9.5 M21 12 A9 9 0 1 1 3 12 A9 9 0 1 1 21 12 Z";
    private static final String ALERT_TRIANGLE =
        "M12 9 V13 M12 16.5 V16.51 "
      + "M10.3 4.6 L2.7 17.5 A2 2 0 0 0 4.4 20.5 H19.6 A2 2 0 0 0 21.3 17.5 "
      + "L13.7 4.6 A2 2 0 0 0 10.3 4.6 Z";
    private static final String INFO_CIRCLE =
        "M12 11 V16 M12 8 V8.01 M21 12 A9 9 0 1 1 3 12 A9 9 0 1 1 21 12 Z";
    private static final String CLIPBOARD =
        "M9 4.5 H15 V6.5 H9 Z "
      + "M9 5.5 H7 A1.5 1.5 0 0 0 5.5 7 V19 A1.5 1.5 0 0 0 7 20.5 H17 "
      + "A1.5 1.5 0 0 0 18.5 19 V7 A1.5 1.5 0 0 0 17 5.5 H15 "
      + "M8.5 10.5 H15.5 M8.5 14 H15.5 M8.5 17 H12.5";
    private static final String X_CIRCLE =
        "M21 12 A9 9 0 1 1 3 12 A9 9 0 1 1 21 12 Z M9 9 L15 15 M15 9 L9 15";
    private static final String DOOR_EXIT =
        "M13 3.5 H6.5 A2 2 0 0 0 4.5 5.5 V18.5 A2 2 0 0 0 6.5 20.5 H13 "
      + "M10 12 H20 M16.5 8.5 L20 12 L16.5 15.5";
    private static final String REFRESH =
        "M20.5 12 A8.5 8.5 0 1 1 17.4 5.4 M20.5 4.5 V10 H15";

    /* ── factories ──────────────────────────────────────────────── */

    /** @return the filled pine-tree logomark, theme-coloured (or cream via extra class) */
    public static Node pine(double size, String... extraClasses) {
        return fill(PINE, size, extraClasses);
    }

    /** @return stroked sun icon (light-mode indicator on the theme toggle) */
    public static Node sun(double size, String... extraClasses) {
        return stroke(SUN, size, extraClasses);
    }

    /** @return filled crescent-moon icon (dark-mode indicator on the theme toggle) */
    public static Node moon(double size, String... extraClasses) {
        return fill(MOON, size, extraClasses);
    }

    /** @return stroked calendar icon (reservation / order empty states) */
    public static Node calendar(double size, String... extraClasses) {
        return stroke(CALENDAR, size, extraClasses);
    }

    /** @return stroked hourglass icon (waiting-list empty state) */
    public static Node hourglass(double size, String... extraClasses) {
        return stroke(HOURGLASS, size, extraClasses);
    }

    /** @return stroked inbox-tray icon (approvals / requests empty states) */
    public static Node inbox(double size, String... extraClasses) {
        return stroke(INBOX, size, extraClasses);
    }

    /** @return stroked check-circle icon (success toasts) */
    public static Node checkCircle(double size, String... extraClasses) {
        return stroke(CHECK_CIRCLE, size, extraClasses);
    }

    /** @return stroked alert-triangle icon (error toasts) */
    public static Node alertTriangle(double size, String... extraClasses) {
        return stroke(ALERT_TRIANGLE, size, extraClasses);
    }

    /** @return stroked clipboard icon (view / manage reservations) */
    public static Node clipboard(double size, String... extraClasses) {
        return stroke(CLIPBOARD, size, extraClasses);
    }

    /** @return stroked x-in-circle icon (cancel / remove actions) */
    public static Node xCircle(double size, String... extraClasses) {
        return stroke(X_CIRCLE, size, extraClasses);
    }

    /** @return stroked door-with-arrow icon (register exit / leave) */
    public static Node exit(double size, String... extraClasses) {
        return stroke(DOOR_EXIT, size, extraClasses);
    }

    /** @return stroked circular-arrow icon (refresh / reload) */
    public static Node refresh(double size, String... extraClasses) {
        return stroke(REFRESH, size, extraClasses);
    }

    /* ── internals ──────────────────────────────────────────────── */

    /** outline icon: transparent fill, rounded 1.7px stroke; colour via {@code .ikon-stroke}. */
    private static Node stroke(String path, double size, String... extraClasses) {
        SVGPath p = new SVGPath();
        p.setContent(path);
        p.setFill(Color.TRANSPARENT);
        p.setStrokeWidth(1.7);
        p.setStrokeLineCap(StrokeLineCap.ROUND);
        p.setStrokeLineJoin(StrokeLineJoin.ROUND);
        p.getStyleClass().add("ikon-stroke");
        p.getStyleClass().addAll(extraClasses);
        return sized(p, size);
    }

    /** solid icon: filled shape, no stroke; colour via {@code .ikon-fill}. */
    private static Node fill(String path, double size, String... extraClasses) {
        SVGPath p = new SVGPath();
        p.setContent(path);
        p.getStyleClass().add("ikon-fill");
        p.getStyleClass().addAll(extraClasses);
        return sized(p, size);
    }

    /**
     * scales the 24-grid path to the requested size. the path is wrapped in a {@link Group}
     * because a Group's layout bounds include its children's transforms; a bare scaled
     * SVGPath would keep its unscaled layout bounds and misalign next to text.
     */
    private static Node sized(SVGPath p, double size) {
        double s = size / 24.0;
        p.setScaleX(s);
        p.setScaleY(s);
        return new Group(p);
    }
}
