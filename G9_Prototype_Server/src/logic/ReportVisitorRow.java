package logic;

import java.io.Serializable;

/**
 * one aggregated row in the visitor report.
 *
 * <p>total visitors for a specific date and order type over the last 30 days.
 * the client groups rows by date and splits series by order type for the bar chart.
 */
public class ReportVisitorRow implements Serializable {

    private static final long serialVersionUID = 1L;

    private final String dayLabel;       // "MM/dd" — chart X-axis label
    private final String visitDate;      // "yyyy-MM-dd" — used for grouping
    private final String orderType;      // "INDIVIDUAL" or "GROUP" (uppercase)
    private final int    totalVisitors;

    /**
     * @param dayLabel      the display label for the X-axis in {@code MM/dd} format
     * @param visitDate     the full date in {@code yyyy-MM-dd} format (for grouping)
     * @param orderType     the order type, either {@code "INDIVIDUAL"} or {@code "GROUP"}
     * @param totalVisitors the summed visitor count for this date and type
     */
    public ReportVisitorRow(String dayLabel, String visitDate,
                            String orderType, int totalVisitors) {
        this.dayLabel      = dayLabel;
        this.visitDate     = visitDate;
        this.orderType     = orderType;
        this.totalVisitors = totalVisitors;
    }

    /** @return the X-axis display label in {@code MM/dd} format */
    public String getDayLabel()      { return dayLabel; }

    /** @return the full date in {@code yyyy-MM-dd} format */
    public String getVisitDate()     { return visitDate; }

    /** @return the order type in uppercase ({@code "INDIVIDUAL"} or {@code "GROUP"}) */
    public String getOrderType()     { return orderType; }

    /** @return the total number of visitors for this date and type */
    public int    getTotalVisitors() { return totalVisitors; }
}
