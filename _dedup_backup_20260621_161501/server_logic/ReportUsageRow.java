package logic;

import java.io.Serializable;

/**
 * one row in the usage report: average visitor load at an hour slot in a park over 30 days.
 *
 * <p>the client divides by capacity × 100 to get percentage-of-capacity for the line chart.
 */
public class ReportUsageRow implements Serializable {

    private static final long serialVersionUID = 1L;

    private final String parkName;
    private final String hourSlot;    // "HH:00"
    private final int    capacity;
    private final double avgPerDay;

    /**
     * @param parkName   the park name (series label on the chart)
     * @param hourSlot   the hour slot string in {@code HH:00} format
     * @param capacity   the park's maximum simultaneous visitor capacity
     * @param avgPerDay  the average number of visitors booked at this slot per day
     */
    public ReportUsageRow(String parkName, String hourSlot,
                          int capacity, double avgPerDay) {
        this.parkName  = parkName;
        this.hourSlot  = hourSlot;
        this.capacity  = capacity;
        this.avgPerDay = avgPerDay;
    }

    /** @return the park name */
    public String getParkName()  { return parkName; }

    /** @return the hour slot in {@code HH:00} format */
    public String getHourSlot()  { return hourSlot; }

    /** @return the park's maximum simultaneous visitor capacity */
    public int    getCapacity()  { return capacity; }

    /**
     * divide by {@link #getCapacity()} × 100 to get the percentage-of-capacity used in the chart.
     *
     * @return the average daily visitor count at this slot
     */
    public double getAvgPerDay() { return avgPerDay; }
}
