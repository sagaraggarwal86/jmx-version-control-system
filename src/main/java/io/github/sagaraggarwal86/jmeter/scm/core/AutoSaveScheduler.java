package io.github.sagaraggarwal86.jmeter.scm.core;

import io.github.sagaraggarwal86.jmeter.scm.config.ScmConfigManager;
import io.github.sagaraggarwal86.jmeter.scm.model.TriggerType;
import io.github.sagaraggarwal86.jmeter.scm.model.VersionEntry;
import io.github.sagaraggarwal86.jmeter.scm.ui.Toast;
import org.apache.jmeter.gui.action.ActionNames;
import org.apache.jmeter.gui.action.ActionRouter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Auto-checkpoint scheduler: periodically saves the script to disk
 * (via JMeter's SAVE action) then creates a version checkpoint.
 */
public final class AutoSaveScheduler {

    private static final Logger log = LoggerFactory.getLogger(AutoSaveScheduler.class);

    private final ScmInitializer initializer;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private Timer timer;
    private volatile SwingWorker<VersionEntry, Void> activeWorker;

    public AutoSaveScheduler(ScmInitializer initializer) {
        this.initializer = initializer;
    }

    public void start() {
        stop();

        if (!ScmConfigManager.isAutoSaveEnabled()) {
            log.debug("Auto-checkpoint disabled");
            return;
        }

        int intervalMs = ScmConfigManager.getAutoSaveIntervalMinutes() * 60 * 1000;
        timer = new Timer(intervalMs, e -> performAutoCheckpoint());
        timer.setRepeats(true);
        timer.start();
        log.info("Auto-checkpoint started (interval: {} min)", ScmConfigManager.getAutoSaveIntervalMinutes());
    }

    public void stop() {
        if (timer != null) {
            timer.stop();
            timer = null;
        }
        SwingWorker<VersionEntry, Void> worker = activeWorker;
        if (worker != null) {
            worker.cancel(true);
            activeWorker = null;
        }
        log.debug("Auto-checkpoint stopped");
    }

    private void performAutoCheckpoint() {
        ScmContext ctx = initializer.getCurrentContext();
        if (ctx == null || ctx.isDisposed() || ctx.isReadOnly()) {
            return;
        }

        if (ctx.isAtUnprunableCapacity()) {
            log.debug("Auto-checkpoint skipped — at unprunable capacity");
            return;
        }

        if (!running.compareAndSet(false, true)) {
            log.debug("Auto-checkpoint still in progress — skipping this tick");
            return;
        }

        try {
            // Save in-memory state to disk on EDT (must be on EDT for JMeter's model)
            ActionRouter.getInstance().doActionNow(
                new ActionEvent(this, ActionEvent.ACTION_PERFORMED, ActionNames.SAVE));
        } catch (Exception e) {
            log.warn("Auto-checkpoint save failed: {}", e.getMessage());
            running.set(false);
            return;
        }

        // Snapshot off EDT — verifyDirty() + createSnapshot() both do file I/O
        SwingWorker<VersionEntry, Void> worker = new SwingWorker<>() {
            @Override
            protected VersionEntry doInBackground() throws Exception {
                if (isCancelled()) return null;
                ScmContext current = initializer.getCurrentContext();
                if (current == null || current.isDisposed() || current.isReadOnly()) {
                    return null;
                }
                // Skip if file unchanged since last checkpoint/restore
                if (!current.getDirtyTracker().verifyDirty()) {
                    log.debug("Auto-checkpoint skipped — file unchanged");
                    return null;
                }
                if (isCancelled()) return null;
                return current.createSnapshot(TriggerType.AUTO_CHECKPOINT, null);
            }

            @Override
            protected void done() {
                try {
                    if (isCancelled()) return;
                    VersionEntry entry = get();
                    if (entry != null) {
                        initializer.notifyVersionsChanged();
                        Toast.show("Auto-checkpoint: v" + entry.getVersion());
                        log.debug("Auto-checkpoint created: v{}", entry.getVersion());
                    }
                } catch (Exception e) {
                    log.warn("Auto-checkpoint failed: {}", e.getMessage());
                } finally {
                    activeWorker = null;
                    running.set(false);
                }
            }
        };
        activeWorker = worker;
        worker.execute();
    }
}
