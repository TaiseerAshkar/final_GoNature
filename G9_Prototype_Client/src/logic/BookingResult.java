package logic;

import java.io.Serializable;

/**
 * immutable confirmation returned by the server after a booking or waiting-list request.
 *
 * <p>payload of {@code BOOKING_CONFIRMED} (status {@code "CONFIRMED"}) or
 * {@code WAITING_LIST_CONFIRMED} (status {@code "WAITING"}).
 * displayed in {@link gui.BookingConfirmationController}.
 */
public class BookingResult implements Serializable {

    private static final long serialVersionUID = 1L;

    private final int    id;
    private final String status;     // "CONFIRMED" or "WAITING"
    private final String parkName;
    private final String visitDate;  // "yyyy-MM-dd"
    private final String visitTime;  // "HH:mm"
    private final int    numVisitors;
    private final String orderType;
    private final String email;

    /**
     * @param id          the generated order ID (or waiting-list ID when {@code status = "WAITING"})
     * @param status      {@code "CONFIRMED"} for a confirmed booking, {@code "WAITING"} for waiting list
     * @param parkName    the park name for display
     * @param visitDate   the visit date in {@code yyyy-MM-dd} format
     * @param visitTime   the visit time in {@code HH:mm} format
     * @param numVisitors the number of visitors
     * @param orderType   the visit type ({@code "SOLO"} or {@code "GROUP"})
     * @param email       the contact email used for this booking
     */
    public BookingResult(int id, String status, String parkName,
                         String visitDate, String visitTime,
                         int numVisitors, String orderType, String email) {
        this.id          = id;
        this.status      = status;
        this.parkName    = parkName;
        this.visitDate   = visitDate;
        this.visitTime   = visitTime;
        this.numVisitors = numVisitors;
        this.orderType   = orderType;
        this.email       = email;
    }

    /** @return the generated order or waiting-list ID */
    public int    getId()          { return id; }

    /** @return {@code "CONFIRMED"} or {@code "WAITING"} */
    public String getStatus()      { return status; }

    /** @return the park name */
    public String getParkName()    { return parkName; }

    /** @return the visit date in {@code yyyy-MM-dd} format */
    public String getVisitDate()   { return visitDate; }

    /** @return the visit time in {@code HH:mm} format */
    public String getVisitTime()   { return visitTime; }

    /** @return the number of visitors */
    public int    getNumVisitors() { return numVisitors; }

    /** @return the order type */
    public String getOrderType()   { return orderType; }

    /** @return the contact email for this booking */
    public String getEmail()       { return email; }
}
