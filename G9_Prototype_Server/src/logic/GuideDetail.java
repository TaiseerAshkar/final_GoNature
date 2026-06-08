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

    /**
     * @param idNumber   the guide's government-issued ID number
     * @param firstName  the guide's first name
     * @param lastName   the guide's last name
     * @param email      the guide's contact email, or {@code null} (stored as empty)
     * @param phone      the guide's contact phone, or {@code null} (stored as empty)
     * @param isApproved {@code 1} if the department manager has approved the guide,
     *                   {@code 0} while the guide is still pending approval
     */
    public GuideDetail(String idNumber, String firstName, String lastName,
                       String email, String phone, int isApproved) {
        this.idNumber   = idNumber;
        this.firstName  = firstName;
        this.lastName   = lastName;
        this.email      = email  != null ? email  : "";
        this.phone      = phone  != null ? phone  : "";
        this.isApproved = isApproved;
    }

    /** @return the guide's government-issued ID number */
    public String getIdNumber()    { return idNumber; }

    /** @return the guide's first name */
    public String getFirstName()   { return firstName; }

    /** @return the guide's last name */
    public String getLastName()    { return lastName; }

    /** @return the guide's full name, formatted as "First Last" */
    public String getFullName()    { return firstName + " " + lastName; }

    /** @return the guide's contact email, or an empty string if none was provided */
    public String getEmail()       { return email; }

    /** @return the guide's contact phone, or an empty string if none was provided */
    public String getPhone()       { return phone; }

    /** @return {@code 1} if the guide is approved, {@code 0} if still pending */
    public int    getIsApproved()  { return isApproved; }

    /** @return a human-friendly status label: {@code "APPROVED"} or {@code "PENDING"} */
    public String getStatusLabel() { return isApproved == 1 ? "APPROVED" : "PENDING"; }
}
