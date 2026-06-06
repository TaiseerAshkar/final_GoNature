package logic;

import java.io.Serializable;

/**
 * one aggregated row in the Visitor Report.
 *
 * <p>total visitors for a specific date and order type over the last 30 days.
 * used to populate the bar chart in {@link gui.ReportsController}, split by
 * {@code "SOLO"} vs {@code "GROUP"}.
 *
 * <p>part of a {@code VISITOR_REPORT_DATA} message.
 */
public class ReportVisitorRow implements Serializable {

    private static final long serialVersionUID = 1L;

    private final String dayLabel;       // "MM/dd" — bar-chart X-axis label
    private final String visitDate;      // "yyyy-MM-dd" — used for sorting/grouping
    private final String orderType;      // "SOLO" or "GROUP" (uppercase)
    private final int    totalVisitors;

    /**
     * @param dayLabel       the display label for the X-axis in {@code MM/dd} format
     * @param visitDate      the full date in {@code yyyy-MM-dd} format (for grouping)
     * @param orderType      the visit type: {@code "SOLO"} or {@code "GROUP"}
     * @param totalVisitors  the summed visitor count for this date and type
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

    /** @return the visit type in uppercase ({@code "SOLO"} or {@code "GROUP"}) */
    public String getOrderType()     { return orderType; }

    /** @return the total number of visitors for this date and type */
    public int    getTotalVisitors() { return totalVisitors; }
}
