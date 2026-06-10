package gui;

import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;

/**
 * small shared helpers for rendering table cells consistently across screens.
 *
 * <p>currently provides {@link #applyStatusChip(TableColumn)}, which renders an order's
 * status string as a colour-coded "chip" pill instead of plain text, using the
 * {@code .chip} / {@code .chip-*} rules defined in {@code gonature.css}.
 */
public final class UiCells {

    /** utility class — no instances. */
    private UiCells() {}

    /**
     * installs a cell factory that draws the order-status string as a coloured chip pill.
     * the value factory must already be set on the column.
     *
     * @param col the status column to decorate
     * @param <S> the table row type (e.g. {@code OrderDetail})
     */
    public static <S> void applyStatusChip(TableColumn<S, String> col) {
        col.setCellFactory(c -> new TableCell<S, String>() {
            private final Label chip = new Label();
            @Override
            protected void updateItem(String status, boolean empty) {
                super.updateItem(status, empty);
                if (empty || status == null || status.isBlank()) {
                    setGraphic(null);
                    setText(null);
                    return;
                }
                chip.setText(status);
                chip.getStyleClass().setAll("chip", chipClassFor(status));
                setGraphic(chip);
                setText(null);
                setAlignment(Pos.CENTER_LEFT);
            }
        });
    }

    /**
     * maps an order status to its chip style class.
     *
     * @param status the status string
     * @return the matching {@code .chip-*} class name
     */
    private static String chipClassFor(String status) {
        switch (status) {
            case "CONFIRMED": return "chip-confirmed";
            case "PENDING":   return "chip-pending";
            case "COMPLETED": return "chip-completed";
            case "CANCELLED": return "chip-cancelled";
            case "NO_SHOW":   return "chip-noshow";
            case "WAITING":   return "chip-waiting";
            default:          return "chip-completed";
        }
    }
}
