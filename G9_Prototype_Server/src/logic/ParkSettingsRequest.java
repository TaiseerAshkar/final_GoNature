package logic;

import java.io.Serializable;

/**
 * represents one row in the {@code park_settings_requests} table.
 * used both as the payload for {@code SUBMIT_PARK_SETTINGS} (from park manager
 * to server) and as a read-only row in the department manager's Pending Approvals panel.
 */
public class ParkSettingsRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    private final int    id;               // 0 when used as a submission payload
    private final int    parkId;
    private final String parkName;         // display only — not stored in the table
    private final String requestedBy;      // park manager username
    private final int    capacity;
    private final int    maxOrders;
    private final int    visitDurationHours;
    private final double fullPrice;
    private final String status;           // "pending" / "approved" / "rejected"
    private final String requestedAt;      // formatted timestamp, empty for submissions

    /**
     * Full constructor — used when reading rows back from the database.
     */
    public ParkSettingsRequest(int id, int parkId, String parkName, String requestedBy,
                               int capacity, int maxOrders, int visitDurationHours,
                               double fullPrice, String status, String requestedAt) {
        this.id                  = id;
        this.parkId              = parkId;
        this.parkName            = parkName            != null ? parkName            : "";
        this.requestedBy         = requestedBy         != null ? requestedBy         : "";
        this.capacity            = capacity;
        this.maxOrders           = maxOrders;
        this.visitDurationHours  = visitDurationHours;
        this.fullPrice           = fullPrice;
        this.status              = status              != null ? status              : "pending";
        this.requestedAt         = requestedAt         != null ? requestedAt         : "";
    }

    /**
     * Submission constructor — used by the park manager to package proposed values
     * before sending to the server. {@code id} is 0 and {@code status} /
     * {@code requestedAt} are empty; the server fills those in on insert.
     */
    public ParkSettingsRequest(int parkId, String parkName, String requestedBy,
                               int capacity, int maxOrders, int visitDurationHours,
                               double fullPrice) {
        this(0, parkId, parkName, requestedBy,
             capacity, maxOrders, visitDurationHours, fullPrice, "pending", "");
    }

    /** @return the request's primary-key ID, or 0 when this object is a fresh submission */
    public int    getId()                 { return id; }

    /** @return the ID of the park the change applies to */
    public int    getParkId()             { return parkId; }

    /** @return the park's display name (not stored in the table; for display only) */
    public String getParkName()           { return parkName; }

    /** @return the username of the park manager who submitted the request */
    public String getRequestedBy()        { return requestedBy; }

    /** @return the proposed maximum simultaneous-visitor capacity */
    public int    getCapacity()           { return capacity; }

    /** @return the proposed maximum number of bookable orders per time slot */
    public int    getMaxOrders()          { return maxOrders; }

    /** @return the proposed visit-window length, in hours */
    public int    getVisitDurationHours() { return visitDurationHours; }

    /** @return the proposed full per-person admission price */
    public double getFullPrice()          { return fullPrice; }

    /** @return the request status: {@code "pending"}, {@code "approved"}, or {@code "rejected"} */
    public String getStatus()             { return status; }

    /** @return the formatted submission timestamp, or an empty string for a fresh submission */
    public String getRequestedAt()        { return requestedAt; }

    /** Formatted summary of the proposed values — shown in the approvals table. */
    public String getSummary() {
        return String.format("Cap: %d  |  Max orders: %d  |  Duration: %d h  |  Price: $%.2f",
            capacity, maxOrders, visitDurationHours, fullPrice);
    }
}
