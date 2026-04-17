package io.github.sagaraggarwal86.jmeter.scm.ui;

import javax.swing.*;
import java.awt.*;

/**
 * About dialog showing plugin version, description, and uninstall note (R10).
 */
public final class AboutDialog {

    private AboutDialog() {
        // utility class
    }

    /**
     * Shows the About dialog.
     *
     * @param parent the parent window
     */
    public static void showDialog(Window parent) {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBorder(BorderFactory.createEmptyBorder(12, 16, 12, 16));

        JLabel title = new JLabel("JMX Version Control System");
        title.setFont(title.getFont().deriveFont(Font.BOLD, 16f));
        title.setAlignmentX(Component.LEFT_ALIGNMENT);
        panel.add(title);

        panel.add(Box.createVerticalStrut(8));

        String versionText = "Version ";
        String implVersion = AboutDialog.class.getPackage().getImplementationVersion();
        versionText += (implVersion != null) ? implVersion : "dev";
        JLabel version = new JLabel(versionText);
        version.setAlignmentX(Component.LEFT_ALIGNMENT);
        panel.add(version);

        panel.add(Box.createVerticalStrut(12));

        JLabel description = new JLabel(
            "<html>Lightweight local version control for JMeter test plans (.jmx files).<br>" +
                "Manual and auto checkpoints, linear version history, one-click rollback.<br>" +
                "No Git, no SVN, no external tools required.</html>");
        description.setAlignmentX(Component.LEFT_ALIGNMENT);
        panel.add(description);

        panel.add(Box.createVerticalStrut(16));

        // R10: Uninstall note
        JLabel note = new JLabel(
            "<html><b>Note:</b> Version snapshots are stored in the configured storage<br>" +
                "location (default: .history). Removing this plugin does <b>not</b><br>" +
                "delete your version history.</html>");
        note.setForeground(new Color(100, 100, 100));
        note.setAlignmentX(Component.LEFT_ALIGNMENT);
        panel.add(note);

        JOptionPane.showMessageDialog(parent, panel,
            "About — JVCS", JOptionPane.PLAIN_MESSAGE);
    }
}
