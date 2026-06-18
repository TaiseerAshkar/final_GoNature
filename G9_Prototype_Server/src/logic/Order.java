package logic;

import java.io.Serializable;

/**
 * value object used by the department-manager and service-rep screens to carry
 * {@code UPDATE_ORDER} request data (order ID, new visit date, new visitor count).
 * distinct from {@link OrderDetail}, which maps to the main {@code orders} table.
 */
public class Order implements Serializable {

    private static final long serialVersionUID = 1L;

    private int    orderNumber;
    private String orderDate;        // "YYYY-MM-DD"
    private int    numberOfVisitors;
    private String orderType;        // display name: "SOLO" | "FAMILY" | "GROUP"

    /**
     * @param orderNumber      the unique order identifier
     * @param orderDate        the scheduled visit date in {@code YYYY-MM-DD} format
     * @param numberOfVisitors the number of visitors on the order
     */
    public Order(int orderNumber, String orderDate, int numberOfVisitors) {
        this(orderNumber, orderDate, numberOfVisitors, "");
    }

    /**
     * @param orderNumber      the unique order identifier
     * @param orderDate        the scheduled visit date in {@code YYYY-MM-DD} format
     * @param numberOfVisitors the number of visitors on the order
     * @param orderType        the display visit type: {@code "SOLO"}, {@code "FAMILY"}, or {@code "GROUP"}
     */
    public Order(int orderNumber, String orderDate, int numberOfVisitors, String orderType) {
        this.orderNumber      = orderNumber;
        this.orderDate        = orderDate;
        this.numberOfVisitors = numberOfVisitors;
        this.orderType        = orderType != null ? orderType : "";
    }

    /** @return the unique order number */
    public int    getOrderNumber()      { return orderNumber; }

    /** @return the visit date in {@code YYYY-MM-DD} format */
    public String getOrderDate()        { return orderDate; }

    /** @return the number of visitors on the order */
    public int    getNumberOfVisitors() { return numberOfVisitors; }

    /** @return the display visit type: SOLO, FAMILY, or GROUP */
    public String getOrderType()        { return orderType; }

    /**
     * @return a readable one-line summary of the order's key fields,
     *         intended for logging and debugging rather than display to users
     */
    @Override
    public String toString() {
        return orderNumber + " | " + orderDate + " | " + numberOfVisitors + " visitors | type:" + orderType;
    }
}
