package logic;

import java.io.Serializable;

/**
 * one row in the Average Visit Duration report. it holds the average length of stay
 * (in hours) for a single visitor type over the selected calendar month.
 *
 * <p>only completed visits with both an {@code entry_time} and an {@code exit_time}
 * contribute to the average; the duration of each visit is
 * {@code TIMESTAMPDIFF(MINUTE, entry_time, exit_time) / 60.0} hours.
 *
 * <p>the client plots {@link #getAvgHours()} on the bar chart in
 * {@link gui.ReportsController}, one bar per visitor type.
 *
 * <p>part of a {@code DURATION_REPORT_DATA} message.
 */
public class ReportDurationRow implements Serializable {

    private static final long serialVersionUID = 1L;

    private final String visitorType;   // "Individual" or "Group"
    private final double avgHours;       // average visit duration in hours

    /**
     * @param visitorType the visitor type bucket ({@code "Individual"} or {@code "Group"})
     * @param avgHours    the average visit duration in hours for that type
     */
    public ReportDurationRow(String visitorType, double avgHours) {
        this.visitorType = visitorType;
        this.avgHours    = avgHours;
    }

    /** @return the visitor type bucket ({@code "Individual"} or {@code "Group"}) */
    public String getVisitorType() { return visitorType; }

    /** @return the average visit duration in hours for this visitor type */
    public double getAvgHours()    { return avgHours; }
}
