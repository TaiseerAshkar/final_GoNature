package logic;

import java.io.Serializable;

/**
 * value object used by the department-manager and service-rep screens to carry
 * {@code UPDATE_ORDER} request data (order ID, new visit date, new visitor count).
 * distinct from {@link OrderDetail} which is the full read-only order projection
 * used for display throughout the app.
 */
public class Order implements Serializable {

    private static final long serialVersionUID = 1L;

    private int    orderNumber;
    private String orderDate;           // "YYYY-MM-DD"
    private int    numberOfVisitors;
    private int    confirmationCode;
    private int    subscriberId;
    private String dateOfPlacingOrder;  // "YYYY-MM-DD"
    private String orderType;           // display name: "SOLO" | "FAMILY" | "GROUP"

    /**
     * @param orderNumber        the unique order identifier
     * @param orderDate          the scheduled visit date in {@code YYYY-MM-DD} format
     * @param numberOfVisitors   the number of visitors on the order
     * @param confirmationCode   the numeric confirmation code sent to the visitor
     * @param subscriberId       the ID of the subscriber who placed the order
     * @param dateOfPlacingOrder the date the order was created in {@code YYYY-MM-DD} format
     */
    public Order(int orderNumber, String orderDate, int numberOfVisitors,
                 int confirmationCode, int subscriberId, String dateOfPlacingOrder) {
        this(orderNumber, orderDate, numberOfVisitors, confirmationCode, subscriberId,
             dateOfPlacingOrder, "");
    }

    /**
     * @param orderNumber        the unique order identifier
     * @param orderDate          the scheduled visit date in {@code YYYY-MM-DD} format
     * @param numberOfVisitors   the number of visitors on the order
     * @param confirmationCode   the numeric confirmation code sent to the visitor
     * @param subscriberId       the ID of the subscriber who placed the order
     * @param dateOfPlacingOrder the date the order was created in {@code YYYY-MM-DD} format
     * @param orderType          the display visit type: {@code "SOLO"}, {@code "FAMILY"}, or {@code "GROUP"}
     */
    public Order(int orderNumber, String orderDate, int numberOfVisitors,
                 int confirmationCode, int subscriberId, String dateOfPlacingOrder,
                 String orderType) {
        this.orderNumber        = orderNumber;
        this.orderDate          = orderDate;
        this.numberOfVisitors   = numberOfVisitors;
        this.confirmationCode   = confirmationCode;
        this.subscriberId       = subscriberId;
        this.dateOfPlacingOrder = dateOfPlacingOrder;
        this.orderType          = orderType != null ? orderType : "";
    }

    /** @return the unique order number */
    public int    getOrderNumber()          { return orderNumber; }

    /** @return the visit date in {@code YYYY-MM-DD} format */
    public String getOrderDate()            { return orderDate; }

    /** @return the number of visitors on the order */
    public int    getNumberOfVisitors()     { return numberOfVisitors; }

    /** @return the numeric confirmation code */
    public int    getConfirmationCode()     { return confirmationCode; }

    /** @return the subscriber ID who placed the order */
    public int    getSubscriberId()         { return subscriberId; }

    /** @return the date the order was placed in {@code YYYY-MM-DD} format */
    public String getDateOfPlacingOrder()   { return dateOfPlacingOrder; }

    /** @return the display visit type: SOLO, FAMILY, or GROUP */
    public String getOrderType()            { return orderType; }

    /** @param orderDate the new visit date in {@code YYYY-MM-DD} format */
    public void setOrderDate(String orderDate)             { this.orderDate = orderDate; }

    /** @param numberOfVisitors the new visitor count; must be positive */
    public void setNumberOfVisitors(int numberOfVisitors)  { this.numberOfVisitors = numberOfVisitors; }

    /** @param orderType the new display visit type */
    public void setOrderType(String orderType)             { this.orderType = orderType; }

    /**
     * @return a readable one-line summary of the order's key fields,
     *         intended for logging and debugging rather than display to users
     */
    @Override
    public String toString() {
        return "Order #" + orderNumber +
               " | Date: " + orderDate +
               " | Visitors: " + numberOfVisitors +
               " | ConfCode: " + confirmationCode +
               " | SubID: " + subscriberId +
               " | Placed: " + dateOfPlacingOrder;
    }
}
