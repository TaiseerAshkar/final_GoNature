package logic;

import java.io.Serializable;

/**
 * immutable booking request sent from the client to the server.
 *
 * <p>used as the payload of both {@code BOOK_ORDER} and {@code JOIN_WAITING_LIST}.
 * the server validates and inserts into the {@code orders} table.
 */
public class BookingRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    private final String visitorId;
    private final int    parkId;
    /** carried for display in the confirmation screen without a second DB round-trip. */
    private final String parkName;
    private final String visitDate;   // "yyyy-MM-dd"
    private final String visitTime;   // "HH:mm"
    private final int    numVisitors;
    private final String orderType;   // "SOLO" | "GROUP"
    private final String email;

    /**
     * @param visitorId   the visitor's ID number
     * @param parkId      the database ID of the selected park
     * @param parkName    the park name (for client-side display only)
     * @param visitDate   the requested visit date in {@code yyyy-MM-dd} format
     * @param visitTime   the requested visit start time in {@code HH:mm} format
     * @param numVisitors the number of visitors in the party
     * @param orderType   {@code "SOLO"} or {@code "GROUP"}
     * @param email       the contact email address for booking notifications
     */
    public BookingRequest(String visitorId, int parkId, String parkName,
                          String visitDate, String visitTime,
                          int numVisitors, String orderType, String email) {
        this.visitorId   = visitorId;
        this.parkId      = parkId;
        this.parkName    = parkName;
        this.visitDate   = visitDate;
        this.visitTime   = visitTime;
        this.numVisitors = numVisitors;
        this.orderType   = orderType;
        this.email       = email;
    }

    /** @return the visitor's ID number */
    public String getVisitorId()   { return visitorId; }

    /** @return the database park ID */
    public int    getParkId()      { return parkId; }

    /** @return the park name (for display use only; not persisted separately) */
    public String getParkName()    { return parkName; }

    /** @return the visit date in {@code yyyy-MM-dd} format */
    public String getVisitDate()   { return visitDate; }

    /** @return the visit start time in {@code HH:mm} format */
    public String getVisitTime()   { return visitTime; }

    /** @return the number of visitors in the party */
    public int    getNumVisitors() { return numVisitors; }

    /** @return the visit type: {@code "SOLO"} or {@code "GROUP"} */
    public String getOrderType()   { return orderType; }

    /** @return the contact email address for this booking */
    public String getEmail()       { return email; }
}
