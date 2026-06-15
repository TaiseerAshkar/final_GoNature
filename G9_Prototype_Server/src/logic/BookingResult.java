package logic;

import java.io.Serializable;

/**
 * immutable confirmation returned by the server after a booking or waiting-list request.
 *
 * <p>sent as the payload of {@code BOOKING_CONFIRMED} or {@code WAITING_LIST_CONFIRMED}.
 */
public class BookingResult implements Serializable {

    private static final long serialVersionUID = 1L;

    private final int    id;
    private final String status;      // "CONFIRMED" or "WAITING"
    private final String parkName;
    private final String visitDate;   // "yyyy-MM-dd"
    private final String visitTime;   // "HH:mm"
    private final int    numVisitors;
    private final String orderType;
    private final String email;

    // ── Pricing / promotion preview (CONFIRMED bookings only; 0 / empty otherwise) ──
    private final double basePrice;        // indicative price before any active promotion
    private final double finalPrice;       // price after the best active promotion
    private final double promoPercent;     // active promotion discount %, 0 when none
    private final String promoDescription; // active promotion text, empty when none

    /**
     * Basic constructor — no pricing preview (used for waiting-list results and any
     * caller that doesn't compute a price). Pricing fields default to 0 / empty.
     *
     * @param id          the generated order ID (or waiting-list ID when status is {@code "WAITING"})
     * @param status      {@code "CONFIRMED"} for a confirmed booking; {@code "WAITING"} for waiting list
     * @param parkName    the park name for display
     * @param visitDate   the visit date in {@code yyyy-MM-dd} format
     * @param visitTime   the visit time in {@code HH:mm} format
     * @param numVisitors the number of visitors
     * @param orderType   the order type ({@code "INDIVIDUAL"}, {@code "FAMILY"}, or {@code "GROUP"})
     * @param email       the contact email used for this booking
     */
    public BookingResult(int id, String status, String parkName,
                         String visitDate, String visitTime,
                         int numVisitors, String orderType, String email) {
        this(id, status, parkName, visitDate, visitTime, numVisitors, orderType, email,
             0.0, 0.0, 0.0, "");
    }

    /**
     * Full constructor — includes the pricing/promotion preview shown on the
     * booking-confirmation screen.
     *
     * @param basePrice        indicative price before any active promotion
     * @param finalPrice       price after the best active promotion
     * @param promoPercent     active promotion discount percentage ({@code 0} when none)
     * @param promoDescription active promotion description (empty when none)
     */
    public BookingResult(int id, String status, String parkName,
                         String visitDate, String visitTime,
                         int numVisitors, String orderType, String email,
                         double basePrice, double finalPrice,
                         double promoPercent, String promoDescription) {
        this.id               = id;
        this.status           = status;
        this.parkName         = parkName;
        this.visitDate        = visitDate;
        this.visitTime        = visitTime;
        this.numVisitors      = numVisitors;
        this.orderType        = orderType;
        this.email            = email;
        this.basePrice        = basePrice;
        this.finalPrice       = finalPrice;
        this.promoPercent     = promoPercent;
        this.promoDescription = promoDescription != null ? promoDescription : "";
    }

    /** @return the generated order or waiting-list ID */
    public int    getId()          { return id; }

    /** @return {@code "CONFIRMED"} or {@code "WAITING"} */
    public String getStatus()      { return status; }

    /** @return the park name */
    public String getParkName()    { return parkName; }

    /** @return the visit date in {@code yyyy-MM-dd} format */
    public String getVisitDate()   { return visitDate; }

    /** @return the visit time in {@code HH:mm} format */
    public String getVisitTime()   { return visitTime; }

    /** @return the number of visitors */
    public int    getNumVisitors() { return numVisitors; }

    /** @return the order type */
    public String getOrderType()   { return orderType; }

    /** @return the contact email for this booking */
    public String getEmail()       { return email; }

    /** @return indicative price before any active promotion */
    public double getBasePrice()         { return basePrice; }

    /** @return price after the best active promotion (equals base price when none) */
    public double getFinalPrice()        { return finalPrice; }

    /** @return the active promotion discount percentage, or {@code 0} when none applies */
    public double getPromoPercent()      { return promoPercent; }

    /** @return the active promotion description, or an empty string when none applies */
    public String getPromoDescription()  { return promoDescription; }

    /** @return {@code true} when an active promotion was applied to this booking's price */
    public boolean hasPromotion()        { return promoPercent > 0.0; }
}
