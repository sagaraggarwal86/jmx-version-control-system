package io.github.sagaraggarwal86.jmeter.scm.ui;

import javax.swing.*;
import java.awt.*;

/**
 * Dialog for entering an optional note when creating a manual checkpoint.
 */
public final class CheckpointDialog {

    private CheckpointDialog() {
        // utility class
    }

    /**
     * Shows the checkpoint dialog and returns the user's note.
     *
     * @param parent the parent window
     * @return the note text (may be empty), or null if cancelled
     */
    public static String showDialog(Window parent) {
        JPanel panel = new JPanel(new BorderLayout(8, 8));
        panel.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

        JLabel label = new JLabel("Enter a note for this checkpoint (optional):");
        panel.add(label, BorderLayout.NORTH);

        JTextField noteField = new JTextField(30);
        noteField.setDocument(new javax.swing.text.PlainDocument() {
            @Override
            public void insertString(int offs, String str, javax.swing.text.AttributeSet a)
                    throws javax.swing.text.BadLocationException {
                if (str == null) return;
                if (getLength() + str.length() <= 500) {
                    super.insertString(offs, str, a);
                }
            }
        });
        panel.add(noteField, BorderLayout.CENTER);

        int result = JOptionPane.showConfirmDialog(parent, panel,
                "Create Checkpoint — SCM Plugin",
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);

        if (result == JOptionPane.OK_OPTION) {
            return noteField.getText().trim();
        }
        return null;
    }
}
