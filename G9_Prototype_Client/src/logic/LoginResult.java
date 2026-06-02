package logic;

import java.io.Serializable;

/**
 * immutable value object returned by the server after a successful login.
 *
 * <p>carries the user's role, name, email, ID, and optional park assignment.
 * sent as the payload of {@code LOGIN_SUCCESS} and stored in {@link UserSession}.
 */
public class LoginResult implements Serializable {

    private static final long serialVersionUID = 1L;

    private final String  role;
    private final String  firstName;
    private final String  lastName;
    private final String  email;
    /** visitor {@code id_number} for visitors/guides; {@code username} for staff. */
    private final String  userId;
    /** non-null only for park employees and managers. */
    private final Integer parkId;

    /**
     * @param role      the application role constant (e.g., {@code "VISITOR"}, {@code "PARK_EMPLOYEE"})
     * @param firstName the user's first name
     * @param lastName  the user's last name
     * @param email     the user's contact email address
     * @param userId    visitor {@code id_number} or staff {@code username}
     * @param parkId    the assigned park ID for employees/managers, or {@code null}
     */
    public LoginResult(String role, String firstName, String lastName,
                       String email, String userId, Integer parkId) {
        this.role      = role;
        this.firstName = firstName;
        this.lastName  = lastName;
        this.email     = email;
        this.userId    = userId;
        this.parkId    = parkId;
    }

    /** @return the application role constant; never {@code null} */
    public String  getRole()      { return role; }

    /** @return the user's first name */
    public String  getFirstName() { return firstName; }

    /** @return the user's last name */
    public String  getLastName()  { return lastName; }

    /** @return the user's email address */
    public String  getEmail()     { return email; }

    /** @return the visitor ID number or staff username */
    public String  getUserId()    { return userId; }

    /** @return the assigned park ID, or {@code null} if not park-specific */
    public Integer getParkId()    { return parkId; }
}
