package io.github.sagaraggarwal86.jmeter.scm.ui;

import io.github.sagaraggarwal86.jmeter.scm.config.ScmConfigManager;
import io.github.sagaraggarwal86.jmeter.scm.core.ScmContext;
import io.github.sagaraggarwal86.jmeter.scm.core.ScmInitializer;
import io.github.sagaraggarwal86.jmeter.scm.model.VersionEntry;
import io.github.sagaraggarwal86.jmeter.scm.model.VersionIndex;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.*;
import java.awt.*;

/**
 * Settings dialog for storage location, max retention, stale lock timeout, and auto-save.
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

        // Auto-Save separator
        gbc.gridx = 0;
        gbc.gridy = 3;
        gbc.gridwidth = 2;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        panel.add(new JSeparator(), gbc);
        gbc.fill = GridBagConstraints.NONE;
        gbc.gridwidth = 1;

        // Auto-Checkpoint Enabled
        gbc.gridx = 0;
        gbc.gridy = 4;
        panel.add(new JLabel("Auto-Checkpoint:"), gbc);

        JCheckBox autoSaveCheckbox = new JCheckBox("Enabled", ScmConfigManager.isAutoSaveEnabled());
        gbc.gridx = 1;
        panel.add(autoSaveCheckbox, gbc);

        // Auto-Checkpoint Interval
        gbc.gridx = 0;
        gbc.gridy = 5;
        panel.add(new JLabel("Auto-Checkpoint Interval (minutes):"), gbc);

        JSpinner autoSaveSpinner = new JSpinner(
                new SpinnerNumberModel(ScmConfigManager.getAutoSaveIntervalMinutes(), 1, 60, 1));
        autoSaveSpinner.setEnabled(autoSaveCheckbox.isSelected());
        autoSaveCheckbox.addActionListener(e -> autoSaveSpinner.setEnabled(autoSaveCheckbox.isSelected()));
        gbc.gridx = 1;
        panel.add(autoSaveSpinner, gbc);

        // Toolbar separator
        gbc.gridx = 0;
        gbc.gridy = 6;
        gbc.gridwidth = 2;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        panel.add(new JSeparator(), gbc);
        gbc.fill = GridBagConstraints.NONE;
        gbc.gridwidth = 1;

        // Show Toolbar Icons
        gbc.gridx = 0;
        gbc.gridy = 7;
        panel.add(new JLabel("Show Toolbar Icons:"), gbc);

        JCheckBox toolbarCheckbox = new JCheckBox("Visible", ScmConfigManager.isToolbarVisible());
        gbc.gridx = 1;
        panel.add(toolbarCheckbox, gbc);

        // Note
        gbc.gridx = 0;
        gbc.gridy = 8;
        gbc.gridwidth = 2;
        JLabel noteLabel = new JLabel("<html><i>Per-plan: saved in index.json (overrides global).<br>" +
                "Global: saved in user.properties (JMETER_HOME/bin/).<br>" +
                "Property prefix: scm.* — see user.properties for all settings.</i></html>");
        noteLabel.setForeground(Color.GRAY);
        panel.add(noteLabel, gbc);

        int result = JOptionPane.showConfirmDialog(parent, panel,
                "Settings — SCM Plugin",
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);

        if (result == JOptionPane.OK_OPTION) {
            // Save per-plan settings
            if (context != null && !context.isReadOnly()) {
                try {
                    VersionIndex index = context.getVersionIndex();
                    String newStorage = storageField.getText().trim();
                    int newRetention = (int) retentionSpinner.getValue();

                    // Check retention reduction before mutating index
                    boolean pruned = false;
                    int currentCount = index.getVersions().size();
                    int excess = currentCount - newRetention;
                    if (excess > 0) {
                        VersionEntry latest = index.getLatestVersion();
                        long deletableCount = index.getVersions().stream()
                                .filter(v -> !index.isPinned(v.getVersion()))
                                .filter(v -> latest == null || v.getVersion() != latest.getVersion())
                                .count();
                        int willDelete = (int) Math.min(excess, deletableCount);

                        if (willDelete > 0) {
                            int confirm = JOptionPane.showConfirmDialog(parent,
                                    "Reducing retention to " + newRetention + " will delete " +
                                            willDelete + " version(s).\n" +
                                            "Kept versions and the latest version will be preserved.\n" +
                                            "This cannot be undone. Continue?",
                                    "Retention Change", JOptionPane.YES_NO_OPTION,
                                    JOptionPane.WARNING_MESSAGE);
                            if (confirm != JOptionPane.YES_OPTION) {
                                newRetention = currentRetention;
                            }
                        }
                    }

                    // Apply all index mutations atomically after confirmations
                    if (!newStorage.isBlank()) {
                        index.setStorageLocation(newStorage);
                    }
                    index.setMaxRetention(newRetention);

                    if (newRetention < currentCount) {
                        int deleted = context.pruneExcessVersions();
                        if (deleted > 0) {
                            pruned = true;
                            Toast.show("Deleted " + deleted + " version(s) due to retention change");
                        }
                    }

                    if (!pruned) {
                        context.getIndexManager().save(context.getStorageDir(), index);
                    }
                    initializer.notifyVersionsChanged();
                    log.info("Settings updated: storage={}, retention={}",
                            newStorage, index.getMaxRetention());
                } catch (Exception e) {
                    log.error("Failed to save settings: {}", e.getMessage(), e);
                    JOptionPane.showMessageDialog(parent,
                            "Failed to save settings: " + e.getMessage(),
                            "SCM Plugin", JOptionPane.ERROR_MESSAGE);
                }
            }

            // Save global settings — persisted to user.properties for restart survival
            ScmConfigManager.setAndPersist(ScmConfigManager.PROP_LOCK_STALE_MINUTES,
                    String.valueOf((int) staleSpinner.getValue()));

            boolean newAutoSaveEnabled = autoSaveCheckbox.isSelected();
            int newAutoSaveInterval = (int) autoSaveSpinner.getValue();
            ScmConfigManager.setAndPersist(ScmConfigManager.PROP_AUTOSAVE_ENABLED,
                    String.valueOf(newAutoSaveEnabled));
            ScmConfigManager.setAndPersist(ScmConfigManager.PROP_AUTOSAVE_INTERVAL,
                    String.valueOf(newAutoSaveInterval));
            initializer.restartAutoCheckpoint();

            boolean newToolbarVisible = toolbarCheckbox.isSelected();
            ScmConfigManager.setAndPersist(ScmConfigManager.PROP_TOOLBAR_VISIBLE,
                    String.valueOf(newToolbarVisible));
            initializer.setToolbarVisible(newToolbarVisible);

            log.info("Settings updated: staleLock={}min, autoCheckpoint={}, interval={}min, toolbar={}",
                    staleSpinner.getValue(), newAutoSaveEnabled, newAutoSaveInterval, newToolbarVisible);
        }
    }
}
