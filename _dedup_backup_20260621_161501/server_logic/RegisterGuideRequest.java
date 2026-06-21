package logic;

import java.io.Serializable;

/**
 * payload sent by the service representative to register a new guide.
 * the server inserts the record with {@code is_approved = 0} (pending).
 */
public class RegisterGuideRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    private final String idNumber;
    private final String firstName;
    private final String lastName;
    private final String email;
    private final String phone;

    /**
     * @param idNumber  the new guide's government-issued ID number
     * @param firstName the guide's first name
     * @param lastName  the guide's last name
     * @param email     the guide's contact email, or {@code null} (trimmed, or stored as empty)
     * @param phone     the guide's contact phone, or {@code null} (trimmed, or stored as empty)
     */
    public RegisterGuideRequest(String idNumber, String firstName, String lastName,
                                String email, String phone) {
        this.idNumber  = idNumber;
        this.firstName = firstName;
        this.lastName  = lastName;
        this.email     = email != null ? email.trim() : "";
        this.phone     = phone != null ? phone.trim() : "";
    }

    /** @return the new guide's government-issued ID number */
    public String getIdNumber()  { return idNumber; }

    /** @return the guide's first name */
    public String getFirstName() { return firstName; }

    /** @return the guide's last name */
    public String getLastName()  { return lastName; }

    /** @return the guide's contact email, or an empty string if none was provided */
    public String getEmail()     { return email; }

    /** @return the guide's contact phone, or an empty string if none was provided */
    public String getPhone()     { return phone; }
}
