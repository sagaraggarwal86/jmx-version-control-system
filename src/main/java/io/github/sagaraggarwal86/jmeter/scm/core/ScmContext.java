package io.github.sagaraggarwal86.jmeter.scm.core;

import io.github.sagaraggarwal86.jmeter.scm.config.ScmConfigManager;
import io.github.sagaraggarwal86.jmeter.scm.model.LockInfo;
import io.github.sagaraggarwal86.jmeter.scm.model.TriggerType;
import io.github.sagaraggarwal86.jmeter.scm.model.VersionEntry;
import io.github.sagaraggarwal86.jmeter.scm.model.VersionIndex;
import io.github.sagaraggarwal86.jmeter.scm.storage.AuditLogger;
import io.github.sagaraggarwal86.jmeter.scm.storage.FileOperations;
import io.github.sagaraggarwal86.jmeter.scm.storage.IndexManager;
import io.github.sagaraggarwal86.jmeter.scm.storage.LockManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
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
     * Resolves the per-plan storage directory: {@code .history/<jmx-stem>/}
     * where jmx-stem is the filename without extension (e.g., "test1" for "test1.jmx").
     */
    private static Path resolveStorageDir(Path jmxFile) {
        String storageLocation = ScmConfigManager.getGlobalStorageLocation();
        Path parent = jmxFile.getParent();
        if (parent == null) {
            parent = Path.of(".");
        }
        String stem = jmxFile.getFileName().toString().replaceFirst("\\.[^.]+$", "");
        return parent.resolve(storageLocation).resolve(stem);
    }

    /**
     * Initializes the context: migrates legacy layout if needed, loads index,
     * acquires lock, performs self-healing.
     *
     * @throws IOException if initialization fails
     */
    public void initialize() throws IOException {
        checkNotDisposed();

        migrateLegacyLayout();

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
     * Auto-migrates legacy flat .history/ layout (pre-per-plan subdirectories).
     * Detects: .history/index.json exists but .history/&lt;stem&gt;/ does not.
     * Moves all files (index.json, .lock, v*.jmxv) into the per-plan subdirectory.
     */
    private void migrateLegacyLayout() {
        Path historyRoot = storageDir.getParent(); // .history/
        if (historyRoot == null) return;

        Path legacyIndex = historyRoot.resolve("index.json");
        if (!Files.exists(legacyIndex) || Files.exists(storageDir)) {
            return; // No legacy layout, or already migrated
        }

        log.info("Migrating legacy .history/ layout to per-plan subdirectory: {}", storageDir);
        try {
            Files.createDirectories(storageDir);

            // Move all files from .history/ into .history/<stem>/
            try (var stream = Files.list(historyRoot)) {
                stream.filter(Files::isRegularFile).forEach(file -> {
                    try {
                        Files.move(file, storageDir.resolve(file.getFileName()),
                                StandardCopyOption.REPLACE_EXISTING);
                    } catch (IOException e) {
                        log.warn("Could not migrate {}: {}", file.getFileName(), e.getMessage());
                    }
                });
            }
            log.info("Legacy migration complete");
        } catch (IOException e) {
            log.warn("Legacy migration failed: {}", e.getMessage());
        }
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

    /**
     * Returns the current lock info, or null if no lock exists.
     */
    public LockInfo getLockInfo() {
        return lockManager.getLockInfo(storageDir);
    }

    /**
     * Force-releases the lock held by another process and re-acquires it.
     * Clears read-only mode on success.
     *
     * @return true if the lock was force-released and re-acquired
     */
    public boolean forceReleaseLock() {
        checkNotDisposed();
        boolean success = lockManager.forceRelease(storageDir);
        if (success) {
            readOnly = false;
            AuditLogger.logForceReleaseLock(storageDir);
            log.info("Lock force-released, switching to read-write mode");
        }
        return success;
    }

    /**
     * Attempts to politely acquire the lock (succeeds if missing or stale).
     * Does not force-release an active lock held by another instance.
     *
     * @return true if lock acquired, false if held by another non-stale process
     */
    public boolean tryAcquireLock() {
        checkNotDisposed();
        if (lockManager.acquire(storageDir)) {
            readOnly = false;
            log.info("Lock acquired, switching to read-write mode");
            return true;
        }
        readOnly = true;
        log.info("Lock held by another instance, switching to read-only mode");
        return false;
    }

    public boolean isDisposed() {
        return disposed;
    }

    /**
     * Creates a version snapshot with the given trigger type. Resets dirty tracker if snapshot was created.
     *
     * @param trigger what caused the snapshot
     * @param note    optional user note (may be null)
     * @return the new version entry, or null if skipped (deduplication)
     * @throws IOException if snapshot creation fails
     */
    public VersionEntry createSnapshot(TriggerType trigger, String note) throws IOException {
        checkNotDisposed();
        ensureWriteLock();
        VersionEntry entry = snapshotEngine.createSnapshot(
                jmxFile, storageDir, versionIndex, trigger, note);
        if (entry != null) {
            dirtyTracker.reset(entry.getChecksum());
            if (trigger == TriggerType.CHECKPOINT) {
                AuditLogger.logCheckpoint(storageDir, entry.getVersion(), note);
            } else if (trigger == TriggerType.AUTO_CHECKPOINT) {
                AuditLogger.logAutoCheckpoint(storageDir, entry.getVersion());
            }
        }
        return entry;
    }

    /**
     * Creates a checkpoint snapshot with optional note.
     */
    public VersionEntry createCheckpoint(String note) throws IOException {
        return createSnapshot(TriggerType.CHECKPOINT, note);
    }

    /**
     * Restores a version snapshot. Auto-snapshots current state first, then resets dirty tracker.
     *
     * @param versionNumber the version to restore
     * @throws IOException if restore fails
     */
    public void restore(int versionNumber) throws IOException {
        checkNotDisposed();
        ensureWriteLock();
        snapshotEngine.restore(jmxFile, storageDir, versionIndex, versionNumber);
        dirtyTracker.reset();
        AuditLogger.logRestore(storageDir, versionNumber);
    }

    /**
     * Deletes a version snapshot. Blocks deletion of the latest version.
     *
     * @param versionNumber the version to delete
     * @throws IOException if deletion fails
     */
    public void deleteVersion(int versionNumber) throws IOException {
        checkNotDisposed();
        ensureWriteLock();
        snapshotEngine.deleteVersion(storageDir, versionIndex, versionNumber);
        AuditLogger.logDelete(storageDir, versionNumber);
    }

    /**
     * Clears version history. Preserves kept (pinned) versions and the latest version.
     *
     * @return number of versions deleted
     * @throws IOException if deletion fails
     */
    public int clearHistory() throws IOException {
        checkNotDisposed();
        ensureWriteLock();

        VersionEntry latest = versionIndex.getLatestVersion();
        List<VersionEntry> toDelete = versionIndex.getVersions().stream()
                .filter(e -> !versionIndex.isPinned(e.getVersion()))
                .filter(e -> latest == null || e.getVersion() != latest.getVersion())
                .toList();

        for (VersionEntry entry : toDelete) {
            Path snapshotFile = storageDir.resolve(entry.getFile());
            try {
                FileOperations.deleteSnapshot(snapshotFile);
            } catch (IOException e) {
                log.warn("Could not delete snapshot {}: {}", entry.getFile(), e.getMessage());
            }
        }

        versionIndex.getVersions().removeAll(toDelete);
        indexManager.save(storageDir, versionIndex);
        AuditLogger.logClearHistory(storageDir, toDelete.size());
        return toDelete.size();
    }

    /**
     * Prunes excess versions according to current maxRetention setting.
     * Delegates to RetentionManager (FIFO, skips pinned + latest).
     *
     * @return number of versions pruned
     * @throws IOException if deletion or index save fails
     */
    public int pruneExcessVersions() throws IOException {
        checkNotDisposed();
        ensureWriteLock();
        int before = versionIndex.getVersions().size();
        retentionManager.pruneIfNeeded(storageDir, versionIndex);
        indexManager.save(storageDir, versionIndex);
        int pruned = before - versionIndex.getVersions().size();
        if (pruned > 0) {
            AuditLogger.logRetentionPrune(storageDir, pruned);
        }
        return pruned;
    }

    private void checkNotDisposed() {
        if (disposed) {
            throw new IllegalStateException("ScmContext has been disposed");
        }
    }

    /**
     * Re-validates the lock before a write operation. If the lock file was deleted
     * or stolen, attempts to re-acquire. Switches to read-only if another instance
     * now holds it.
     *
     * @throws IOException if the lock cannot be re-acquired (another instance holds it)
     */
    private void ensureWriteLock() throws IOException {
        if (readOnly) {
            throw new IOException("Cannot write — read-only mode");
        }
        if (!lockManager.acquire(storageDir)) {
            readOnly = true;
            log.warn("Lock lost — another instance now holds it. Switching to read-only.");
            throw new IOException("Lock lost to another instance. Switched to read-only mode.");
        }
    }
}
