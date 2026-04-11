package io.github.sagaraggarwal86.jmeter.scm.core;

import io.github.sagaraggarwal86.jmeter.scm.config.ScmConfigManager;
import io.github.sagaraggarwal86.jmeter.scm.model.TriggerType;
import io.github.sagaraggarwal86.jmeter.scm.model.VersionEntry;
import io.github.sagaraggarwal86.jmeter.scm.model.VersionIndex;
import io.github.sagaraggarwal86.jmeter.scm.storage.IndexManager;
import io.github.sagaraggarwal86.jmeter.scm.storage.LockManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Objects;

/**
 * Per-test-plan lifecycle state: manages lock, dirty tracker, index reference, and storage directory.
 * Disposed when a new test plan is opened. Prevents state leaks across plans (R1).
 */
public final class ScmContext {

    private static final Logger log = LoggerFactory.getLogger(ScmContext.class);

    private final Path jmxFile;
    private final Path storageDir;
    private final IndexManager indexManager;
    private final LockManager lockManager;
    private final SnapshotEngine snapshotEngine;
    private final RetentionManager retentionManager;
    private final DirtyTracker dirtyTracker;
    private VersionIndex versionIndex;
    private boolean readOnly;
    private boolean disposed;

    /**
     * Creates a new ScmContext for the given test plan file.
     *
     * @param jmxFile      the test plan file path
     * @param indexManager the index manager
     * @param lockManager  the lock manager
     */
    public ScmContext(Path jmxFile, IndexManager indexManager, LockManager lockManager) {
        this.jmxFile = Objects.requireNonNull(jmxFile, "jmxFile must not be null");
        this.indexManager = Objects.requireNonNull(indexManager, "indexManager must not be null");
        this.lockManager = Objects.requireNonNull(lockManager, "lockManager must not be null");
        this.retentionManager = new RetentionManager();
        this.snapshotEngine = new SnapshotEngine(indexManager, retentionManager);
        this.dirtyTracker = new DirtyTracker(jmxFile);
        this.storageDir = resolveStorageDir(jmxFile);

        this.readOnly = false;
        this.disposed = false;
    }

    /**
     * Resolves the storage directory path relative to the .jmx file.
     */
    private static Path resolveStorageDir(Path jmxFile) {
        String storageLocation = ScmConfigManager.getGlobalStorageLocation();
        Path parent = jmxFile.getParent();
        if (parent == null) {
            parent = Path.of(".");
        }
        return parent.resolve(storageLocation);
    }

    /**
     * Initializes the context: loads index, acquires lock, performs self-healing.
     *
     * @throws IOException if initialization fails
     */
    public void initialize() throws IOException {
        checkNotDisposed();

        this.versionIndex = indexManager.load(storageDir);

        if (!lockManager.acquire(storageDir)) {
            this.readOnly = true;
            log.info("Test plan locked by another instance — read-only mode");
        }

        log.info("SCM context initialized for {} ({} versions, {})",
                jmxFile.getFileName(), versionIndex.getVersions().size(),
                readOnly ? "read-only" : "read-write");
    }

    /**
     * Disposes the context: releases lock, clears state. Called when switching test plans.
     */
    public void dispose() {
        if (disposed) {
            return;
        }
        disposed = true;
        if (!readOnly) {
            lockManager.release(storageDir);
        }
        log.debug("SCM context disposed for {}", jmxFile.getFileName());
    }

    public Path getJmxFile() {
        return jmxFile;
    }

    public Path getStorageDir() {
        return storageDir;
    }

    public VersionIndex getVersionIndex() {
        return versionIndex;
    }

    public SnapshotEngine getSnapshotEngine() {
        return snapshotEngine;
    }

    public DirtyTracker getDirtyTracker() {
        return dirtyTracker;
    }

    public IndexManager getIndexManager() {
        return indexManager;
    }

    public boolean isReadOnly() {
        return readOnly;
    }

    public boolean isDisposed() {
        return disposed;
    }

    /**
     * Creates a checkpoint snapshot with optional note. Resets dirty tracker if snapshot was created.
     *
     * @param note optional user note (may be null)
     * @return the new version entry, or null if skipped (deduplication)
     * @throws IOException if snapshot creation fails
     */
    public VersionEntry createCheckpoint(String note) throws IOException {
        checkNotDisposed();
        VersionEntry entry = snapshotEngine.createSnapshot(
                jmxFile, storageDir, versionIndex, TriggerType.CHECKPOINT, note);
        if (entry != null) {
            dirtyTracker.reset(entry.getChecksum());
        }
        return entry;
    }

    /**
     * Restores a version snapshot. Auto-snapshots current state first, then resets dirty tracker.
     *
     * @param versionNumber the version to restore
     * @throws IOException if restore fails
     */
    public void restore(int versionNumber) throws IOException {
        checkNotDisposed();
        snapshotEngine.restore(jmxFile, storageDir, versionIndex, versionNumber);
        dirtyTracker.reset();
    }

    /**
     * Deletes a version snapshot. Blocks deletion of the latest version.
     *
     * @param versionNumber the version to delete
     * @throws IOException if deletion fails
     */
    public void deleteVersion(int versionNumber) throws IOException {
        checkNotDisposed();
        snapshotEngine.deleteVersion(storageDir, versionIndex, versionNumber);
    }

    private void checkNotDisposed() {
        if (disposed) {
            throw new IllegalStateException("ScmContext has been disposed");
        }
    }
}
