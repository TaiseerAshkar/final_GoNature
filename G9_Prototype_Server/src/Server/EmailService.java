package Server;

import java.io.InputStream;
import java.util.Properties;

import javax.mail.Authenticator;
import javax.mail.Message;
import javax.mail.PasswordAuthentication;
import javax.mail.Session;
import javax.mail.Transport;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMessage;

/**
 * sends real notification emails through Gmail's SMTP server (STARTTLS on port 587).
 *
 * <p>credentials are read once from the {@code /db.properties} classpath resource
 * ({@code mail.sender}, {@code mail.password}, {@code mail.host}, {@code mail.port}).
 * {@code mail.password} must be a Gmail App Password, not the normal account password.
 *
 * <p>{@link #sendEmail(String, String, String)} dispatches each message on a short-lived
 * daemon thread so the SMTP round-trip never blocks the OCSF request handlers. failures are
 * logged to stdout and swallowed — a notification email never breaks the booking flow.
 */
public class EmailService {

    private static String  sender;
    private static String  password;
    private static String  host;
    private static String  port;
    private static boolean configLoaded = false;

    /** bounded pool so burst bookings never exhaust JVM threads. */
    private static final java.util.concurrent.ExecutorService emailExecutor =
        java.util.concurrent.Executors.newFixedThreadPool(4, r -> {
            Thread t = new Thread(r, "email-sender");
            t.setDaemon(true);
            return t;
        });

    /** utility class — no instances. */
    private EmailService() {}

    /**
     * loads the mail.* settings from {@code /db.properties} exactly once.
     * missing host/port fall back to Gmail's defaults.
     */
    private static synchronized void loadConfig() {
        if (configLoaded) return;
        try (InputStream is = EmailService.class.getResourceAsStream("/db.properties")) {
            if (is != null) {
                Properties props = new Properties();
                props.load(is);
                sender   = props.getProperty("mail.sender");
                password = props.getProperty("mail.password");
                host     = props.getProperty("mail.host", "smtp.gmail.com");
                port     = props.getProperty("mail.port", "587");
            } else {
                System.out.println("EmailService: db.properties not found — email disabled.");
            }
        } catch (Exception e) {
            System.out.println("EmailService: failed to load mail config — " + e.getMessage());
        }
        configLoaded = true;
    }

    /**
     * sends a plain-text email via Gmail SMTP with STARTTLS. the actual send happens on a
     * daemon thread, so this method returns immediately and never blocks the caller.
     * if configuration or the recipient is missing the call is skipped silently (logged to stdout).
     *
     * @param toEmail the recipient address; ignored if {@code null} or blank
     * @param subject the email subject line
     * @param body    the plain-text message body
     */
    public static void sendEmail(String toEmail, String subject, String body) {
        loadConfig();
        if (sender == null || sender.isEmpty() || password == null || password.isEmpty()) {
            System.out.println("EmailService: skipped — sender/password not configured.");
            return;
        }
        if (toEmail == null || toEmail.trim().isEmpty()) {
            System.out.println("EmailService: skipped — no recipient address.");
            return;
        }

        final String to = toEmail.trim();
        System.out.println("EmailService: ► queuing email  to=" + to
            + "  subject=\"" + subject + "\"  host=" + host + ":" + port
            + "  sender=" + sender);
        emailExecutor.execute(() -> {
            try {
                System.out.println("EmailService: connecting to " + host + ":" + port + " …");
                Properties p = new Properties();
                p.put("mail.smtp.auth", "true");
                // Use SSL on port 465 (SMTPS) instead of STARTTLS on 587.
                // Port 587 STARTTLS is often blocked by firewalls; 465 SMTPS wraps
                // the entire connection in SSL from the start and is more reliably open.
                p.put("mail.smtp.ssl.enable", "true");
                p.put("mail.smtp.starttls.enable", "false");
                // JavaMail 1.4.7 defaults to TLSv1/1.1, which Java 11+ disables; force a modern
                // protocol so the SSL handshake succeeds on Java 21.
                p.put("mail.smtp.ssl.protocols", "TLSv1.2 TLSv1.3");
                p.put("mail.smtp.host", host);
                p.put("mail.smtp.port", port);
                // 10-second timeouts so a network issue doesn't hang the daemon thread forever
                p.put("mail.smtp.connectiontimeout", "10000");
                p.put("mail.smtp.timeout", "10000");
                p.put("mail.smtp.writetimeout", "10000");

                Session session = Session.getInstance(p, new Authenticator() {
                    @Override
                    protected PasswordAuthentication getPasswordAuthentication() {
                        return new PasswordAuthentication(sender, password);
                    }
                });

                MimeMessage msg = new MimeMessage(session);
                msg.setFrom(new InternetAddress(sender, "GoNature Parks"));
                msg.setRecipients(Message.RecipientType.TO, InternetAddress.parse(to));
                msg.setSubject(subject);
                msg.setText(body);
                Transport.send(msg);

                System.out.println("EmailService: ✓ sent to " + to + " — \"" + subject + "\"");
            } catch (Exception e) {
                System.out.println("EmailService: ✗ send FAILED to " + to
                    + " — " + e.getClass().getSimpleName() + ": " + e.getMessage());
            }
        });
    }
}
