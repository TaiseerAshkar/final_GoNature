package logic;

import java.io.Serializable;

/**
 * request sent by a park employee to check in a visitor at the gate.
 *
 * <p>the server first looks for a confirmed booking for today. if found,
 * {@code numVisitors} and {@code orderType} are ignored and the booking values
 * are used instead. if no booking exists, those fields describe the walk-in entry.
 *
 * <p>sent as the payload of {@code CHECK_IN_VISITOR}.
 */
public class EntryCheckRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    private final String visitorId;
    private final int    parkId;
    /** walk-in only; ignored when a pre-booking is matched. */
    private final int    numVisitors;
    /** walk-in only; ignored when a pre-booking is matched. */
    private final String orderType;

    /**
     * @param visitorId   the visitor's ID number
     * @param parkId      the database ID of the park being entered
     * @param numVisitors the number of walk-in visitors (ignored if a booking exists)
     * @param orderType   the walk-in visit type: {@code "SOLO"} or {@code "GROUP"}
     *                    (ignored if a booking exists)
     */
    public EntryCheckRequest(String visitorId, int parkId, int numVisitors, String orderType) {
        this.visitorId   = visitorId;
        this.parkId      = parkId;
        this.numVisitors = numVisitors;
        this.orderType   = orderType;
    }

    /** @return the visitor's ID number */
    public String getVisitorId()   { return visitorId; }

    /** @return the database park ID */
    public int    getParkId()      { return parkId; }

    /** @return the walk-in visitor count (server ignores this if a booking is found) */
    public int    getNumVisitors() { return numVisitors; }

    /** @return the walk-in order type (server ignores this if a booking is found) */
    public String getOrderType()   { return orderType; }
}
