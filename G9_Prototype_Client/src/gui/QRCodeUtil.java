package gui;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.Alert;
import javafx.scene.control.Label;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;

import java.util.Random;

/**
 * simulated QR code generator for booking entry passes.
 *
 * <p>produces a deterministic, visually realistic grid: proper 7×7 finder patterns in three
 * corners, QR timing strips on row/col 6, and a seeded pseudo-random data area so the same
 * payload always renders the same visual. this is not a real QR code; it is a simulation.
 *
 * <p>the encoded payload format is {@code GONATURE:ORDER=<id>:VISITOR=<visitorId>}, which the
 * entry-control "scan" dialog can parse to pre-fill the visitor ID field automatically.
 */
class QRCodeUtil {

    /** modules (cells) per side, matches QR Version 1. */
    private static final int MODULES = 21;

    /**
     * renders a simulated QR code canvas for the given payload.
     *
     * @param payload the string to encode (determines data-area appearance)
     * @param cellPx  pixel size of each module
     * @return a canvas ready to embed in a scene graph
     */
    static Canvas generate(String payload, int cellPx) {
        boolean[][] grid = buildGrid(payload);
        int size = MODULES * cellPx;

        Canvas canvas = new Canvas(size, size);
        GraphicsContext gc = canvas.getGraphicsContext2D();

        gc.setFill(Color.WHITE);
        gc.fillRect(0, 0, size, size);

        gc.setFill(Color.web("#1A1A1A"));
        for (int r = 0; r < MODULES; r++) {
            for (int c = 0; c < MODULES; c++) {
                if (grid[r][c]) {
                    // slight rounding makes it look less like a bitmap grid
                    gc.fillRoundRect(c * cellPx + 0.5, r * cellPx + 0.5,
                                     cellPx - 1, cellPx - 1, 2, 2);
                }
            }
        }
        return canvas;
    }

    /**
     * opens a modal dialog showing the booking QR code and the reference details.
     *
     * @param orderId   the database order ID (shown as a human-readable reference below the code)
     * @param visitorId the visitor's government ID (encoded in the QR payload)
     * @param parkName  the park name (displayed in the dialog subtitle)
     * @param date      visit date in {@code yyyy-MM-dd} format
     * @param time      visit start time in {@code HH:mm} format
     */
    static void showDialog(int orderId, String visitorId,
                           String parkName, String date, String time) {
        String payload = buildPayload(orderId, visitorId);

        Canvas qr = generate(payload, 10);

        // thin black border that looks like a real QR printout
        StackPane qrFrame = new StackPane(qr);
        qrFrame.setStyle("-fx-border-color: #1A1A1A; -fx-border-width: 3;"
                       + "-fx-background-color: white; -fx-padding: 8;");
        qrFrame.setAlignment(Pos.CENTER);
        qrFrame.setMaxWidth(230);

        Label lblRef      = styledLabel("Booking Reference:  ORDER-" + orderId, true,  14);
        Label lblVisitor  = styledLabel("Visitor ID:  " + visitorId,             false, 12);
        Label lblDetails  = styledLabel(parkName + "  ·  " + date + "  at  " + time, false, 12);
        Label lblNote     = styledLabel("Show this code to the park employee at entry gate.", false, 11);
        lblNote.setStyle(lblNote.getStyle() + "; -fx-text-fill: #555555; -fx-font-style: italic;");
        lblNote.setWrapText(true);

        VBox content = new VBox(10, qrFrame, lblRef, lblVisitor, lblDetails, lblNote);
        content.setAlignment(Pos.CENTER);
        content.setPadding(new Insets(8, 0, 4, 0));

        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("Entry QR Code");
        alert.setHeaderText("📱  Your Entry Pass");
        alert.getDialogPane().setContent(content);
        alert.getDialogPane().setMinWidth(300);
        ThemeManager.styleDialog(alert);
        alert.showAndWait();
    }

    /**
     * builds the QR payload string. format: {@code GONATURE:ORDER=<id>:VISITOR=<visitorId>}.
     * the entry-control scanner can parse this to pre-fill the visitor ID field.
     *
     * @param orderId   the order's database ID
     * @param visitorId the visitor's government ID
     * @return the payload string
     */
    static String buildPayload(int orderId, String visitorId) {
        return "GONATURE:ORDER=" + orderId + ":VISITOR=" + visitorId;
    }

    /**
     * parses a visitor ID out of a scanned QR payload (or falls back to the raw input
     * if the string doesn't match the expected format).
     *
     * @param raw the raw string from the scan dialog text field
     * @return the extracted visitor ID, or the original string if parsing fails
     */
    static String parseVisitorId(String raw) {
        if (raw == null) return "";
        raw = raw.trim();
        if (raw.contains("VISITOR=")) {
            try {
                String after = raw.split("VISITOR=")[1];
                // value ends at the next ':' or end of string
                return after.contains(":") ? after.split(":")[0].trim() : after.trim();
            } catch (Exception ignored) {}
        }
        return raw; // assume the user typed the visitor ID directly
    }

    // ── private helpers ───────────────────────────────────────────────────────

    private static boolean[][] buildGrid(String payload) {
        boolean[][] g = new boolean[MODULES][MODULES];

        placeFinderPattern(g, 0,           0);
        placeFinderPattern(g, 0,           MODULES - 7);
        placeFinderPattern(g, MODULES - 7, 0);

        // timing strips on row 6 and column 6
        for (int i = 8; i <= MODULES - 9; i++) {
            g[6][i] = (i % 2 == 0);
            g[i][6] = (i % 2 == 0);
        }

        // data area: deterministic fill so the same payload renders the same visual
        Random rng = new Random((long) payload.hashCode() * 0xDEADBEEFL);
        for (int r = 0; r < MODULES; r++) {
            for (int c = 0; c < MODULES; c++) {
                if (!isReserved(r, c)) {
                    g[r][c] = rng.nextBoolean();
                }
            }
        }
        return g;
    }

    /** places a standard 7×7 QR finder pattern at the given top-left cell. */
    private static void placeFinderPattern(boolean[][] g, int sr, int sc) {
        for (int r = 0; r < 7; r++) {
            for (int c = 0; c < 7; c++) {
                boolean border = (r == 0 || r == 6 || c == 0 || c == 6);
                boolean center = (r >= 2 && r <= 4 && c >= 2 && c <= 4);
                g[sr + r][sc + c] = border || center;
            }
        }
    }

    /**
     * returns true for cells that belong to finder patterns, their quiet-zone
     * separators (1-module border), or the timing strips. these are never overwritten
     * by the data-area fill.
     */
    private static boolean isReserved(int r, int c) {
        boolean topLeft    = r < 9 && c < 9;
        boolean topRight   = r < 9 && c >= MODULES - 8;
        boolean bottomLeft = r >= MODULES - 8 && c < 9;
        boolean timing     = (r == 6 && c >= 8 && c <= MODULES - 9)
                          || (c == 6 && r >= 8 && r <= MODULES - 9);
        return topLeft || topRight || bottomLeft || timing;
    }

    private static Label styledLabel(String text, boolean bold, int size) {
        Label l = new Label(text);
        l.setStyle("-fx-font-size: " + size + "px;"
                 + (bold ? " -fx-font-weight: bold;" : ""));
        return l;
    }
}
