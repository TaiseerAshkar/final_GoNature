package logic;

/**
 * holds the currently logged-in user for the lifetime of a client session.
 *
 * <p>call {@link #set(LoginResult)} right after a successful login, and {@link #clear()} on
 * logout so the next user starts fresh. all GUI controllers read user data from here
 * rather than threading it through constructor arguments.
 */
public class UserSession {

    private static UserSession instance;

    private String  role;
    private String  firstName;
    private String  lastName;
    private String  email;
    private String  userId;
    private Integer parkId;

    /** use {@link #getInstance()} or {@link #set(LoginResult)}, not new. */
    private UserSession() {}

    /**
     * returns the current session, creating an empty one if none exists yet.
     *
     * @return the singleton {@code UserSession}; never {@code null}
     */
    public static UserSession getInstance() {
        if (instance == null) instance = new UserSession();
        return instance;
    }

    /**
     * populates (or replaces) the session from a successful login response.
     *
     * @param r the {@link LoginResult} returned by the server; must not be {@code null}
     */
    public static void set(LoginResult r) {
        UserSession s = getInstance();
        s.role      = r.getRole();
        s.firstName = r.getFirstName();
        s.lastName  = r.getLastName();
        s.email     = r.getEmail();
        s.userId    = r.getUserId();
        s.parkId    = r.getParkId();
    }

    /** nulls out the instance so the next login gets a clean slate. */
    public static void clear() { instance = null; }

    /** @return the application role constant (e.g., {@code "VISITOR"}, {@code "PARK_EMPLOYEE"}) */
    public String  getRole()      { return role; }

    /** @return the user's first name */
    public String  getFirstName() { return firstName; }

    /** @return the user's last name */
    public String  getLastName()  { return lastName; }

    /** @return the user's contact email address */
    public String  getEmail()     { return email; }

    /** @return the visitor ID number or staff username */
    public String  getUserId()    { return userId; }

    /** @return the assigned park ID for employees/managers, or {@code null} */
    public Integer getParkId()    { return parkId; }

    /** @return the user's full name in "First Last" format */
    public String  getFullName()  { return firstName + " " + lastName; }
}
