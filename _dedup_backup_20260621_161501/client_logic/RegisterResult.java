package logic;

import java.io.Serializable;

/**
 * server response to a {@code REGISTER_VISITOR} request.
 *
 * <p>payload of {@code REGISTER_SUCCESS} on success or {@code REGISTER_FAIL} on failure.
 * when the visitor registered as a subscriber, {@link #getSubscriberId()} returns the
 * auto-generated ID from the {@code subscribers} table.
 */
public class RegisterResult implements Serializable {

    private static final long serialVersionUID = 1L;

    private final boolean success;
    private final String  message;
    /** non-null only when a subscriber row was created successfully. */
    private final Integer subscriberId;

    /**
     * @param success      {@code true} if the visitor was saved to the database
     * @param message      a human-readable summary shown to the user
     * @param subscriberId the auto-generated subscriber ID, or {@code null} if not a subscriber
     *                     or if subscriber insertion failed
     */
    public RegisterResult(boolean success, String message, Integer subscriberId) {
        this.success      = success;
        this.message      = message;
        this.subscriberId = subscriberId;
    }

    /** @return {@code true} if the visitor account was created successfully */
    public boolean isSuccess()          { return success; }

    /** @return a human-readable summary message */
    public String  getMessage()         { return message; }

    /** @return the subscriber ID, or {@code null} if registration didn't include a subscriber record */
    public Integer getSubscriberId()    { return subscriberId; }
}
