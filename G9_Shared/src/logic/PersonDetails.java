package logic;

import java.io.Serializable;

/**
 * editable personal-details projection for a registered person (visitor, subscriber, or guide),
 * used by the "My Details" view (subscriber sees and edits their own) and the Service Rep's
 * view/edit-any-user panel.
 *
 * <p>the {@code idNumber} is the immutable key. {@code creditCard} and {@code familySize} are
 * only meaningful for subscribers (they are blank/0 for plain visitors and guides). {@code role}
 * is one of {@code "VISITOR"}, {@code "SUBSCRIBER"}, {@code "GUIDE"}.
 */
public class PersonDetails implements Serializable {

    private static final long serialVersionUID = 1L;

    private final String  idNumber;
    private final String  firstName;
    private final String  lastName;
    private final String  email;
    private final String  phone;
    private final String  creditCard;   // subscribers only; "" when not applicable
    private final int     familySize;   // subscribers only; 0 when not applicable
    private final String  role;         // VISITOR / SUBSCRIBER / GUIDE
    private final boolean isSubscriber;
    private final int     subscriberNumber;   // unique member number; 0 when not a subscriber

    /**
     * @param idNumber     the person's government ID (immutable key)
     * @param firstName    first name
     * @param lastName     last name
     * @param email        contact email
     * @param phone        contact phone
     * @param creditCard   subscriber credit card, or {@code null}/empty if none
     * @param familySize   subscriber family size, or {@code 0} if not a subscriber
     * @param role             {@code "VISITOR"}, {@code "SUBSCRIBER"}, or {@code "GUIDE"}
     * @param isSubscriber     convenience flag mirroring {@code role.equals("SUBSCRIBER")}
     * @param subscriberNumber the unique member number, or {@code 0} if not a subscriber
     */
    public PersonDetails(String idNumber, String firstName, String lastName,
                         String email, String phone, String creditCard,
                         int familySize, String role, boolean isSubscriber,
                         int subscriberNumber) {
        this.idNumber         = idNumber;
        this.firstName        = firstName != null ? firstName : "";
        this.lastName         = lastName  != null ? lastName  : "";
        this.email            = email      != null ? email      : "";
        this.phone            = phone      != null ? phone      : "";
        this.creditCard       = creditCard != null ? creditCard : "";
        this.familySize       = familySize;
        this.role             = role != null ? role : "VISITOR";
        this.isSubscriber     = isSubscriber;
        this.subscriberNumber = subscriberNumber;
    }

    /** @return the person's government ID number (the immutable key) */
    public String  getIdNumber()     { return idNumber; }

    /** @return the first name */
    public String  getFirstName()    { return firstName; }

    /** @return the last name */
    public String  getLastName()     { return lastName; }

    /** @return the contact email */
    public String  getEmail()        { return email; }

    /** @return the contact phone */
    public String  getPhone()        { return phone; }

    /** @return the subscriber credit card, or an empty string if not applicable */
    public String  getCreditCard()   { return creditCard; }

    /** @return the subscriber family size, or {@code 0} if not a subscriber */
    public int     getFamilySize()   { return familySize; }

    /** @return the role: {@code "VISITOR"}, {@code "SUBSCRIBER"}, or {@code "GUIDE"} */
    public String  getRole()         { return role; }

    /** @return {@code true} if this person is a Family Member Club subscriber */
    public boolean isSubscriber()    { return isSubscriber; }

    /** @return the unique subscriber/member number, or {@code 0} if not a subscriber */
    public int     getSubscriberNumber() { return subscriberNumber; }
}
