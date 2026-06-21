package logic;

import java.io.Serializable;

/**
 * result returned by the server after a cancellation attempt.
 *
 * <p>cancellation marks the order as cancelled and frees up its capacity, so
 * the slot is excluded from {@code getAvailableSpots()} and a new booking can
 * replace it. if a waiting-list visitor was promoted into the freed slot,
 * {@link #getNotifiedEmail()} returns the email they were notified at; otherwise {@code null}.
 */
public class CancelResult implements Serializable {

    private static final long serialVersionUID = 1L;

    private final boolean success;
    /** the promoted waiting-list visitor's email, or {@code null} if nobody was promoted. */
    private final String  notifiedEmail;

    /**
     * @param success       {@code true} if the order was successfully cancelled
     * @param notifiedEmail the promoted waiting-list visitor's email, or {@code null} if none
     */
    public CancelResult(boolean success, String notifiedEmail) {
        this.success       = success;
        this.notifiedEmail = notifiedEmail;
    }

    /** @return {@code true} if the order status was changed to {@code cancelled} */
    public boolean isSuccess()        { return success; }

    /** @return the promoted waiting-list visitor's email, or {@code null} if nobody was promoted */
    public String  getNotifiedEmail() { return notifiedEmail; }
}
