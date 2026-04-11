package io.github.sagaraggarwal86.jmeter.scm.ui;

import io.github.sagaraggarwal86.jmeter.scm.config.ScmConfigManager;
import io.github.sagaraggarwal86.jmeter.scm.core.ScmContext;
import io.github.sagaraggarwal86.jmeter.scm.core.ScmInitializer;
import io.github.sagaraggarwal86.jmeter.scm.model.VersionIndex;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.*;
import java.awt.*;

/**
 * Settings dialog for storage location, max retention, and stale lock timeout.
 */
public final class SettingsDialog {

    private static final Logger log = LoggerFactory.getLogger(SettingsDialog.class);

    private SettingsDialog() {
        // utility class
    }

    /**
     * Shows the settings dialog.
     *
     * @param parent      the parent window
     * @param initializer the SCM initializer
     */
    public static void showDialog(Window parent, ScmInitializer initializer) {
        ScmContext context = initializer.getCurrentContext();

        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(4, 4, 4, 4);
        gbc.anchor = GridBagConstraints.WEST;

        // Storage Location
        gbc.gridx = 0;
        gbc.gridy = 0;
        panel.add(new JLabel("Storage Location:"), gbc);

        String currentStorage = context != null
                ? ScmConfigManager.getStorageLocation(context.getVersionIndex())
                : ScmConfigManager.getGlobalStorageLocation();
        JTextField storageField = new JTextField(currentStorage, 25);
        gbc.gridx = 1;
        panel.add(storageField, gbc);

        // Max Retention
        gbc.gridx = 0;
        gbc.gridy = 1;
        panel.add(new JLabel("Max Retention:"), gbc);

        int currentRetention = context != null
                ? ScmConfigManager.getMaxRetention(context.getVersionIndex())
                : ScmConfigManager.getGlobalMaxRetention();
        JSpinner retentionSpinner = new JSpinner(new SpinnerNumberModel(currentRetention, 1, 1000, 1));
        gbc.gridx = 1;
        panel.add(retentionSpinner, gbc);

        // Stale Lock Timeout
        gbc.gridx = 0;
        gbc.gridy = 2;
        panel.add(new JLabel("Stale Lock Timeout (minutes):"), gbc);

        JSpinner staleSpinner = new JSpinner(
                new SpinnerNumberModel(ScmConfigManager.getStaleLockMinutes(), 1, 10080, 5));
        gbc.gridx = 1;
        panel.add(staleSpinner, gbc);

        // Note
        gbc.gridx = 0;
        gbc.gridy = 3;
        gbc.gridwidth = 2;
        JLabel noteLabel = new JLabel("<html><i>Per-plan settings are saved in index.json.<br>" +
                "Global defaults can be set in jmeter.properties.</i></html>");
        noteLabel.setForeground(Color.GRAY);
        panel.add(noteLabel, gbc);

        int result = JOptionPane.showConfirmDialog(parent, panel,
                "Settings — SCM Plugin",
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);

        if (result == JOptionPane.OK_OPTION && context != null && !context.isReadOnly()) {
            try {
                VersionIndex index = context.getVersionIndex();
                String newStorage = storageField.getText().trim();
                int newRetention = (int) retentionSpinner.getValue();

                if (!newStorage.isBlank()) {
                    index.setStorageLocation(newStorage);
                }
                index.setMaxRetention(newRetention);

                context.getIndexManager().save(context.getStorageDir(), index);
                initializer.notifyVersionsChanged();
                log.info("Settings updated: storage={}, retention={}", newStorage, newRetention);
            } catch (Exception e) {
                log.error("Failed to save settings: {}", e.getMessage(), e);
                JOptionPane.showMessageDialog(parent,
                        "Failed to save settings: " + e.getMessage(),
                        "SCM Plugin", JOptionPane.ERROR_MESSAGE);
            }
        }
    }
}
