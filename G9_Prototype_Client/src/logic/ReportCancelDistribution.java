package logic;

import java.io.Serializable;
import java.util.List;

/**
 * aggregated cancellation distribution for the department-manager cancellation report:
 * how cancelled + no-show orders spread across the seven days of the week, for a single
 * park or the whole region, within one calendar month.
 *
 * <p>backs the day-of-week bar chart shown alongside the detailed cancellation table.
 * the average is exposed two ways: per weekday bucket ({@link #getAvgPerWeekday()} — the
 * mean bar height, total ÷ 7) and per active day ({@link #getAvgPerWorkday()} — total ÷
 * distinct cancellation days, matching the per-operating-day style of the usage report).
 */
public class ReportCancelDistribution implements Serializable {

    private static final long serialVersionUID = 1L;

    private final List<String>  dayLabels;        // "Sun".."Sat", Sunday-first
    private final List<Integer> counts;           // parallel to dayLabels
    private final int           total;            // total cancelled + no-show in the period
    private final int           distinctWorkdays; // distinct calendar dates with >=1 cancellation

    /**
     * @param dayLabels        the seven weekday labels, Sunday-first ({@code "Sun".."Sat"})
     * @param counts           cancellation counts, one per weekday, parallel to {@code dayLabels}
     * @param total            total cancelled + no-show orders in the period
     * @param distinctWorkdays number of distinct calendar dates with at least one cancellation
     */
    public ReportCancelDistribution(List<String> dayLabels, List<Integer> counts,
                                    int total, int distinctWorkdays) {
        this.dayLabels        = dayLabels;
        this.counts           = counts;
        this.total            = total;
        this.distinctWorkdays = distinctWorkdays;
    }

    /** @return the seven weekday labels in Sunday-first order ({@code "Sun".."Sat"}) */
    public List<String>  getDayLabels()        { return dayLabels; }

    /** @return cancellation counts, one per weekday, parallel to {@link #getDayLabels()} */
    public List<Integer> getCounts()           { return counts; }

    /** @return total cancelled + no-show orders in the period */
    public int           getTotal()            { return total; }

    /** @return number of distinct calendar dates on which at least one cancellation fell */
    public int           getDistinctWorkdays() { return distinctWorkdays; }

    /** @return mean cancellations per weekday bucket (total ÷ 7) — the average bar height */
    public double getAvgPerWeekday() { return total / 7.0; }

    /**
     * @return mean cancellations per active day (total ÷ distinct workdays), or {@code 0} when
     *         the period has no cancellations
     */
    public double getAvgPerWorkday() {
        return distinctWorkdays > 0 ? (double) total / distinctWorkdays : 0.0;
    }

    /** @return the weekday label with the most cancellations, or {@code "—"} if the period is empty */
    public String getPeakDay() {
        if (counts == null || counts.isEmpty()) return "—";
        int maxIdx = 0;
        for (int i = 1; i < counts.size(); i++) {
            if (counts.get(i) > counts.get(maxIdx)) maxIdx = i;
        }
        return counts.get(maxIdx) > 0 ? dayLabels.get(maxIdx) : "—";
    }

    /** @return the highest single-weekday count, or {@code 0} if the period is empty */
    public int getPeakCount() {
        int max = 0;
        if (counts != null) for (int c : counts) if (c > max) max = c;
        return max;
    }
}
