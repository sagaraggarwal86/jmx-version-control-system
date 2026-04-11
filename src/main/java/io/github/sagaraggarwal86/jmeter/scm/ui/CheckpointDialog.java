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
