package logic;

import java.io.Serializable;

/**
 * immutable snapshot of a park's configuration as stored in the database.
 *
 * <p>sent by the server in response to {@code GET_PARKS} and used across the client
 * for park selection, capacity checking, and price calculation.
 * an instance with {@code id = 0} / {@code name = "All Parks"} is used as a sentinel
 * in report filter combo-boxes.
 */
public class Park implements Serializable {

    private static final long serialVersionUID = 1L;

    private final int    id;
    private final String name;
    private final int    capacity;
    private final int    maxOrders;
    private final int    visitDurationHours;
    private final double fullPrice;

    /**
     * @param id                  the unique database park ID
     * @param name                the human-readable park name
     * @param capacity            the maximum simultaneous visitors allowed
     * @param maxOrders           the maximum concurrent orders (booking cap)
     * @param visitDurationHours  the default visit window in hours (used for overlap detection)
     * @param fullPrice           the full per-person admission price before discounts
     */
    public Park(int id, String name, int capacity, int maxOrders,
                int visitDurationHours, double fullPrice) {
        this.id                = id;
        this.name              = name;
        this.capacity          = capacity;
        this.maxOrders         = maxOrders;
        this.visitDurationHours = visitDurationHours;
        this.fullPrice         = fullPrice;
    }

    /** @return the unique database park ID */
    public int    getId()                  { return id; }

    /** @return the human-readable park name */
    public String getName()                { return name; }

    /** @return the maximum simultaneous visitors */
    public int    getCapacity()            { return capacity; }

    /** @return the maximum concurrent orders */
    public int    getMaxOrders()           { return maxOrders; }

    /** @return the visit window duration in hours */
    public int    getVisitDurationHours()  { return visitDurationHours; }

    /** @return the full per-person admission price before any discount */
    public double getFullPrice()           { return fullPrice; }

    /** so ComboBox renders the park name without a custom StringConverter. */
    @Override
    public String toString() { return name; }
}
