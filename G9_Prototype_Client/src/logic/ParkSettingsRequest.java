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
     * Submission constructor — used by {@link gui.ParkSettingsController} to package
     * the manager's proposed values before sending to the server. {@code id} is 0
     * and {@code status} / {@code requestedAt} are empty; the server fills those in.
     */
    public ParkSettingsRequest(int parkId, String parkName, String requestedBy,
                               int capacity, int maxOrders, int visitDurationHours,
                               double fullPrice) {
        this(0, parkId, parkName, requestedBy,
             capacity, maxOrders, visitDurationHours, fullPrice, "pending", "");
    }

    public int    getId()                 { return id; }
    public int    getParkId()             { return parkId; }
    public String getParkName()           { return parkName; }
    public String getRequestedBy()        { return requestedBy; }
    public int    getCapacity()           { return capacity; }
    public int    getMaxOrders()          { return maxOrders; }
    public int    getVisitDurationHours() { return visitDurationHours; }
    public double getFullPrice()          { return fullPrice; }
    public String getStatus()             { return status; }
    public String getRequestedAt()        { return requestedAt; }

    /** Formatted summary of the proposed values — shown in the approvals table. */
    public String getSummary() {
        return String.format("Cap: %d  |  Max orders: %d  |  Duration: %d h  |  Price: $%.2f",
            capacity, maxOrders, visitDurationHours, fullPrice);
    }
}
