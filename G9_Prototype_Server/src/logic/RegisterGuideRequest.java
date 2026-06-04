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

    public RegisterGuideRequest(String idNumber, String firstName, String lastName,
                                String email, String phone) {
        this.idNumber  = idNumber;
        this.firstName = firstName;
        this.lastName  = lastName;
        this.email     = email != null ? email.trim() : "";
        this.phone     = phone != null ? phone.trim() : "";
    }

    public String getIdNumber()  { return idNumber; }
    public String getFirstName() { return firstName; }
    public String getLastName()  { return lastName; }
    public String getEmail()     { return email; }
    public String getPhone()     { return phone; }
}
