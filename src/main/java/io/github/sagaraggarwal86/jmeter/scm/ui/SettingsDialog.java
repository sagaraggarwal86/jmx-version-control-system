package io.github.sagaraggarwal86.jmeter.scm.ui;

import io.github.sagaraggarwal86.jmeter.scm.config.ScmConfigManager;
import io.github.sagaraggarwal86.jmeter.scm.core.ScmContext;
import io.github.sagaraggarwal86.jmeter.scm.core.ScmInitializer;
import io.github.sagaraggarwal86.jmeter.scm.model.VersionEntry;
import io.github.sagaraggarwal86.jmeter.scm.model.VersionIndex;
import io.github.sagaraggarwal86.jmeter.scm.storage.AuditLogger;
import io.github.sagaraggarwal86.jmeter.scm.storage.FileOperations;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.*;
import java.awt.*;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

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

        String currentStorage = ScmConfigManager.getGlobalStorageLocation();
        JTextField storageField = new JTextField(currentStorage, 20);
        JButton browseBtn = new JButton("Browse...");
        browseBtn.setMargin(new Insets(1, 4, 1, 4));
        browseBtn.addActionListener(ev -> {
            JFileChooser chooser = new JFileChooser(storageField.getText());
            chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
            chooser.setDialogTitle("Select Storage Location");
            if (chooser.showOpenDialog(panel) == JFileChooser.APPROVE_OPTION) {
                storageField.setText(chooser.getSelectedFile().getAbsolutePath());
            }
        });
        JPanel storageRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        storageRow.add(storageField);
        storageRow.add(browseBtn);
        gbc.gridx = 1;
        panel.add(storageRow, gbc);

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
        JLabel noteLabel = new JLabel("<html><i>All settings saved in user.properties (JMETER_HOME/bin/).<br>" +
                "Property prefix: scm.* — see user.properties for all options.<br>" +
                "Retention and frozen versions are per test plan (stored in index.json).</i></html>");
        noteLabel.setForeground(Color.GRAY);
        panel.add(noteLabel, gbc);

        // Button panel: [Reset to Defaults]  ----  [OK] [Cancel]
        JPanel buttonPanel = new JPanel(new BorderLayout());
        buttonPanel.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

        JButton resetBtn = new JButton("Reset to Defaults");
        resetBtn.addActionListener(ev -> {
            storageField.setText(ScmConfigManager.DEFAULT_STORAGE_LOCATION);
            retentionSpinner.setValue(ScmConfigManager.DEFAULT_MAX_RETENTION);
            staleSpinner.setValue(ScmConfigManager.DEFAULT_LOCK_STALE_MINUTES);
            autoSaveCheckbox.setSelected(ScmConfigManager.DEFAULT_AUTOSAVE_ENABLED);
            autoSaveSpinner.setValue(ScmConfigManager.DEFAULT_AUTOSAVE_INTERVAL);
            autoSaveSpinner.setEnabled(ScmConfigManager.DEFAULT_AUTOSAVE_ENABLED);
            toolbarCheckbox.setSelected(ScmConfigManager.DEFAULT_TOOLBAR_VISIBLE);
        });
        buttonPanel.add(resetBtn, BorderLayout.WEST);

        JPanel rightButtons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        JButton okBtn = new JButton("OK");
        JButton cancelBtn = new JButton("Cancel");
        rightButtons.add(okBtn);
        rightButtons.add(cancelBtn);
        buttonPanel.add(rightButtons, BorderLayout.EAST);

        JDialog dialog = new JDialog(parent, "Settings — SCM Plugin", Dialog.ModalityType.APPLICATION_MODAL);
        dialog.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
        dialog.setResizable(false);
        dialog.getContentPane().setLayout(new BorderLayout());
        dialog.getContentPane().add(panel, BorderLayout.CENTER);
        dialog.getContentPane().add(buttonPanel, BorderLayout.SOUTH);

        boolean[] confirmed = {false};
        okBtn.addActionListener(ev -> {
            confirmed[0] = true;
            dialog.dispose();
        });
        cancelBtn.addActionListener(ev -> dialog.dispose());
        dialog.getRootPane().setDefaultButton(okBtn);

        // Escape key dismisses dialog (Cancel behavior)
        dialog.getRootPane().registerKeyboardAction(
                ev -> dialog.dispose(),
                KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_ESCAPE, 0),
                JComponent.WHEN_IN_FOCUSED_WINDOW);

        dialog.pack();
        dialog.setLocationRelativeTo(parent);
        dialog.setVisible(true);

        if (confirmed[0]) {
            // Handle storage location change first (may reinit context)
            String newStorageVal = storageField.getText().trim();
            if (newStorageVal.isBlank()) {
                newStorageVal = ScmConfigManager.DEFAULT_STORAGE_LOCATION;
            }
            String oldStorageVal = ScmConfigManager.getGlobalStorageLocation();
            if (!newStorageVal.equals(oldStorageVal)) {
                if (context != null && !context.isDisposed()) {
                    boolean changed = handleStorageLocationChange(
                            parent, initializer, context, oldStorageVal, newStorageVal);
                    if (changed) {
                        context = initializer.getCurrentContext();
                    }
                } else {
                    // No active test plan — just persist the new location
                    ScmConfigManager.setAndPersist(ScmConfigManager.PROP_STORAGE_LOCATION, newStorageVal);
                }
            }

            // Save per-plan settings
            if (context != null && !context.isReadOnly()) {
                try {
                    VersionIndex index = context.getVersionIndex();
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
                    log.info("Settings updated: retention={}", index.getMaxRetention());
                } catch (Exception e) {
                    log.error("Failed to save settings: {}", e.getMessage(), e);
                    JOptionPane.showMessageDialog(parent,
                            "Failed to save settings: " + e.getMessage(),
                            "SCM Plugin", JOptionPane.ERROR_MESSAGE);
                }
            }

            ScmConfigManager.setAndPersist(ScmConfigManager.PROP_MAX_RETENTION,
                    String.valueOf((int) retentionSpinner.getValue()));
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

            log.info("Settings updated: storage={}, retention={}, staleLock={}min, autoCheckpoint={}, interval={}min, toolbar={}",
                    storageField.getText().trim(), retentionSpinner.getValue(),
                    staleSpinner.getValue(), newAutoSaveEnabled, newAutoSaveInterval, newToolbarVisible);
        }
    }

    /**
     * Shows Migrate/Reset/Cancel dialog for storage location change.
     *
     * @return true if location was changed (Migrate or Reset), false if cancelled
     */
    private static boolean handleStorageLocationChange(Window parent, ScmInitializer initializer,
                                                       ScmContext context, String oldLocation,
                                                       String newLocation) {
        String message = "Storage location will change from:\n  " + oldLocation +
                "\nto:\n  " + newLocation + "\n\n" +
                "Migrate \u2014 Move all version files to the new location\n" +
                "Reset \u2014 Start fresh at the new location (old files deleted)\n" +
                "Cancel \u2014 Keep the current storage location";

        String[] options = {"Migrate", "Reset", "Cancel"};
        int choice = JOptionPane.showOptionDialog(parent, message,
                "Storage Location Change",
                JOptionPane.DEFAULT_OPTION, JOptionPane.QUESTION_MESSAGE,
                null, options, options[0]);

        Path jmxFile = context.getJmxFile();

        switch (choice) {
            case 0: // Migrate
                Path oldDir = context.getStorageDir();
                Path newDir = resolveStorageDir(jmxFile, newLocation);
                ScmConfigManager.setAndPersist(ScmConfigManager.PROP_STORAGE_LOCATION, newLocation);
                initializer.disposeCurrentContext();
                migrateFiles(parent, oldDir, newDir);
                initializer.initializeForTestPlan(jmxFile);
                AuditLogger.logStorageMigrate(newDir, oldLocation, newLocation);
                return true;

            case 1: // Reset
                Path resetOldDir = context.getStorageDir();
                ScmConfigManager.setAndPersist(ScmConfigManager.PROP_STORAGE_LOCATION, newLocation);
                initializer.disposeCurrentContext();
                Path backupZip = backupAndDelete(resetOldDir);
                initializer.initializeForTestPlan(jmxFile);
                Path resetNewDir = resolveStorageDir(jmxFile, newLocation);
                AuditLogger.logStorageReset(resetNewDir, oldLocation,
                        backupZip != null ? backupZip.toString() : null);
                if (backupZip != null) {
                    Toast.show("Backup saved to " + backupZip.getFileName() + ". Fresh history started");
                } else {
                    Toast.show("Fresh history started at new location");
                }
                return true;

            default: // Cancel or dialog closed
                return false;
        }
    }

    private static Path resolveStorageDir(Path jmxFile, String storageLocation) {
        Path parent = jmxFile.getParent();
        if (parent == null) parent = Path.of(".");
        return parent.resolve(storageLocation).resolve(FileOperations.extractStem(jmxFile));
    }

    private static void migrateFiles(Window parent, Path oldDir, Path newDir) {
        if (!Files.isDirectory(oldDir)) {
            log.warn("Old storage directory does not exist: {}", oldDir);
            return;
        }
        try {
            Files.createDirectories(newDir);
            int[] migrated = {0};
            int[] failed = {0};
            try (var stream = Files.list(oldDir)) {
                stream.filter(Files::isRegularFile).forEach(file -> {
                    try {
                        Files.move(file, newDir.resolve(file.getFileName()),
                                StandardCopyOption.REPLACE_EXISTING);
                        migrated[0]++;
                    } catch (IOException e) {
                        log.warn("Could not migrate {}: {}", file.getFileName(), e.getMessage());
                        failed[0]++;
                    }
                });
            }
            // Clean up old directory if empty
            try (var remaining = Files.list(oldDir)) {
                if (remaining.findAny().isEmpty()) {
                    Files.deleteIfExists(oldDir);
                }
            }
            if (failed[0] > 0) {
                JOptionPane.showMessageDialog(parent,
                        "Migrated " + migrated[0] + " file(s), " + failed[0] + " failed.\n" +
                                "Check the old location for remaining files.",
                        "SCM Plugin", JOptionPane.WARNING_MESSAGE);
            } else {
                Toast.show("Migrated " + migrated[0] + " file(s) to new location");
            }
        } catch (IOException e) {
            log.warn("Migration failed: {}", e.getMessage());
            JOptionPane.showMessageDialog(parent,
                    "Migration failed: " + e.getMessage() + "\nFiles may need manual migration.",
                    "SCM Plugin", JOptionPane.WARNING_MESSAGE);
        }
    }

    /**
     * Zips all files in the directory to a backup, then deletes the originals.
     * Backup is created in the parent directory: {@code <stem>_backup_<timestamp>.zip}.
     *
     * @return the backup zip path, or null if directory was empty or didn't exist
     */
    private static Path backupAndDelete(Path dir) {
        if (!Files.isDirectory(dir)) {
            return null;
        }
        try (var check = Files.list(dir)) {
            if (check.findAny().isEmpty()) {
                Files.deleteIfExists(dir);
                return null;
            }
        } catch (IOException e) {
            log.warn("Could not list directory: {}", e.getMessage());
            return null;
        }

        Path parentDir = dir.getParent();
        if (parentDir == null) parentDir = Path.of(".");
        String stem = dir.getFileName().toString();
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
        Path zipFile = parentDir.resolve(stem + "_backup_" + timestamp + ".zip");

        try (OutputStream fos = Files.newOutputStream(zipFile);
             ZipOutputStream zos = new ZipOutputStream(fos);
             var stream = Files.list(dir)) {
            stream.filter(Files::isRegularFile).forEach(file -> {
                try {
                    zos.putNextEntry(new ZipEntry(file.getFileName().toString()));
                    Files.copy(file, zos);
                    zos.closeEntry();
                } catch (IOException e) {
                    log.warn("Could not zip {}: {}", file.getFileName(), e.getMessage());
                }
            });
        } catch (IOException e) {
            log.warn("Backup zip failed: {}", e.getMessage());
            return null;
        }

        // Delete originals after successful zip
        try (var stream = Files.list(dir)) {
            stream.filter(Files::isRegularFile).forEach(file -> {
                try {
                    Files.deleteIfExists(file);
                } catch (IOException e) {
                    log.warn("Could not delete {}: {}", file.getFileName(), e.getMessage());
                }
            });
        } catch (IOException e) {
            log.warn("Could not clean up old files: {}", e.getMessage());
        }
        try (var remaining = Files.list(dir)) {
            if (remaining.findAny().isEmpty()) {
                Files.deleteIfExists(dir);
            }
        } catch (IOException e) {
            log.warn("Could not remove directory {}: {}", dir, e.getMessage());
        }

        log.info("Backup created: {}", zipFile);
        return zipFile;
    }
}
