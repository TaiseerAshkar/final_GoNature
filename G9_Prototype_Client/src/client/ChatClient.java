package client;

import client.ChatIF;
import logic.BookingResult;
import logic.CancelResult;
import logic.EntryResult;
import logic.GuideDetail;
import logic.ParkSettingsRequest;
import logic.Promotion;
import logic.LoginResult;
import logic.Message;
import logic.OrderDetail;
import logic.Park;
import logic.RegisterResult;
import logic.ReportCancelRow;
import logic.ReportCancelDistribution;
import logic.ReportDurationRow;
import logic.ReportUsageRow;
import logic.ReportVisitorRow;
import ocsf.client.AbstractClient;
import java.io.IOException;
import java.util.List;

/**
 * OCSF client that handles all communication with the GoNature server.
 *
 * <p>routes each incoming {@link Message} to the appropriate static field so that
 * the GUI thread (blocked in {@link #handleMessageFromClientUI(Message)}) can read
 * the result after {@code awaitResponse} is cleared.
 *
 * <p>static fields act as a shared-memory rendezvous between the OCSF reader thread
 * (which writes) and the JavaFX GUI thread (which reads after the blocking call returns).
 */
public class ChatClient extends AbstractClient {

    private final ChatIF clientUI;

    /**
     * set to {@code true} before sending; cleared by the response handler to unblock the GUI.
     * declared {@code volatile} so the OCSF reader thread's write is immediately visible to the
     * JavaFX/background thread that is polling it in {@link #handleMessageFromClientUI}.
     */
    public static volatile boolean awaitResponse = false;

    // ── Order update result ───────────────────────────────────────────────────
    public static String lastResult = "";

    // ── Login ─────────────────────────────────────────────────────────────────
    public static LoginResult lastLoginResult = null;
    public static String      lastLoginError  = null;

    // ── Parks and bookings ────────────────────────────────────────────────────
    public static List<Park>    lastParkList          = null;
    public static BookingResult lastBookingResult     = null;
    /** {@code "FULL"} when the slot is fully booked; otherwise an error message. */
    public static String        lastBookingError      = null;
    /** alternative available slots returned after a BOOKING_FULL response; each entry is "yyyy-MM-dd HH:mm". */
    public static List<String>  lastAlternativeSlots  = null;

    // ── My orders and cancellations ───────────────────────────────────────────
    public static List<OrderDetail> lastMyOrders              = null;
    /** pending orders from waitlist promotions awaiting visitor confirmation. */
    public static List<OrderDetail> lastPendingWaitlistOrders = null;
    public static CancelResult      lastCancelResult          = null;
    public static String            lastCancelError           = null;

    // ── Entry / exit ──────────────────────────────────────────────────────────
    public static EntryResult lastEntryResult   = null;
    public static String      lastEntryError    = null;
    public static boolean     lastExitSuccess   = false;
    public static String      lastExitError     = null;
    public static OrderDetail lastFoundBooking  = null;
    public static String      lastFindBookingError = null;

    // ── Registration ──────────────────────────────────────────────────────────
    public static RegisterResult lastRegisterResult = null;
    public static String         lastRegisterError  = null;

    // ── Reports ───────────────────────────────────────────────────────────────
    public static List<ReportVisitorRow>     lastVisitorReport      = null;
    public static List<ReportCancelRow>      lastCancelReport       = null;
    public static ReportCancelDistribution   lastCancelDistribution = null;
    public static List<ReportUsageRow>       lastUsageReport        = null;
    public static List<ReportDurationRow>    lastDurationReport     = null;

    // ── Employee park screens ─────────────────────────────────────────────────
    public static List<OrderDetail> lastTodayOrders = null;
    public static List<OrderDetail> lastWaitingList  = null;
    public static List<OrderDetail> lastLiveOrders   = null;

    // ── Live capacity / occupancy lookups ─────────────────────────────────────
    /** visitors currently inside a park (checked-in, not yet exited); {@code null} until a reply arrives. */
    public static Integer lastParkOccupancy  = null;
    /** free slots for a park/date/time; {@code -1} on error, {@code null} until a reply arrives. */
    public static Integer lastAvailableSpots = null;

    // ── Visit confirmation ────────────────────────────────────────────────────
    public static Boolean lastConfirmVisitOk    = null;
    public static String  lastConfirmVisitError = null;

    // ── Park settings ─────────────────────────────────────────────────────────
    public static Park   lastParkSettings      = null;
    public static String lastParkSettingsError = null;

    // ── Guide management ──────────────────────────────────────────────────────
    public static String            lastGuideRegisterSuccess = null;
    public static String            lastGuideRegisterFail    = null;
    public static List<GuideDetail> lastGuidesList           = null;
    public static String            lastGuideActionSuccess   = null;
    public static String            lastGuideActionFail      = null;

    // ── Park settings approval workflow ───────────────────────────────────────
    public static String                       lastSettingsSubmitSuccess = null;
    public static String                       lastSettingsSubmitFail    = null;
    public static List<ParkSettingsRequest>    lastPendingSettings       = null;
    public static String                       lastSettingsActionSuccess = null;
    public static String                       lastSettingsActionFail    = null;

    // ── Promotions approval workflow ──────────────────────────────────────────
    public static String          lastPromotionSubmitSuccess = null;
    public static String          lastPromotionSubmitFail    = null;
    public static List<Promotion> lastParkPromotions         = null;
    public static List<Promotion> lastPendingPromotions      = null;
    public static String          lastPromotionActionSuccess = null;
    public static String          lastPromotionActionFail    = null;

    /**
     * @param host     the server hostname or IP address
     * @param port     the TCP port the server is listening on
     * @param clientUI the display sink for diagnostic messages
     * @throws IOException if the socket connection cannot be established
     */
    public ChatClient(String host, int port, ChatIF clientUI) throws IOException {
        super(host, port);
        this.clientUI = clientUI;
    }

    /**
     * routes an incoming server message to the correct static field and clears
     * {@link #awaitResponse} so the blocked GUI thread can proceed.
     *
     * @param msg the object received from the server; expected to be a {@link Message}
     */
    @Override
    @SuppressWarnings("unchecked")
    public void handleMessageFromServer(Object msg) {
        // awaitResponse is cleared AFTER all field assignments at the bottom of this method.
        // Clearing it here (before assignments) was a race: the waiting thread could read a
        // field before the OCSF reader thread had written it.
        if (!(msg instanceof Message)) {
            awaitResponse = false;   // non-Message still unblocks the caller
            return;
        }
        Message m = (Message) msg;

        switch (m.getType()) {
            case "UPDATE_SUCCESS":
                lastResult = "The order was updated successfully.";
                break;
            case "UPDATE_FAIL":
                lastResult = (m.getData() instanceof String && !((String) m.getData()).isEmpty())
                    ? (String) m.getData() : "Update failed.";
                break;
            case "LOGIN_SUCCESS":
                lastLoginResult = (LoginResult) m.getData();
                lastLoginError  = null;
                break;
            case "LOGIN_FAIL":
                lastLoginResult = null;
                lastLoginError  = (String) m.getData();
                break;
            case "PARKS_LIST":
                lastParkList = (List<Park>) m.getData();
                break;
            case "BOOKING_CONFIRMED":
            case "WAITING_LIST_CONFIRMED":
                lastBookingResult = (BookingResult) m.getData();
                lastBookingError  = null;
                break;
            case "BOOKING_FULL":
                lastBookingResult = null;
                lastBookingError  = "FULL";
                break;
            case "ALTERNATIVE_SLOTS":
                lastAlternativeSlots = (List<String>) m.getData();
                break;
            case "BOOKING_ERROR":
                lastBookingResult = null;
                lastBookingError  = (String) m.getData();
                break;
            case "MY_ORDERS_LIST":
                lastMyOrders = (List<OrderDetail>) m.getData();
                break;
            case "PENDING_WAITLIST_ORDERS":
                lastPendingWaitlistOrders = (List<OrderDetail>) m.getData();
                break;
            case "CANCEL_SUCCESS":
                lastCancelResult = (CancelResult) m.getData();
                lastCancelError  = null;
                break;
            case "CANCEL_FAIL":
                lastCancelResult = null;
                lastCancelError  = (String) m.getData();
                break;
            case "ENTRY_APPROVED":
                lastEntryResult = (EntryResult) m.getData();
                lastEntryError  = null;
                break;
            case "ENTRY_DENIED":
                lastEntryResult = null;
                lastEntryError  = (String) m.getData();
                break;
            case "EXIT_SUCCESS":
                lastExitSuccess = true;
                lastExitError   = null;
                break;
            case "EXIT_FAIL":
                lastExitSuccess = false;
                lastExitError   = (String) m.getData();
                break;
            case "BOOKING_FOUND":
                lastFoundBooking      = (OrderDetail) m.getData();
                lastFindBookingError  = null;
                break;
            case "BOOKING_NOT_FOUND":
                lastFoundBooking      = null;
                lastFindBookingError  = (String) m.getData();
                break;
            case "VISITOR_REPORT_DATA":
                lastVisitorReport = (List<ReportVisitorRow>) m.getData();
                break;
            case "CANCEL_REPORT_DATA":
                lastCancelReport = (List<ReportCancelRow>) m.getData();
                break;
            case "CANCEL_DISTRIBUTION_DATA":
                lastCancelDistribution = (ReportCancelDistribution) m.getData();
                break;
            case "USAGE_REPORT_DATA":
                lastUsageReport = (List<ReportUsageRow>) m.getData();
                break;
            case "DURATION_REPORT_DATA":
                lastDurationReport = (List<ReportDurationRow>) m.getData();
                break;
            case "REGISTER_SUCCESS":
                lastRegisterResult = (RegisterResult) m.getData();
                lastRegisterError  = null;
                break;
            case "REGISTER_FAIL":
                lastRegisterResult = null;
                lastRegisterError  = (String) m.getData();
                break;
            case "TODAY_ORDERS_LIST":
                lastTodayOrders = (List<OrderDetail>) m.getData();
                break;
            case "WAITING_LIST_DATA":
                lastWaitingList = (List<OrderDetail>) m.getData();
                break;
            case "LIVE_ORDERS_LIST":
                lastLiveOrders = (List<OrderDetail>) m.getData();
                lastResult     = "Loaded " + lastLiveOrders.size() + " orders.";
                break;
            case "PARK_OCCUPANCY":
                lastParkOccupancy = (m.getData() instanceof Integer) ? (Integer) m.getData() : 0;
                break;
            case "AVAILABLE_SPOTS":
                lastAvailableSpots = (m.getData() instanceof Integer) ? (Integer) m.getData() : -1;
                break;
            case "PARK_SETTINGS_DATA":
                lastParkSettings      = (Park) m.getData();
                lastParkSettingsError = null;
                break;
            case "PARK_SETTINGS_ERROR":
                lastParkSettings      = null;
                lastParkSettingsError = (String) m.getData();
                break;
            case "CONFIRM_VISIT_OK":
                lastConfirmVisitOk    = true;
                lastConfirmVisitError = null;
                break;
            case "CONFIRM_VISIT_FAIL":
                lastConfirmVisitOk    = false;
                lastConfirmVisitError = (String) m.getData();
                break;
            // ── Guide management ─────────────────────────────────────────────
            case "GUIDE_REGISTER_SUCCESS":
                lastGuideRegisterSuccess = (String) m.getData();
                lastGuideRegisterFail    = null;
                break;
            case "GUIDE_REGISTER_FAIL":
                lastGuideRegisterSuccess = null;
                lastGuideRegisterFail    = (String) m.getData();
                break;
            case "GUIDES_LIST":
                lastGuidesList = (List<GuideDetail>) m.getData();
                break;
            case "GUIDE_APPROVE_SUCCESS":
            case "GUIDE_REJECT_SUCCESS":
                lastGuideActionSuccess = (String) m.getData();
                lastGuideActionFail    = null;
                break;
            case "GUIDE_APPROVE_FAIL":
            case "GUIDE_REJECT_FAIL":
                lastGuideActionSuccess = null;
                lastGuideActionFail    = (String) m.getData();
                break;

            // ── Park settings approval workflow ──────────────────────────────
            case "PARK_SETTINGS_SUBMITTED":
                lastSettingsSubmitSuccess = (String) m.getData();
                lastSettingsSubmitFail    = null;
                break;
            case "PARK_SETTINGS_SUBMIT_FAIL":
                lastSettingsSubmitSuccess = null;
                lastSettingsSubmitFail    = (String) m.getData();
                break;
            case "PENDING_SETTINGS_LIST":
                lastPendingSettings = (List<ParkSettingsRequest>) m.getData();
                break;
            case "SETTINGS_APPROVE_SUCCESS":
            case "SETTINGS_REJECT_SUCCESS":
                lastSettingsActionSuccess = String.valueOf(m.getData());
                lastSettingsActionFail    = null;
                break;
            case "SETTINGS_APPROVE_FAIL":
            case "SETTINGS_REJECT_FAIL":
                lastSettingsActionSuccess = null;
                lastSettingsActionFail    = (String) m.getData();
                break;

            // ── Promotions approval workflow ─────────────────────────────────
            case "PROMOTION_SUBMITTED":
                lastPromotionSubmitSuccess = (String) m.getData();
                lastPromotionSubmitFail    = null;
                break;
            case "PROMOTION_SUBMIT_FAIL":
                lastPromotionSubmitSuccess = null;
                lastPromotionSubmitFail    = (String) m.getData();
                break;
            case "PROMOTIONS_FOR_PARK_LIST":
                lastParkPromotions = (List<Promotion>) m.getData();
                break;
            case "PENDING_PROMOTIONS_LIST":
                lastPendingPromotions = (List<Promotion>) m.getData();
                break;
            case "PROMOTION_APPROVE_SUCCESS":
            case "PROMOTION_REJECT_SUCCESS":
                lastPromotionActionSuccess = String.valueOf(m.getData());
                lastPromotionActionFail    = null;
                break;
            case "PROMOTION_APPROVE_FAIL":
            case "PROMOTION_REJECT_FAIL":
                lastPromotionActionSuccess = null;
                lastPromotionActionFail    = (String) m.getData();
                break;
        }
        // All field assignments above are complete, NOW unblock the waiting thread.
        awaitResponse = false;
    }

    /**
     * sends a message and blocks until the server response clears {@link #awaitResponse}.
     *
     * @param message the message to send; must not be {@code null}
     */
    public void handleMessageFromClientUI(Message message) {
        try {
            openConnection();
            awaitResponse = true;
            sendToServer(message);
            // 15-second safety timeout so the UI recovers if the server never replies
            long deadline = System.currentTimeMillis() + 15_000;
            while (awaitResponse) {
                if (System.currentTimeMillis() > deadline) {
                    awaitResponse = false; // stop blocking; recover instead of hanging forever
                    lastResult = "No response from server — please try again";
                    clientUI.display("No response from server — please try again");
                    break;
                }
                try { Thread.sleep(100); } catch (InterruptedException e) { e.printStackTrace(); }
            }
        } catch (IOException e) {
            clientUI.display("Could not send message: " + e);
        }
    }

    /**
     * fire-and-forget logout with no {@code awaitResponse}, so the GUI thread isn't blocked.
     * the server marks this client as "Disconnected" in its UI.
     */
    public void sendLogout() {
        try {
            openConnection();
            sendToServer(new Message("LOGOUT", null));
            // No awaitResponse; we don't wait for a reply
        } catch (IOException e) {
            // ignore; we're logging out anyway
        }
    }

}
