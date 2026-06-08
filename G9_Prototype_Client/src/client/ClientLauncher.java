package client;

/**
 * Plain (non-JavaFX) entry point used as the {@code Main-Class} of the runnable
 * client JAR ({@code G9_client.jar}).
 *
 * <p>Why this class exists: when a JAR's {@code Main-Class} is a subclass of
 * {@code javafx.application.Application}, the Java launcher refuses to start with
 * <em>"Error: JavaFX runtime components are missing"</em> unless JavaFX is supplied
 * on the module path. Because {@link ClientUI} extends {@code Application}, we point
 * the JAR manifest at this launcher instead. It is an ordinary class, so the guard is
 * skipped, and it simply hands control to {@link ClientUI#main(String[])}, which starts
 * JavaFX the normal way. This lets the bundled JAR run with a plain {@code java -jar}.
 *
 * @see ClientUI
 */
public class ClientLauncher {

    /**
     * Delegates straight to the JavaFX application entry point.
     *
     * @param args command-line arguments (passed through unchanged; not used by the app)
     * @throws Exception if the JavaFX runtime fails to initialise
     */
    public static void main(String[] args) throws Exception {
        ClientUI.main(args);
    }
}
