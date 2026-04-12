package io.github.sagaraggarwal86.jmeter.scm.core;

import io.github.sagaraggarwal86.jmeter.scm.model.TriggerType;
import io.github.sagaraggarwal86.jmeter.scm.model.VersionEntry;
import io.github.sagaraggarwal86.jmeter.scm.model.VersionIndex;
import io.github.sagaraggarwal86.jmeter.scm.storage.FileOperations;
import io.github.sagaraggarwal86.jmeter.scm.storage.IndexManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.Objects;

/**
 * Creates, restores, and deletes version snapshots.
 */
public final class SnapshotEngine {

    private static final Logger log = LoggerFactory.getLogger(SnapshotEngine.class);

    private final IndexManager indexManager;
    private final RetentionManager retentionManager;

    public SnapshotEngine(IndexManager indexManager, RetentionManager retentionManager) {
        this.indexManager = Objects.requireNonNull(indexManager, "indexManager must not be null");
        this.retentionManager = Objects.requireNonNull(retentionManager, "retentionManager must not be null");
    }

    /**
     * Creates a new version snapshot of the .jmx file.
     * Performs deduplication check (R8) and FIFO pruning.
     *
     * @param jmxFile    the .jmx file to snapshot
     * @param storageDir the storage directory
     * @param index      the version index
     * @param trigger    what caused the snapshot
     * @param note       optional user note (may be null)
     * @return the new version entry, or null if skipped (deduplication)
     * @throws IOException if snapshot creation fails
     */
    public VersionEntry createSnapshot(Path jmxFile, Path storageDir, VersionIndex index,
                                       TriggerType trigger, String note) throws IOException {
        Objects.requireNonNull(jmxFile, "jmxFile must not be null");
        Objects.requireNonNull(storageDir, "storageDir must not be null");
        Objects.requireNonNull(index, "index must not be null");
        Objects.requireNonNull(trigger, "trigger must not be null");

        // Synchronize on index to prevent concurrent save + auto-save races
        synchronized (index) {
            String checksum = FileOperations.computeChecksum(jmxFile);

            // Dedup: skip auto-checkpoints if unchanged. CHECKPOINT and RESTORE always create.
            VersionEntry latest = index.getLatestVersion();
            if (trigger == TriggerType.AUTO_CHECKPOINT
                    && latest != null && checksum.equals(latest.getChecksum())) {
                log.debug("Skipping {} — identical to latest version {}", trigger, latest.getVersion());
                return null;
            }

            retentionManager.pruneIfNeeded(storageDir, index);

            int versionNumber = index.getNextVersionNumber();
            String fileName = FileOperations.snapshotFileName(versionNumber);
            FileOperations.createSnapshot(jmxFile, storageDir, fileName);

            VersionEntry entry = new VersionEntry(
                    versionNumber, fileName, LocalDateTime.now(), trigger, note, checksum);
            indexManager.addVersion(storageDir, index, entry);

            log.info("Created snapshot v{} ({})", versionNumber, trigger);
            return entry;
        }
    }

    /**
     * Restores a version snapshot. Auto-snapshots current state first (safety net).
     *
     * @param jmxFile       the .jmx file to restore to
     * @param storageDir    the storage directory
     * @param index         the version index
     * @param versionNumber the version to restore
     * @throws IOException              if restore fails
     * @throws IllegalArgumentException if version not found
     */
    public void restore(Path jmxFile, Path storageDir, VersionIndex index,
                        int versionNumber) throws IOException {
        Objects.requireNonNull(jmxFile, "jmxFile must not be null");
        Objects.requireNonNull(storageDir, "storageDir must not be null");
        Objects.requireNonNull(index, "index must not be null");

        VersionEntry target = index.getVersions().stream()
                .filter(e -> e.getVersion() == versionNumber)
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Version " + versionNumber + " not found"));

        Path snapshotPath = storageDir.resolve(target.getFile());
        if (!Files.exists(snapshotPath)) {
            throw new IOException("Snapshot file missing: " + target.getFile()
                    + ". The version entry exists in the index but the file was deleted from disk.");
        }

        createSnapshot(jmxFile, storageDir, index, TriggerType.RESTORE, "Replaced by v" + versionNumber);

        FileOperations.atomicRestore(snapshotPath, jmxFile);

        log.info("Restored to version {}", versionNumber);
    }

    /**
     * Deletes a version snapshot. Blocks deletion of the latest version (R5).
     *
     * @param storageDir    the storage directory
     * @param index         the version index
     * @param versionNumber the version to delete
     * @throws IOException              if deletion fails
     * @throws IllegalStateException    if attempting to delete the latest version
     * @throws IllegalArgumentException if version not found
     */
    public void deleteVersion(Path storageDir, VersionIndex index, int versionNumber) throws IOException {
        Objects.requireNonNull(storageDir, "storageDir must not be null");
        Objects.requireNonNull(index, "index must not be null");

        VersionEntry latest = index.getLatestVersion();
        if (latest != null && latest.getVersion() == versionNumber) {
            throw new IllegalStateException("Cannot delete the latest version (v" + versionNumber + ")");
        }
        if (index.isPinned(versionNumber)) {
            throw new IllegalStateException("Cannot delete pinned version (v" + versionNumber + "). Unpin it first.");
        }

        VersionEntry target = index.getVersions().stream()
                .filter(e -> e.getVersion() == versionNumber)
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Version " + versionNumber + " not found"));

        Path snapshotFile = storageDir.resolve(target.getFile());
        if (Files.exists(snapshotFile)) {
            FileOperations.deleteSnapshot(snapshotFile);
        } else {
            log.warn("Snapshot file already missing: {} — removing index entry only", target.getFile());
        }
        indexManager.removeVersion(storageDir, index, versionNumber);

        log.info("Deleted version {}", versionNumber);
    }
}
