package logic;

import java.io.Serializable;

/**
 * read-only projection of a row in the {@code guides} table.
 * transferred between server and client for the Manage Guides panel
 * and the guide-registration confirmation.
 */
public class GuideDetail implements Serializable {

    private static final long serialVersionUID = 1L;

    private final String idNumber;
    private final String firstName;
    private final String lastName;
    private final String email;
    private final String phone;
    private final int    isApproved;   // 0 = pending, 1 = approved

    public GuideDetail(String idNumber, String firstName, String lastName,
                       String email, String phone, int isApproved) {
        this.idNumber   = idNumber;
        this.firstName  = firstName;
        this.lastName   = lastName;
        this.email      = email  != null ? email  : "";
        this.phone      = phone  != null ? phone  : "";
        this.isApproved = isApproved;
    }

    public String getIdNumber()    { return idNumber; }
    public String getFirstName()   { return firstName; }
    public String getLastName()    { return lastName; }
    public String getFullName()    { return firstName + " " + lastName; }
    public String getEmail()       { return email; }
    public String getPhone()       { return phone; }
    public int    getIsApproved()  { return isApproved; }

    /** @return {@code "APPROVED"} or {@code "PENDING"} */
    public String getStatusLabel() { return isApproved == 1 ? "APPROVED" : "PENDING"; }
}
