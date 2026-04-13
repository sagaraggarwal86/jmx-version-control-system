package io.github.sagaraggarwal86.jmeter.scm.ui;

import io.github.sagaraggarwal86.jmeter.scm.core.ScmContext;
import io.github.sagaraggarwal86.jmeter.scm.core.ScmInitializer;
import org.apache.jmeter.gui.GuiPackage;
import org.apache.jmeter.gui.action.ActionNames;
import org.apache.jmeter.gui.action.ActionRouter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;

/**
 * Creates "Version Control" menu items and handles action triggering.
 */
public final class ScmMenuHandler {

    private static final Logger log = LoggerFactory.getLogger(ScmMenuHandler.class);

    private ScmMenuHandler() {
    }

    static Window getParentWindow() {
        try {
            return GuiPackage.getInstance().getMainFrame();
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Returns the current writable context after ensuring initialization.
     * Shows a warning and returns null if no active plan or read-only.
     */
    private static ScmContext getWritableContext(ScmInitializer initializer) {
        initializer.ensureInitializedWithContext();
        var context = initializer.getCurrentContext();
        if (context == null || context.isDisposed() || context.isReadOnly()) {
            JOptionPane.showMessageDialog(getParentWindow(),
                    "No active test plan or read-only mode.",
                    "SCM Plugin", JOptionPane.WARNING_MESSAGE);
            return null;
        }
        return context;
    }

    /**
     * Creates the Version Control submenu items for the Tools menu.
     */
    public static JMenuItem[] createMenuItems(ScmInitializer initializer) {
        JMenu versionControlMenu = new JMenu("Version Control");

        JMenuItem showHistory = new JMenuItem("Show History");
        showHistory.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_H, InputEvent.CTRL_DOWN_MASK));
        showHistory.addActionListener(e -> triggerShowHistory(initializer));
        versionControlMenu.add(showHistory);

        JMenuItem saveCheckpoint = new JMenuItem("Save Checkpoint");
        saveCheckpoint.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_K, InputEvent.CTRL_DOWN_MASK));
        saveCheckpoint.addActionListener(e -> triggerCheckpoint(initializer));
        versionControlMenu.add(saveCheckpoint);

        JMenuItem deleteVersions = new JMenuItem("Delete Versions");
        deleteVersions.addActionListener(e -> triggerDeleteVersions(initializer));
        versionControlMenu.add(deleteVersions);

        JMenuItem lockItem = new JMenuItem("Lock");
        lockItem.addActionListener(e -> triggerLock(initializer));
        versionControlMenu.add(lockItem);

        versionControlMenu.addSeparator();

        JMenuItem settings = new JMenuItem("Settings");
        settings.addActionListener(e -> {
            initializer.ensureInitializedWithContext();
            SettingsDialog.showDialog(getParentWindow(), initializer);
        });
        versionControlMenu.add(settings);

        JMenuItem about = new JMenuItem("About");
        about.addActionListener(e -> AboutDialog.showDialog(getParentWindow()));
        versionControlMenu.add(about);

        log.debug("Version Control menu items created");
        return new JMenuItem[]{versionControlMenu};
    }

    /**
     * Toggles version history panel visibility.
     */
    public static void triggerShowHistory(ScmInitializer initializer) {
        initializer.ensureInitializedWithContext();
        VersionHistoryPanel panel = initializer.getHistoryPanel();
        if (panel != null) {
            panel.toggleVisibility();
        }
    }

    /**
     * Deletes selected versions from the history panel. Opens panel if hidden.
     */
    public static void triggerDeleteVersions(ScmInitializer initializer) {
        initializer.ensureInitializedWithContext();
        VersionHistoryPanel panel = initializer.getHistoryPanel();
        if (panel == null) return;

        if (!panel.isVisible()) {
            panel.toggleVisibility();
        }
        panel.deleteSelectedVersions();
    }

    /**
     * Context-aware lock action:
     * - Read-write (own lock): info message
     * - Read-only: try polite acquire, escalate to force release if needed
     * - No context: info message
     */
    public static void triggerLock(ScmInitializer initializer) {
        initializer.ensureInitializedWithContext();
        var context = initializer.getCurrentContext();
        if (context == null || context.isDisposed()) {
            JOptionPane.showMessageDialog(getParentWindow(),
                    "No active test plan.",
                    "SCM Plugin", JOptionPane.WARNING_MESSAGE);
            return;
        }

        // Try to acquire (also verifies we still own it if read-write)
        boolean wasReadWrite = !context.isReadOnly();
        if (context.tryAcquireLock()) {
            if (wasReadWrite) {
                JOptionPane.showMessageDialog(getParentWindow(),
                        "You already hold the lock.",
                        "SCM Plugin", JOptionPane.INFORMATION_MESSAGE);
            } else {
                initializer.notifyVersionsChanged();
                Toast.show("Lock acquired — read-write mode restored");
            }
            return;
        }
        // Lock held by another instance — update UI if state just changed
        if (wasReadWrite) {
            initializer.notifyVersionsChanged();
        }

        // Polite acquire failed — another instance holds a non-stale lock
        String lockDetails = "";
        var lockInfo = context.getLockInfo();
        if (lockInfo != null) {
            lockDetails = "\n\nLock held by:\n" +
                    "  PID: " + lockInfo.getPid() + "\n" +
                    "  Host: " + lockInfo.getHostname() + "\n" +
                    "  Since: " + lockInfo.getTimestamp();
        }

        int confirm = JOptionPane.showConfirmDialog(getParentWindow(),
                "WARNING: Force releasing the lock may cause data corruption\n" +
                        "if another JMeter instance is actively using this test plan.\n\n" +
                        "Only use this if you are certain the other instance is no longer running." +
                        lockDetails + "\n\nForce release and acquire the lock?",
                "Force Acquire Lock",
                JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
        if (confirm != JOptionPane.YES_OPTION) return;

        if (context.forceReleaseLock()) {
            initializer.notifyVersionsChanged();
            Toast.show("Lock force-acquired — read-write mode restored");
        } else {
            JOptionPane.showMessageDialog(getParentWindow(),
                    "Failed to acquire the lock.",
                    "SCM Plugin", JOptionPane.ERROR_MESSAGE);
        }
    }

    /**
     * Triggers checkpoint creation. Called by menu action and key binding.
     */
    public static void triggerCheckpoint(ScmInitializer initializer) {
        var context = getWritableContext(initializer);
        if (context == null) return;

        String note = CheckpointDialog.showDialog(getParentWindow());
        if (note == null) return;

        // Save in-memory state to disk first, so the checkpoint captures current changes
        try {
            ActionRouter.getInstance().doActionNow(
                    new ActionEvent(initializer, ActionEvent.ACTION_PERFORMED, ActionNames.SAVE));
        } catch (Exception e) {
            log.warn("Could not save before checkpoint: {}", e.getMessage());
        }

        SwingWorker<Void, Void> worker = new SwingWorker<>() {
            @Override
            protected Void doInBackground() throws Exception {
                context.createCheckpoint(note.isBlank() ? null : note);
                return null;
            }

            @Override
            protected void done() {
                try {
                    get();
                    initializer.notifyVersionsChanged();
                    Toast.show("Checkpoint created");
                } catch (Exception ex) {
                    Throwable cause = ex.getCause() != null ? ex.getCause() : ex;
                    log.warn("Checkpoint failed: {}", cause.getMessage());
                    initializer.notifyVersionsChanged();
                    JOptionPane.showMessageDialog(getParentWindow(),
                            "Checkpoint failed: " + cause.getMessage(),
                            "SCM Plugin", JOptionPane.WARNING_MESSAGE);
                }
            }
        };
        worker.execute();
    }
}
