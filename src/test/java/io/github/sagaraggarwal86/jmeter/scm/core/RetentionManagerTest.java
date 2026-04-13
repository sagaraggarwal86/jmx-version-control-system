package io.github.sagaraggarwal86.jmeter.scm.core;

import io.github.sagaraggarwal86.jmeter.scm.model.TriggerType;
import io.github.sagaraggarwal86.jmeter.scm.model.VersionEntry;
import io.github.sagaraggarwal86.jmeter.scm.model.VersionIndex;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

class RetentionManagerTest {

    @TempDir
    Path tempDir;

    private RetentionManager retentionManager;

    @BeforeEach
    void setUp() {
        retentionManager = new RetentionManager();
    }

    @Test
    void noOpWhenWithinLimit() throws IOException {
        VersionIndex index = VersionIndex.createDefault(5, ".history");
        addEntries(index, 3);

        retentionManager.pruneIfNeeded(tempDir, index);

        assertEquals(3, index.getVersions().size());
    }

    @Test
    void prunesOldestWhenOverLimit() throws IOException {
        VersionIndex index = VersionIndex.createDefault(3, ".history");
        addEntries(index, 5);
        createSnapshotFiles(5);

        retentionManager.pruneIfNeeded(tempDir, index);

        assertEquals(3, index.getVersions().size());
        // Oldest two (v1, v2) should be pruned
        assertEquals(3, index.getVersions().get(0).getVersion());
        assertEquals(4, index.getVersions().get(1).getVersion());
        assertEquals(5, index.getVersions().get(2).getVersion());
    }

    @Test
    void prunesSnapshotFiles() throws IOException {
        VersionIndex index = VersionIndex.createDefault(2, ".history");
        addEntries(index, 4);
        createSnapshotFiles(4);

        retentionManager.pruneIfNeeded(tempDir, index);

        assertFalse(Files.exists(tempDir.resolve("v001.jmxv")));
        assertFalse(Files.exists(tempDir.resolve("v002.jmxv")));
        assertTrue(Files.exists(tempDir.resolve("v003.jmxv")));
        assertTrue(Files.exists(tempDir.resolve("v004.jmxv")));
    }

    @Test
    void pruneAtExactLimit() throws IOException {
        VersionIndex index = VersionIndex.createDefault(3, ".history");
        addEntries(index, 3);

        retentionManager.pruneIfNeeded(tempDir, index);

        assertEquals(3, index.getVersions().size());
    }

    @Test
    void pruneHandlesMissingFile() throws IOException {
        VersionIndex index = VersionIndex.createDefault(1, ".history");
        addEntries(index, 3);
        // Don't create the actual files — should still prune entries

        retentionManager.pruneIfNeeded(tempDir, index);

        assertEquals(1, index.getVersions().size());
    }

    private void addEntries(VersionIndex index, int count) {
        for (int i = 1; i <= count; i++) {
            index.addVersion(new VersionEntry(
                    i, String.format("v%03d.jmxv", i), LocalDateTime.now(),
                    TriggerType.CHECKPOINT, null, "checksum" + i));
        }
    }

    private void createSnapshotFiles(int count) throws IOException {
        Files.createDirectories(tempDir);
        for (int i = 1; i <= count; i++) {
            Files.writeString(tempDir.resolve(String.format("v%03d.jmxv", i)), "content " + i);
        }
    }
}
