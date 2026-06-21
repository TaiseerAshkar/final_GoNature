package logic;

import java.io.Serializable;

/**
 * one row in the Cancellation Report.
 *
 * <p>represents a single cancelled or no-show order. part of a {@code CANCEL_REPORT_DATA}
 * message; displayed with colour-coded status cells in {@link gui.ReportsController}.
 */
public class ReportCancelRow implements Serializable {

    private static final long serialVersionUID = 1L;

    private final int    id;
    private final String parkName;
    private final String visitorId;
    private final String visitDate;    // "yyyy-MM-dd"
    private final String visitTime;    // "HH:mm"
    private final String orderType;    // uppercase
    private final String status;       // "CANCELLED" or "NO_SHOW"
    private final int    numVisitors;
    private final String createdAt;    // "yyyy-MM-dd HH:mm"

    /**
     * @param id           the database order ID
     * @param parkName     the park name
     * @param visitorId    the visitor's ID number
     * @param visitDate    the planned visit date in {@code yyyy-MM-dd} format
     * @param visitTime    the planned visit time in {@code HH:mm} format
     * @param orderType    the order type in uppercase
     * @param status       {@code "CANCELLED"} or {@code "NO_SHOW"} (uppercase)
     * @param numVisitors  the number of visitors on the cancelled order
     * @param createdAt    the booking creation timestamp in {@code yyyy-MM-dd HH:mm} format
     */
    public ReportCancelRow(int id, String parkName, String visitorId,
                           String visitDate, String visitTime,
                           String orderType, String status,
                           int numVisitors, String createdAt) {
        this.id          = id;
        this.parkName    = parkName;
        this.visitorId   = visitorId;
        this.visitDate   = visitDate;
        this.visitTime   = visitTime;
        this.orderType   = orderType;
        this.status      = status;
        this.numVisitors = numVisitors;
        this.createdAt   = createdAt;
    }

    /** @return the database order ID */
    public int    getId()          { return id; }

    /** @return the park name */
    public String getParkName()    { return parkName; }

    /** @return the visitor's ID number */
    public String getVisitorId()   { return visitorId; }

    /** @return the planned visit date in {@code yyyy-MM-dd} format */
    public String getVisitDate()   { return visitDate; }

    /** @return the planned visit time in {@code HH:mm} format */
    public String getVisitTime()   { return visitTime; }

    /** @return the order type in uppercase */
    public String getOrderType()   { return orderType; }

    /** @return {@code "CANCELLED"} or {@code "NO_SHOW"} */
    public String getStatus()      { return status; }

    /** @return the number of visitors on the cancelled order */
    public int    getNumVisitors() { return numVisitors; }

    /** @return the booking creation timestamp in {@code yyyy-MM-dd HH:mm} format */
    public String getCreatedAt()   { return createdAt; }
}
