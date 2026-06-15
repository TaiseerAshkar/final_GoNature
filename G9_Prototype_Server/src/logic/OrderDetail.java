package logic;

import java.io.Serializable;

/**
 * a single order row from the server.
 *
 * <p>maps to the main {@code orders} table — used for My Orders, Today's Reservations,
 * the Management Dashboard, and the Waiting List view.
 */
public class OrderDetail implements Serializable {

    private static final long serialVersionUID = 1L;

    private final int    id;
    private final String parkName;
    private final String visitorId;  // government ID of the booking visitor
    private final String visitDate;  // "yyyy-MM-dd"
    private final String visitTime;  // "HH:mm"
    private final int    numVisitors;
    private final String orderType;  // "INDIVIDUAL" | "FAMILY" | "GROUP"  (uppercase)
    private final String status;     // "PENDING" | "CONFIRMED" | "CANCELLED" | "COMPLETED" | "WAITING" (uppercase)
    private final String email;
    private final String createdAt;  // "yyyy-MM-dd HH:mm"
    /** non-null only for PENDING waitlist-promotion orders; the deadline to confirm by. */
    private final String confirmationDeadline; // "yyyy-MM-dd HH:mm" or null

    /**
     * @param id          the database order ID
     * @param parkName    the park name
     * @param visitorId   the visitor's government ID
     * @param visitDate   the planned visit date in {@code yyyy-MM-dd} format
     * @param visitTime   the planned visit start time in {@code HH:mm} format
     * @param numVisitors the number of visitors on the order
     * @param orderType   the order type in uppercase ({@code "INDIVIDUAL"}, {@code "FAMILY"}, or {@code "GROUP"})
     * @param status      the order status in uppercase (e.g., {@code "PENDING"}, {@code "CONFIRMED"})
     * @param email       the contact email address for notifications
     * @param createdAt   the booking creation timestamp in {@code yyyy-MM-dd HH:mm} format
     */
    public OrderDetail(int id, String parkName, String visitorId,
                       String visitDate, String visitTime,
                       int numVisitors, String orderType, String status,
                       String email, String createdAt) {
        this(id, parkName, visitorId, visitDate, visitTime,
             numVisitors, orderType, status, email, createdAt, null);
    }

    /**
     * extended constructor for waitlist-promotion orders that carry a confirmation deadline.
     *
     * @param confirmationDeadline the deadline by which the visitor must confirm, in
     *                             {@code yyyy-MM-dd HH:mm} format, or {@code null}
     */
    public OrderDetail(int id, String parkName, String visitorId,
                       String visitDate, String visitTime,
                       int numVisitors, String orderType, String status,
                       String email, String createdAt, String confirmationDeadline) {
        this.id                   = id;
        this.parkName             = parkName;
        this.visitorId            = visitorId;
        this.visitDate            = visitDate;
        this.visitTime            = visitTime;
        this.numVisitors          = numVisitors;
        this.orderType            = orderType;
        this.status               = status;
        this.email                = email;
        this.createdAt            = createdAt;
        this.confirmationDeadline = confirmationDeadline;
    }

    /** @return the database order ID */
    public int    getId()          { return id; }

    /** @return the park name */
    public String getParkName()    { return parkName; }

    /** @return the visitor's government ID */
    public String getVisitorId()   { return visitorId; }

    /** @return the planned visit date in {@code yyyy-MM-dd} format */
    public String getVisitDate()   { return visitDate; }

    /** @return the planned visit start time in {@code HH:mm} format */
    public String getVisitTime()   { return visitTime; }

    /** @return the number of visitors on the order */
    public int    getNumVisitors() { return numVisitors; }

    /** @return the order type in uppercase */
    public String getOrderType()   { return orderType; }

    /** @return the order status in uppercase */
    public String getStatus()      { return status; }

    /** @return the contact email address */
    public String getEmail()       { return email; }

    /** @return the booking creation timestamp in {@code yyyy-MM-dd HH:mm} format */
    public String getCreatedAt()   { return createdAt; }

    /** @return the confirmation deadline for a waitlist-promotion order, or {@code null} */
    public String getConfirmationDeadline() { return confirmationDeadline; }
}
