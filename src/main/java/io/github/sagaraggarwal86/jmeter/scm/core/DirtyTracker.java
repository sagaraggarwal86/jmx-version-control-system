package io.github.sagaraggarwal86.jmeter.scm.core;

import io.github.sagaraggarwal86.jmeter.scm.storage.FileOperations;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;

/**
 * Hybrid dirty state tracking: boolean flag for instant toolbar feedback +
 * on-demand SHA-256 checksum for accurate verification.
 */
public final class DirtyTracker {

    private static final Logger log = LoggerFactory.getLogger(DirtyTracker.class);
    private final Path jmxFile;
    private volatile boolean dirty;
    private volatile String lastKnownChecksum;

    /**
     * Creates a new dirty tracker for the given .jmx file.
     *
     * @param jmxFile the test plan file to track
     */
    public DirtyTracker(Path jmxFile) {
        this.jmxFile = Objects.requireNonNull(jmxFile, "jmxFile must not be null");
        this.dirty = false;
    }

    /**
     * Returns whether the file has been modified since the last save/checkpoint/restore.
     */
    public boolean isDirty() {
        return dirty;
    }

    /**
     * Marks the file as dirty (modified).
     */
    public void markDirty() {
        this.dirty = true;
    }

    /**
     * Resets dirty state after save, checkpoint, or restore. Updates the known checksum.
     */
    public void reset() {
        this.dirty = false;
        initializeChecksum();
    }

    /**
     * Resets dirty state with an already-computed checksum, avoiding a redundant file read.
     *
     * @param checksum the known checksum of the current file state
     */
    public void reset(String checksum) {
        this.dirty = false;
        this.lastKnownChecksum = checksum;
    }

    /**
     * Returns the last known checksum of the .jmx file.
     */
    public String getLastKnownChecksum() {
        return lastKnownChecksum;
    }

    /**
     * Performs on-demand SHA-256 comparison to verify if the file has actually changed.
     *
     * @return true if the current file content differs from the last known checksum
     */
    public boolean verifyDirty() {
        if (lastKnownChecksum == null) {
            return true;
        }
        try {
            if (!Files.exists(jmxFile)) {
                return false;
            }
            String currentChecksum = FileOperations.computeChecksum(jmxFile);
            boolean changed = !lastKnownChecksum.equals(currentChecksum);
            if (changed) {
                dirty = true;
            }
            return changed;
        } catch (IOException e) {
            log.warn("Could not verify dirty state: {}", e.getMessage());
            return dirty; // Fall back to boolean flag
        }
    }

    /**
     * Computes and stores the initial checksum.
     */
    private void initializeChecksum() {
        try {
            if (Files.exists(jmxFile)) {
                this.lastKnownChecksum = FileOperations.computeChecksum(jmxFile);
            } else {
                this.lastKnownChecksum = null;
            }
        } catch (IOException e) {
            log.warn("Could not compute initial checksum: {}", e.getMessage());
            this.lastKnownChecksum = null;
        }
    }
}
