package io.github.sagaraggarwal86.jmeter.scm.core;

import io.github.sagaraggarwal86.jmeter.scm.model.TriggerType;
import io.github.sagaraggarwal86.jmeter.scm.model.VersionEntry;
import org.apache.jmeter.exceptions.IllegalUserActionException;
import org.apache.jmeter.gui.action.ActionNames;
import org.apache.jmeter.gui.action.Command;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.util.HashSet;
import java.util.Set;

/**
 * Wraps JMeter's SAVE action to trigger post-save snapshots.
 * <p>
 * Safety: entire post-save logic is in try-catch(Throwable) — native save is never blocked (R3).
 */
public final class SaveCommandWrapper implements Command {

    private static final Logger log = LoggerFactory.getLogger(SaveCommandWrapper.class);

    private final Command originalCommand;
    private final ScmInitializer initializer;

    /**
     * Creates a wrapper around the original save command.
     *
     * @param originalCommand the original JMeter SAVE command
     * @param initializer     the SCM initializer for context access
     */
    public SaveCommandWrapper(Command originalCommand, ScmInitializer initializer) {
        this.originalCommand = originalCommand;
        this.initializer = initializer;
    }

    @Override
    public void doAction(ActionEvent e) throws IllegalUserActionException {
        originalCommand.doAction(e);

        // R3: never block save
        try {
            performPostSaveSnapshot();
        } catch (Throwable t) {
            log.error("Post-save snapshot failed (save itself succeeded): {}", t.getMessage(), t);
            SwingUtilities.invokeLater(() ->
                    JOptionPane.showMessageDialog(null,
                            "Version snapshot failed: " + t.getMessage() + "\nYour save was successful.",
                            "SCM Plugin", JOptionPane.WARNING_MESSAGE));
        }
    }

    @Override
    public Set<String> getActionNames() {
        Set<String> names = new HashSet<>();
        names.add(ActionNames.SAVE);
        return names;
    }

    private void performPostSaveSnapshot() throws Exception {
        ScmContext context = initializer.getCurrentContext();
        if (context == null || context.isDisposed() || context.isReadOnly()) {
            log.debug("No active SCM context or read-only — skipping post-save snapshot");
            return;
        }

        VersionEntry entry = context.getSnapshotEngine().createSnapshot(
                context.getJmxFile(),
                context.getStorageDir(),
                context.getVersionIndex(),
                TriggerType.SAVE,
                null);

        if (entry != null) {
            // Reuse checksum from snapshot to avoid redundant file read
            context.getDirtyTracker().reset(entry.getChecksum());
        } else {
            context.getDirtyTracker().reset();
        }

        initializer.notifyVersionsChanged();
    }
}
