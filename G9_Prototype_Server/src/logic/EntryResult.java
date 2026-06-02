package logic;

import java.io.Serializable;

/**
 * result returned by the server after a gate check-in attempt.
 *
 * <p>sent as the payload of {@code ENTRY_APPROVED} on success, or as the error detail
 * of {@code ENTRY_DENIED} on failure.
 */
public class EntryResult implements Serializable {

    private static final long serialVersionUID = 1L;

    private final boolean success;
    private final boolean prebooked;    // true if a confirmed booking was found, false for walk-in
    private final int     orderId;
    private final String  parkName;
    private final String  visitorId;
    private final int     numVisitors;
    private final String  orderType;
    private final double  totalPrice;
    private final String  message;

    /**
     * @param success      {@code true} if entry was approved
     * @param prebooked    {@code true} if a confirmed booking was found; {@code false} for walk-in
     * @param orderId      the database order ID ({@code -1} if no order exists)
     * @param parkName     the park name for display
     * @param visitorId    the visitor's ID number
     * @param numVisitors  the number of visitors admitted
     * @param orderType    the order type in uppercase (e.g., {@code "INDIVIDUAL"}, {@code "GROUP"})
     * @param totalPrice   the calculated admission price after applicable discounts
     * @param message      a human-readable status or error description
     */
    public EntryResult(boolean success, boolean prebooked, int orderId,
                       String parkName, String visitorId, int numVisitors,
                       String orderType, double totalPrice, String message) {
        this.success     = success;
        this.prebooked   = prebooked;
        this.orderId     = orderId;
        this.parkName    = parkName;
        this.visitorId   = visitorId;
        this.numVisitors = numVisitors;
        this.orderType   = orderType;
        this.totalPrice  = totalPrice;
        this.message     = message;
    }

    /** @return {@code true} if entry was approved */
    public boolean isSuccess()      { return success; }

    /** @return {@code true} if a confirmed booking was matched; {@code false} for walk-in */
    public boolean isPrebooked()    { return prebooked; }

    /** @return the database order ID, or {@code -1} if none */
    public int     getOrderId()     { return orderId; }

    /** @return the park name */
    public String  getParkName()    { return parkName; }

    /** @return the visitor's ID number */
    public String  getVisitorId()   { return visitorId; }

    /** @return the number of visitors admitted */
    public int     getNumVisitors() { return numVisitors; }

    /** @return the order type in uppercase */
    public String  getOrderType()   { return orderType; }

    /** @return the total admission price after discounts */
    public double  getTotalPrice()  { return totalPrice; }

    /** @return a human-readable status or error message */
    public String  getMessage()     { return message; }
}
