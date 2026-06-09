package logic;

import java.io.Serializable;

/**
 * result returned by the server after a cancellation attempt.
 *
 * <p>cancellation marks the order as cancelled and frees up its capacity —
 * the slot is excluded from {@code getAvailableSpots()} so a new booking can
 * replace it. {@link #getNotifiedEmail()} always returns {@code null}.
 */
public class CancelResult implements Serializable {

    private static final long serialVersionUID = 1L;

    private final boolean success;
    /** always {@code null} — no waiting-list notification is sent automatically on cancellation. */
    private final String  notifiedEmail;

    /**
     * @param success       {@code true} if the order was successfully cancelled
     * @param notifiedEmail unused; always pass {@code null}
     */
    public CancelResult(boolean success, String notifiedEmail) {
        this.success       = success;
        this.notifiedEmail = notifiedEmail;
    }

    /** @return {@code true} if the order status was changed to {@code cancelled} */
    public boolean isSuccess()        { return success; }

    /** @return always {@code null} — cancellations no longer trigger waiting-list notifications */
    public String  getNotifiedEmail() { return notifiedEmail; }
}
