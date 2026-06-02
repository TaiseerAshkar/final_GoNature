package common;

/**
 * display contract for any component that renders messages to the user.
 *
 * <p>implemented by {@link client.ClientController} so the network layer can surface
 * diagnostic messages without coupling to a specific UI widget.
 */
public interface ChatIF {

    /**
     * @param message the text to display; never {@code null}
     */
    void display(String message);
}
