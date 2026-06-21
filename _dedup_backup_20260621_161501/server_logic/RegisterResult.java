package logic;

import java.io.Serializable;

/**
 * server response to a {@code REGISTER_VISITOR} request.
 *
 * <p>when registration succeeds and the visitor joined the Family Member Club,
 * {@link #getSubscriberId()} returns the auto-generated row ID they should keep as their subscriber ID.
 */
public class RegisterResult implements Serializable {

    private static final long serialVersionUID = 1L;

    private final boolean success;
    private final String  message;
    /** non-null only when a subscriber row was created successfully. */
    private final Integer subscriberId;

    /**
     * @param success      {@code true} if the visitor was saved to the database
     * @param message      a human-readable summary (shown on success or failure)
     * @param subscriberId the auto-generated subscriber ID, or {@code null} if not a subscriber
     */
    public RegisterResult(boolean success, String message, Integer subscriberId) {
        this.success      = success;
        this.message      = message;
        this.subscriberId = subscriberId;
    }

    /** @return {@code true} if the visitor account was created successfully */
    public boolean isSuccess()       { return success; }

    /** @return a human-readable summary message */
    public String  getMessage()      { return message; }

    /** @return the auto-generated subscriber ID, or {@code null} if not a subscriber */
    public Integer getSubscriberId() { return subscriberId; }
}
