package io.github.sagaraggarwal86.jmeter.scm.core;

import io.github.sagaraggarwal86.jmeter.scm.config.ScmConfigManager;
import io.github.sagaraggarwal86.jmeter.scm.model.VersionEntry;
import io.github.sagaraggarwal86.jmeter.scm.model.VersionIndex;
import io.github.sagaraggarwal86.jmeter.scm.storage.FileOperations;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Objects;

/**
 * FIFO pruning when version count exceeds maxRetention.
 * Removes oldest versions (files + index entries) until within limit.
 * <p>
 * Mutates the index in-memory only — caller is responsible for persisting.
 */
public final class RetentionManager {

    private static final Logger log = LoggerFactory.getLogger(RetentionManager.class);

    /**
     * Prunes oldest versions if the count exceeds maxRetention.
     * Deletes snapshot files and removes index entries in-memory.
     * Does not save the index — caller must persist after all mutations.
     *
     * @param storageDir the storage directory
     * @param index      the version index
     * @throws IOException if file deletion fails
     */
    public void pruneIfNeeded(Path storageDir, VersionIndex index) throws IOException {
        Objects.requireNonNull(storageDir, "storageDir must not be null");
        Objects.requireNonNull(index, "index must not be null");

        int maxRetention = ScmConfigManager.getMaxRetention(index);
        int excess = index.getVersions().size() - maxRetention;

        if (excess <= 0) {
            return;
        }

        log.info("Pruning {} excess version(s) (max retention: {})", excess, maxRetention);

        VersionEntry latest = index.getLatestVersion();
        int pruned = 0;
        int i = 0;
        while (pruned < excess && i < index.getVersions().size()) {
            VersionEntry candidate = index.getVersions().get(i);
            if (index.isPinned(candidate.getVersion())
                || (latest != null && candidate.getVersion() == latest.getVersion())) {
                i++;
                continue;
            }
            Path snapshotFile = storageDir.resolve(candidate.getFile());
            try {
                FileOperations.deleteSnapshot(snapshotFile);
            } catch (IOException e) {
                log.warn("Could not delete snapshot file {}: {}", candidate.getFile(), e.getMessage());
                i++;
                continue;
            }
            index.removeVersionAt(i);
            log.debug("Pruned version {}: {}", candidate.getVersion(), candidate.getFile());
            pruned++;
        }

        if (pruned < excess) {
            log.info("Could only prune {} of {} excess versions ({} pinned)",
                pruned, excess, excess - pruned);
        }
    }
}
