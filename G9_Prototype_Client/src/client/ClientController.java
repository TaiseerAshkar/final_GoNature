package client;

import client.ChatIF;
import logic.Message;
import java.io.IOException;

/**
 * façade between the JavaFX GUI and the OCSF {@link ChatClient}.
 *
 * <p>implements {@link ChatIF} so the network layer can log diagnostics without
 * depending on any specific UI widget. holds the single {@link ChatClient}
 * instance for the application's lifetime.
 */
public class ClientController implements ChatIF {

    /** The default port used when no port is specified. */
    public static int DEFAULT_PORT = 5555;

    private final ChatClient client;

    /**
     * opens a connection to the server immediately; calls {@link System#exit(int)} if it fails.
     *
     * @param host the server hostname or IP address
     * @param port the TCP port on which the server is listening
     */
    public ClientController(String host, int port) {
        try {
            client = new ChatClient(host, port, this);
            client.openConnection(); // connect immediately so server logs the client right away
        } catch (IOException e) {
            System.out.println("Error: Can't setup connection! " + e);
            System.exit(1);
            throw new RuntimeException(e); // unreachable; satisfies the compiler
        }
    }

    /** closes the socket; safe to call multiple times since errors are swallowed. */
    public void disconnect() {
        try { client.closeConnection(); } catch (IOException e) { e.printStackTrace(); }
    }

    /**
     * sends a message and blocks until the server reply is processed.
     *
     * @param msg the message to send; must not be {@code null}
     */
    public void accept(Message msg) {
        client.handleMessageFromClientUI(msg);
    }

    /** fire-and-forget logout; delegates to {@link ChatClient#sendLogout()}. */
    public void sendLogout() {
        client.sendLogout();
    }

    /**
     * required by {@link ChatIF}; routes diagnostic messages to stdout.
     *
     * @param message the text to display
     */
    @Override
    public void display(String message) {
        System.out.println("> " + message);
    }
}
