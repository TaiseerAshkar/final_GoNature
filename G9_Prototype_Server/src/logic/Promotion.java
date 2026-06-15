package logic;

import java.io.Serializable;

/**
 * represents one row in the {@code promotions} table.
 *
 * <p>used as the payload for {@code SUBMIT_PROMOTION} (from park manager to server),
 * as a read-only row in the department manager's Pending Promotions panel, and as an
 * entry in the {@code GET_ACTIVE_PROMOTIONS} response used by the pricing engine.
 *
 * <p>mirrors {@code logic.Promotion} in the client project — keep the two in sync so
 * serialization stays compatible.
 */
public class Promotion implements Serializable {

    private static final long serialVersionUID = 1L;

    /** status set by the park manager on submission — awaiting department approval. */
    public static final String PENDING  = "PENDING";
    /** status after the department manager approves — the promotion is live in its date window. */
    public static final String ACTIVE   = "ACTIVE";
    /** status after the department manager rejects — never applied. */
    public static final String REJECTED = "REJECTED";

    private final int    id;               // 0 when used as a submission payload
    private final int    parkId;
    private final String parkName;         // display only — not stored on the row itself
    private final String description;
    private final double discountPercent;  // e.g. 10.0 means 10% off
    private final String startDate;        // "yyyy-MM-dd"
    private final String endDate;          // "yyyy-MM-dd"
    private final String status;           // PENDING / ACTIVE / REJECTED
    private final String submittedBy;      // park manager username/employee id

    /**
     * Full constructor — used when reading rows back from the database.
     */
    public Promotion(int id, int parkId, String parkName, String description,
                     double discountPercent, String startDate, String endDate,
                     String status, String submittedBy) {
        this.id              = id;
        this.parkId          = parkId;
        this.parkName        = parkName    != null ? parkName    : "";
        this.description     = description != null ? description : "";
        this.discountPercent = discountPercent;
        this.startDate       = startDate   != null ? startDate   : "";
        this.endDate         = endDate     != null ? endDate     : "";
        this.status          = status      != null ? status      : PENDING;
        this.submittedBy     = submittedBy != null ? submittedBy : "";
    }

    /**
     * Submission constructor — used by the park manager to package a proposed promotion
     * before sending to the server. {@code id} is 0 and {@code status} is PENDING;
     * the server assigns the real id and stores the PENDING status on insert.
     */
    public Promotion(int parkId, String parkName, String description,
                     double discountPercent, String startDate, String endDate,
                     String submittedBy) {
        this(0, parkId, parkName, description, discountPercent,
             startDate, endDate, PENDING, submittedBy);
    }

    /** @return the promotion's primary-key ID, or 0 when this object is a fresh submission */
    public int    getId()              { return id; }

    /** @return the ID of the park the promotion applies to */
    public int    getParkId()          { return parkId; }

    /** @return the park's display name (not stored on the row; for display only) */
    public String getParkName()        { return parkName; }

    /** @return the human-readable promotion description */
    public String getDescription()     { return description; }

    /** @return the discount percentage (e.g. {@code 10.0} for 10% off) */
    public double getDiscountPercent() { return discountPercent; }

    /** @return the first day the promotion is valid, {@code yyyy-MM-dd} */
    public String getStartDate()       { return startDate; }

    /** @return the last day the promotion is valid, {@code yyyy-MM-dd} */
    public String getEndDate()         { return endDate; }

    /** @return the status: {@link #PENDING}, {@link #ACTIVE}, or {@link #REJECTED} */
    public String getStatus()          { return status; }

    /** @return the username of the park manager who submitted the promotion */
    public String getSubmittedBy()     { return submittedBy; }

    /** @return formatted "X% off" label for display */
    public String getDiscountLabel()   { return String.format("%.0f%% off", discountPercent); }

    /** @return formatted "start → end" date-range label for display */
    public String getDateRange()       { return startDate + "  →  " + endDate; }
}
