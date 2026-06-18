package client;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * in-memory notification log for the current client session.
 *
 * <p>simulates SMS/email delivery; no real messages are sent.
 * controllers push events here at the right moments (booking confirmed,
 * cancellation, waiting-list alert, visit reminder); the visitor home
 * screen reads the list and renders it as a notification inbox.
 *
 * <p>entries are added newest-first and capped at 50 so the list
 * stays bounded across a long session.
 */
public class NotificationCenter {

    private static final DateTimeFormatter TIME_FMT =
        DateTimeFormatter.ofPattern("HH:mm");

    private static final List<String> entries = new ArrayList<>();

    /**
     * pushes a new notification to the top of the list.
     *
     * @param icon    emoji prefix, e.g. {@code "✅"}, {@code "📨"}, {@code "🔔"}
     * @param message the human-readable notification body
     */
    public static void add(String icon, String message) {
        String ts = LocalDateTime.now().format(TIME_FMT);
        entries.add(0, ts + "   " + icon + "  " + message);
        if (entries.size() > 50) entries.remove(entries.size() - 1);
    }

    /** @return an unmodifiable newest-first view of all notifications */
    public static List<String> getAll() {
        return Collections.unmodifiableList(entries);
    }

    /** @return how many notifications are currently stored */
    public static int count() { return entries.size(); }

    /** wipes all entries; call on logout so the next user starts clean. */
    public static void clear() { entries.clear(); }
}
