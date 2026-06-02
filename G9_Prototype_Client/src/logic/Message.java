package logic;

import java.io.Serializable;

/**
 * protocol envelope for all client–server communication.
 *
 * <p>every object exchanged over the OCSF socket is a {@code Message}.
 * {@code type} acts as a command discriminator; {@code data} carries the typed payload.
 *
 * <p>registered command types (C = client sends, S = server sends):
 * <ul>
 *   <li>{@code UPDATE_ORDER} (C, Order) / {@code UPDATE_SUCCESS} / {@code UPDATE_FAIL} (S)</li>
 *   <li>{@code LOGIN_VISITOR} (C, String) / {@code LOGIN_USER} (C, String[]) /
 *       {@code LOGIN_SUCCESS} (S, LoginResult) / {@code LOGIN_FAIL} (S, String)</li>
 *   <li>{@code GET_PARKS} (C) / {@code PARKS_LIST} (S, List&lt;Park&gt;)</li>
 *   <li>{@code BOOK_ORDER} / {@code JOIN_WAITING_LIST} (C, BookingRequest) /
 *       {@code BOOKING_CONFIRMED} / {@code WAITING_LIST_CONFIRMED} (S, BookingResult) /
 *       {@code BOOKING_FULL} / {@code BOOKING_ERROR} (S)</li>
 *   <li>{@code GET_MY_ORDERS} (C, String) / {@code MY_ORDERS_LIST} (S, List&lt;OrderDetail&gt;)</li>
 *   <li>{@code CANCEL_ORDER} (C, Integer) / {@code CANCEL_SUCCESS} (S, CancelResult) /
 *       {@code CANCEL_FAIL} (S, String)</li>
 *   <li>{@code CHECK_IN_VISITOR} (C, EntryCheckRequest) / {@code ENTRY_APPROVED} (S, EntryResult) /
 *       {@code ENTRY_DENIED} (S, String)</li>
 *   <li>{@code REGISTER_EXIT} (C, ExitRequest) / {@code EXIT_SUCCESS} / {@code EXIT_FAIL} (S, String)</li>
 *   <li>{@code GET_VISITOR_REPORT} (C, Integer parkId) / {@code VISITOR_REPORT_DATA} (S, List&lt;ReportVisitorRow&gt;)</li>
 *   <li>{@code GET_CANCEL_REPORT} (C, Integer parkId) / {@code CANCEL_REPORT_DATA} (S, List&lt;ReportCancelRow&gt;)</li>
 *   <li>{@code GET_USAGE_REPORT} (C) / {@code USAGE_REPORT_DATA} (S, List&lt;ReportUsageRow&gt;)</li>
 * </ul>
 */
public class Message implements Serializable {

    private static final long serialVersionUID = 1L;

    private String type;
    private Object data;

    /**
     * constructs a message with the given command type and payload.
     *
     * @param type the command discriminator string; must not be {@code null}
     * @param data the payload object; may be {@code null} for commands that carry no data
     */
    public Message(String type, Object data) {
        this.type = type;
        this.data = data;
    }

    /**
     * @return the command type string; never {@code null}
     */
    public String getType() { return type; }

    /**
     * the caller must cast to the expected type for the given command.
     *
     * @return the payload object, or {@code null} if this command carries no data
     */
    public Object getData() { return data; }

    @Override
    public String toString() {
        return "Message[type=" + type + ", data=" + data + "]";
    }
}
