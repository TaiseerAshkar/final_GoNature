package Server;

/**
 * server-only value object for orders that need reminder notifications.
 *
 * <p>populated by {@link DBController#getTomorrowOrdersForReminder()} and consumed by
 * {@link ReminderService} to log simulation messages and schedule auto-cancellation.
 * not serialised; lives entirely within the server process.
 */
class ReminderOrderInfo {

    final int     id;             // database order ID
    final String  email;          // may be empty for walk-in orders
    final String  visitDate;      // "yyyy-MM-dd"
    final String  visitTime;      // "HH:mm"
    final String  parkName;
    final boolean reminderSent;   // true = email already sent on a previous server run

    /**
     * @param id           the database order ID
     * @param email        the contact email on the order
     * @param visitDate    the visit date in {@code yyyy-MM-dd} format
     * @param visitTime    the visit start time in {@code HH:mm} format
     * @param parkName     the park name for display in simulation messages
     * @param reminderSent whether a reminder email was already sent on a prior run
     */
    ReminderOrderInfo(int id, String email,
                      String visitDate, String visitTime, String parkName,
                      boolean reminderSent) {
        this.id           = id;
        this.email        = email;
        this.visitDate    = visitDate;
        this.visitTime    = visitTime;
        this.parkName     = parkName;
        this.reminderSent = reminderSent;
    }
}
