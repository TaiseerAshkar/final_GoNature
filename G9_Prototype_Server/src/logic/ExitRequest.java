package logic;

import java.io.Serializable;

/**
 * request sent by a park employee to register a visitor's exit.
 *
 * <p>the server stamps {@code exit_time = NOW()} on the most recent open entry
 * for this visitor at this park today.
 */
public class ExitRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    private final String visitorId;
    private final int    parkId;

    /**
     * @param visitorId the visitor's ID number
     * @param parkId    the database ID of the park being exited
     */
    public ExitRequest(String visitorId, int parkId) {
        this.visitorId = visitorId;
        this.parkId    = parkId;
    }

    /** @return the visitor's ID number */
    public String getVisitorId() { return visitorId; }

    /** @return the database park ID */
    public int    getParkId()    { return parkId; }
}
