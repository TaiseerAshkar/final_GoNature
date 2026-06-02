package common;

/**
 * display contract for any component that renders messages to the operator.
 *
 * <p>implemented by server-side console adapters so the network layer can surface
 * diagnostic messages without coupling to a specific output sink.
 */
public interface ChatIF {

    /**
     * @param message the text to display; never {@code null}
     */
    void display(String message);
}
