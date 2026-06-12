package gui;

import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;

/**
 * builder for designed table/list empty states — a theme-coloured icon, a short title,
 * and an optional one-line hint — replacing the bare "No data" text placeholders.
 *
 * <p>usage: {@code table.setPlaceholder(EmptyState.of(Icons.calendar(34), "No reservations yet",
 * "Bookings you make will appear here."));} colours come from the {@code empty-title} /
 * {@code empty-hint} CSS classes, so the placeholder adapts to light/dark automatically.
 */
public final class EmptyState {

    /** utility class — no instances. */
    private EmptyState() {}

    /**
     * @param icon  a theme-coloured icon from {@link Icons} (≈34px reads well)
     * @param title short headline, e.g. {@code "No reservations yet"}
     * @param hint  optional second line with guidance; pass {@code null} to omit
     * @return a centred placeholder node for {@code setPlaceholder(...)}
     */
    public static Node of(Node icon, String title, String hint) {
        Label lblTitle = new Label(title);
        lblTitle.getStyleClass().add("empty-title");

        VBox box = new VBox(7);
        box.setAlignment(Pos.CENTER);
        box.getChildren().add(icon);
        box.getChildren().add(lblTitle);

        if (hint != null && !hint.isEmpty()) {
            Label lblHint = new Label(hint);
            lblHint.getStyleClass().add("empty-hint");
            lblHint.setWrapText(true);
            box.getChildren().add(lblHint);
        }
        return box;
    }
}
