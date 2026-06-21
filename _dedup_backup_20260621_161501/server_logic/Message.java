package logic;

import java.io.Serializable;

/**
 * protocol envelope for all client-server communication.
 *
 * <p>every object exchanged over the OCSF socket is a {@code Message}.
 * the {@code type} string acts as a command discriminator; {@code data} carries
 * the typed payload for that command.
 */
public class Message implements Serializable {

    private static final long serialVersionUID = 1L;

    private String type;
    private Object data;

    /**
     * @param type the command discriminator string; must not be {@code null}
     * @param data the payload object; may be {@code null} for commands that carry no data
     */
    public Message(String type, Object data) {
        this.type = type;
        this.data = data;
    }

    /** @return the command type string; never {@code null} */
    public String getType() { return type; }

    /**
     * the caller must cast to the expected type for the given command.
     *
     * @return the payload object, or {@code null} if this command carries no data
     */
    public Object getData() { return data; }

    // note: the client copy adds a toString() override for UI-side debug logging.
    // omitted here because EchoServer never concatenates Message objects into log strings
    // directly; it logs type/data separately after unpacking.
}
