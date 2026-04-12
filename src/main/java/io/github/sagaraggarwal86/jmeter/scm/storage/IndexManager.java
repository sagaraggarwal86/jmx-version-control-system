package io.github.sagaraggarwal86.jmeter.scm.storage;

import io.github.sagaraggarwal86.jmeter.scm.config.ScmConfigManager;
import io.github.sagaraggarwal86.jmeter.scm.model.TriggerType;
import io.github.sagaraggarwal86.jmeter.scm.model.VersionEntry;
import io.github.sagaraggarwal86.jmeter.scm.model.VersionIndex;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Manages index.json: CRUD operations, atomic writes, and self-healing recovery.
 */
public final class IndexManager {

    private static final Logger log = LoggerFactory.getLogger(IndexManager.class);
    private static final String INDEX_FILE = "index.json";
    private static final String INDEX_TMP = "index.json.tmp";
    private static final String INDEX_BAK = "index.json.bak";
    private static final Pattern JMXV_PATTERN = Pattern.compile("v(\\d+)\\.jmxv");

    /**
     * Loads the version index from the storage directory. Performs self-healing if corrupt.
     *
     * @param storageDir the storage directory containing index.json
     * @return the loaded (or recovered) version index
     * @throws IOException if both load and recovery fail
     */
    public VersionIndex load(Path storageDir) throws IOException {
        Objects.requireNonNull(storageDir, "storageDir must not be null");

        Path indexFile = storageDir.resolve(INDEX_FILE);
        if (!Files.exists(indexFile)) {
            log.debug("No index.json found, creating default index");
            VersionIndex index = VersionIndex.createDefault(
                    ScmConfigManager.getGlobalMaxRetention(),
                    ScmConfigManager.getGlobalStorageLocation());
            save(storageDir, index);
            return index;
        }

        try {
            VersionIndex index = FileOperations.objectMapper().readValue(indexFile.toFile(), VersionIndex.class);
            selfHeal(storageDir, index);
            return index;
        } catch (Exception e) {
            log.warn("index.json corrupt, attempting recovery: {}", e.getMessage());
            return rebuildFromDisk(storageDir);
        }
    }

    /**
     * Atomically saves the version index to index.json.
     * Writes to index.json.tmp first, then performs atomic move.
     *
     * @param storageDir the storage directory
     * @param index      the version index to save
     * @throws IOException if the save fails
     */
    public void save(Path storageDir, VersionIndex index) throws IOException {
        Objects.requireNonNull(storageDir, "storageDir must not be null");
        Objects.requireNonNull(index, "index must not be null");

        Files.createDirectories(storageDir);
        Path indexFile = storageDir.resolve(INDEX_FILE);
        Path tmpFile = storageDir.resolve(INDEX_TMP);

        FileOperations.objectMapper().writeValue(tmpFile.toFile(), index);
        FileOperations.atomicMove(tmpFile, indexFile);
        log.debug("Saved index.json with {} versions", index.getVersions().size());
    }

    /**
     * Adds a new version entry to the index and saves atomically.
     *
     * @param storageDir the storage directory
     * @param index      the version index
     * @param entry      the new version entry
     * @throws IOException if the save fails
     */
    public void addVersion(Path storageDir, VersionIndex index, VersionEntry entry) throws IOException {
        Objects.requireNonNull(entry, "entry must not be null");
        index.getVersions().add(entry);
        save(storageDir, index);
    }

    /**
     * Removes a version entry from the index and saves atomically.
     *
     * @param storageDir    the storage directory
     * @param index         the version index
     * @param versionNumber the version number to remove
     * @throws IOException if the save fails
     */
    public void removeVersion(Path storageDir, VersionIndex index, int versionNumber) throws IOException {
        index.getVersions().removeIf(e -> e.getVersion() == versionNumber);
        index.unpin(versionNumber);
        save(storageDir, index);
    }

    /**
     * Self-healing: validates index entries against actual .jmxv files on disk.
     * Removes entries whose files are missing. Existence-only check (R7).
     */
    private void selfHeal(Path storageDir, VersionIndex index) throws IOException {
        List<VersionEntry> toRemove = new ArrayList<>();
        for (VersionEntry entry : index.getVersions()) {
            Path snapshotFile = storageDir.resolve(entry.getFile());
            if (!Files.exists(snapshotFile)) {
                log.warn("Self-healing: removing entry for missing file: {}", entry.getFile());
                toRemove.add(entry);
            }
        }
        if (!toRemove.isEmpty()) {
            index.getVersions().removeAll(toRemove);
            save(storageDir, index);
            log.info("Self-healing: removed {} entries with missing files", toRemove.size());
        }
    }

    /**
     * Rebuilds index.json from .jmxv filenames on disk after corruption.
     * Renames corrupt index.json to index.json.bak.
     */
    private VersionIndex rebuildFromDisk(Path storageDir) throws IOException {
        Path indexFile = storageDir.resolve(INDEX_FILE);
        Path bakFile = storageDir.resolve(INDEX_BAK);

        if (Files.exists(indexFile)) {
            Files.move(indexFile, bakFile, StandardCopyOption.REPLACE_EXISTING);
            log.info("Renamed corrupt index.json to index.json.bak");
        }

        VersionIndex index = VersionIndex.createDefault(
                ScmConfigManager.getGlobalMaxRetention(),
                ScmConfigManager.getGlobalStorageLocation());

        try (DirectoryStream<Path> stream = Files.newDirectoryStream(storageDir, "v*.jmxv")) {
            List<VersionEntry> entries = new ArrayList<>();
            for (Path file : stream) {
                String fileName = file.getFileName().toString();
                Matcher matcher = JMXV_PATTERN.matcher(fileName);
                if (matcher.matches()) {
                    int version = Integer.parseInt(matcher.group(1));
                    LocalDateTime timestamp = LocalDateTime.now(); // Best effort — use file time
                    try {
                        timestamp = LocalDateTime.ofInstant(
                                Files.getLastModifiedTime(file).toInstant(),
                                java.time.ZoneId.systemDefault());
                    } catch (IOException ignored) {
                        // Use current time as fallback
                    }
                    String checksum;
                    try {
                        checksum = FileOperations.computeChecksum(file);
                    } catch (IOException e) {
                        checksum = "unknown";
                    }
                    entries.add(new VersionEntry(version, fileName, timestamp,
                            TriggerType.CHECKPOINT, "[recovered]", checksum));
                }
            }
            entries.sort(java.util.Comparator.comparingInt(VersionEntry::getVersion));
            index.getVersions().addAll(entries);
        }

        save(storageDir, index);
        log.info("Rebuilt index.json from disk with {} entries", index.getVersions().size());
        return index;
    }
}
