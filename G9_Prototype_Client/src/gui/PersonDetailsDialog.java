package gui;

import client.ChatClient;
import client.ClientUI;
import logic.Message;
import logic.PersonDetails;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.GridPane;
import javafx.stage.Window;

/**
 * shared "Personal Details" dialog used by both the subscriber's own <b>My Details</b> screen and
 * the <b>Service Rep</b>'s view/edit-any-user panel.
 *
 * <p>fetches a {@link PersonDetails} by ID from the server, shows the fields (ID is read-only — it
 * is the immutable key), and — when {@code editable} — lets the user save changes back via
 * {@code UPDATE_PERSON_DETAILS}. credit card and family size only appear for subscribers.
 */
final class PersonDetailsDialog {

    private PersonDetailsDialog() {}

    /**
     * loads the person's details and opens the dialog.
     *
     * @param idNumber the government ID to look up
     * @param owner    the owning window (for modality), may be {@code null}
     * @param editable {@code true} to allow saving changes; {@code false} for a read-only view
     */
    static void show(String idNumber, Window owner, boolean editable) {
        if (idNumber == null || idNumber.trim().isEmpty()) {
            Toast.error("Please provide an ID number.");
            return;
        }
        ChatClient.lastPersonDetails      = null;
        ChatClient.lastPersonDetailsError = null;
        ClientUI.chat.accept(new Message("GET_PERSON_DETAILS", idNumber.trim()));
        PersonDetails pd = ChatClient.lastPersonDetails;
        if (pd == null) {
            Toast.error(ChatClient.lastPersonDetailsError != null
                ? ChatClient.lastPersonDetailsError
                : "Could not load details for ID " + idNumber + ".");
            return;
        }

        Dialog<ButtonType> dlg = new Dialog<>();
        dlg.setTitle(editable ? "Edit Personal Details" : "Personal Details");
        dlg.setHeaderText((editable ? "Edit details for " : "Details for ")
            + pd.getFirstName() + " " + pd.getLastName()
            + "  (" + pd.getRole() + ")");
        if (owner != null) {
            dlg.initOwner(owner);
            dlg.initModality(javafx.stage.Modality.WINDOW_MODAL);
        }

        ButtonType saveBtn = new ButtonType("Save Changes", ButtonBar.ButtonData.OK_DONE);
        if (editable) dlg.getDialogPane().getButtonTypes().addAll(saveBtn, ButtonType.CANCEL);
        else          dlg.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);

        GridPane grid = new GridPane();
        grid.setHgap(12);
        grid.setVgap(12);
        grid.setPadding(new Insets(20, 24, 12, 24));

        TextField fldId        = new TextField(pd.getIdNumber());
        fldId.setEditable(false);
        fldId.setDisable(true);   // ID is the immutable key
        TextField fldSubNum    = new TextField(pd.getSubscriberNumber() > 0
            ? String.valueOf(pd.getSubscriberNumber()) : "—");
        fldSubNum.setEditable(false);
        fldSubNum.setDisable(true);   // unique member number is read-only
        TextField fldFirst     = new TextField(pd.getFirstName());
        TextField fldLast      = new TextField(pd.getLastName());
        TextField fldEmail     = new TextField(pd.getEmail());
        TextField fldPhone     = new TextField(pd.getPhone());
        TextField fldCard      = new TextField(pd.getCreditCard());
        fldCard.setPromptText("16 digits — optional (blank = cash)");
        TextField fldFamily    = new TextField(pd.getFamilySize() > 0
            ? String.valueOf(pd.getFamilySize()) : "");

        TextField[] all = { fldId, fldSubNum, fldFirst, fldLast, fldEmail, fldPhone, fldCard, fldFamily };
        for (TextField f : all) { f.getStyleClass().add("text-field"); f.setPrefWidth(250); }
        if (!editable) {
            for (TextField f : new TextField[]{ fldFirst, fldLast, fldEmail, fldPhone, fldCard, fldFamily })
                f.setEditable(false);
        }

        int r = 0;
        grid.add(fieldLabel("ID Number"),   0, r); grid.add(fldId,    1, r++);
        if (pd.isSubscriber()) {
            grid.add(fieldLabel("Subscriber #"), 0, r); grid.add(fldSubNum, 1, r++);
        }
        grid.add(fieldLabel("First Name *"),0, r); grid.add(fldFirst, 1, r++);
        grid.add(fieldLabel("Last Name *"), 0, r); grid.add(fldLast,  1, r++);
        grid.add(fieldLabel("Email *"),     0, r); grid.add(fldEmail, 1, r++);
        grid.add(fieldLabel("Phone *"),     0, r); grid.add(fldPhone, 1, r++);
        if (pd.isSubscriber()) {
            grid.add(fieldLabel("Family Size *"), 0, r); grid.add(fldFamily, 1, r++);
            grid.add(fieldLabel("Credit Card"),   0, r); grid.add(fldCard,   1, r++);
        }

        Label lblErr = new Label("");
        lblErr.getStyleClass().add("lbl-error");
        lblErr.setWrapText(true);
        grid.add(lblErr, 0, r, 2, 1);

        DialogPane pane = dlg.getDialogPane();
        pane.setContent(grid);
        pane.setPrefWidth(460);
        ThemeManager.styleDialog(dlg);

        if (!editable) { dlg.showAndWait(); return; }

        // Validate on Save; keep the dialog open on any error (no close/reopen flash).
        boolean[] saved     = { false };
        javafx.scene.Node okNode = pane.lookupButton(saveBtn);
        okNode.addEventFilter(javafx.event.ActionEvent.ACTION, ev -> {
            lblErr.setText("");
            String first = fldFirst.getText().trim();
            String last  = fldLast.getText().trim();
            String email = fldEmail.getText().trim();
            String phone = fldPhone.getText().trim();

            if (!first.matches("[\\p{L} '-]+")) { lblErr.setText("First name must contain letters only."); ev.consume(); return; }
            if (!last.matches("[\\p{L} '-]+"))  { lblErr.setText("Last name must contain letters only.");  ev.consume(); return; }
            if (!email.matches("^[\\w._%+\\-]+@[\\w.\\-]+\\.[a-zA-Z]{2,}$")) {
                lblErr.setText("Please enter a valid email address."); ev.consume(); return;
            }
            if (!phone.matches("\\d{9,15}")) { lblErr.setText("Phone must be 9–15 digits."); ev.consume(); return; }

            int    familySize = pd.getFamilySize();
            String creditCard = pd.getCreditCard();
            if (pd.isSubscriber()) {
                String fsStr = fldFamily.getText().trim();
                try { familySize = Integer.parseInt(fsStr); }
                catch (NumberFormatException ex) { lblErr.setText("Family size must be a whole number."); ev.consume(); return; }
                if (familySize < 1 || familySize > 10) { lblErr.setText("Family size must be between 1 and 10."); ev.consume(); return; }
                String cc = fldCard.getText().trim();
                if (!cc.isEmpty() && !cc.matches("\\d{16}")) {
                    lblErr.setText("Credit card must be exactly 16 digits (or blank for cash)."); ev.consume(); return;
                }
                creditCard = cc;
            }

            PersonDetails updated = new PersonDetails(
                pd.getIdNumber(), first, last, email, phone,
                creditCard, familySize, pd.getRole(), pd.isSubscriber(),
                pd.getSubscriberNumber());

            ChatClient.lastPersonUpdateOk    = null;
            ChatClient.lastPersonUpdateError = null;
            ClientUI.chat.accept(new Message("UPDATE_PERSON_DETAILS", updated));

            if (Boolean.TRUE.equals(ChatClient.lastPersonUpdateOk)) {
                saved[0] = true;   // let the dialog close
            } else {
                lblErr.setText(ChatClient.lastPersonUpdateError != null
                    ? ChatClient.lastPersonUpdateError : "Could not save changes. Please try again.");
                ev.consume();
            }
        });

        Platform.runLater(fldFirst::requestFocus);
        dlg.showAndWait();
        if (saved[0]) Toast.success("Details updated for ID " + pd.getIdNumber() + ".");
    }

    private static Label fieldLabel(String text) {
        Label l = new Label(text);
        l.getStyleClass().add("field-label");
        return l;
    }
}
