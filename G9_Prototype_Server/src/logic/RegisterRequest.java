package logic;

import java.io.Serializable;

/**
 * registration request sent when a new visitor creates an account.
 *
 * <p>carries personal-info plus optional subscriber details. the server inserts into
 * {@code visitors} (and optionally {@code subscribers}) and returns a {@link RegisterResult}.
 */
public class RegisterRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    private final String  idNumber;
    private final String  firstName;
    private final String  lastName;
    private final String  email;
    private final String  phone;
    private final boolean subscriber;
    /** {@code null} when not a subscriber or no credit card was given. */
    private final String  creditCard;
    /** {@code 0} when {@link #subscriber} is {@code false}. */
    private final int     familySize;

    /**
     * @param idNumber   the visitor's national ID number (5-15 digits)
     * @param firstName  the visitor's first name
     * @param lastName   the visitor's last name
     * @param email      the visitor's email address
     * @param phone      the visitor's phone number (9-15 digits)
     * @param subscriber {@code true} if the visitor also wants to join the Family Member Club
     * @param creditCard the credit card number (16 digits), or {@code null} if not provided
     * @param familySize the number of family members (1-10); {@code 0} if not a subscriber
     */
    public RegisterRequest(String idNumber, String firstName, String lastName,
                           String email, String phone,
                           boolean subscriber, String creditCard, int familySize) {
        this.idNumber   = idNumber;
        this.firstName  = firstName;
        this.lastName   = lastName;
        this.email      = email;
        this.phone      = phone;
        this.subscriber = subscriber;
        this.creditCard = creditCard;
        this.familySize = familySize;
    }

    /** @return the visitor's national ID number */
    public String  getIdNumber()   { return idNumber; }

    /** @return the visitor's first name */
    public String  getFirstName()  { return firstName; }

    /** @return the visitor's last name */
    public String  getLastName()   { return lastName; }

    /** @return the visitor's email address */
    public String  getEmail()      { return email; }

    /** @return the visitor's phone number */
    public String  getPhone()      { return phone; }

    /** @return {@code true} if the visitor requested subscriber (Family Member Club) registration */
    public boolean isSubscriber()  { return subscriber; }

    /** @return the credit card number, or {@code null} if not provided */
    public String  getCreditCard() { return creditCard; }

    /** @return the family size, or {@code 0} if not a subscriber */
    public int     getFamilySize() { return familySize; }
}
