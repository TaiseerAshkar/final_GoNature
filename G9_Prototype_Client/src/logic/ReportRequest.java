package logic;

import java.io.Serializable;

/**
 * request payload for the three monthly report queries.
 *
 * <p>carries the report period (year + month) and an optional park filter.
 * sent by the client as the data payload of {@code GET_VISITOR_REPORT},
 * {@code GET_CANCEL_REPORT}, and {@code GET_USAGE_REPORT} messages.
 */
public class ReportRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    private final int parkId;  // 0 = all parks
    private final int year;
    private final int month;   // 1–12

    /**
     * @param parkId  the park to restrict results to, or {@code 0} for all parks
     * @param year    the calendar year (e.g. 2026)
     * @param month   the month number 1–12
     */
    public ReportRequest(int parkId, int year, int month) {
        this.parkId = parkId;
        this.year   = year;
        this.month  = month;
    }

    /** @return the park ID, or {@code 0} for all parks */
    public int getParkId() { return parkId; }

    /** @return the four-digit year */
    public int getYear()   { return year;   }

    /** @return the month number (1 = January … 12 = December) */
    public int getMonth()  { return month;  }
}
