package io.github.sagaraggarwal86.jmeter.scm.core;

import io.github.sagaraggarwal86.jmeter.scm.model.TriggerType;
import io.github.sagaraggarwal86.jmeter.scm.model.VersionEntry;
import io.github.sagaraggarwal86.jmeter.scm.model.VersionIndex;
import io.github.sagaraggarwal86.jmeter.scm.storage.IndexManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class SnapshotEngineTest {

    @TempDir
    Path tempDir;

    private IndexManager indexManager;
    private RetentionManager retentionManager;
    private SnapshotEngine snapshotEngine;
    private Path storageDir;
    private Path jmxFile;

    @BeforeEach
    void setUp() throws IOException {
        indexManager = new IndexManager();
        retentionManager = new RetentionManager();
        snapshotEngine = new SnapshotEngine(indexManager, retentionManager);
        storageDir = tempDir.resolve(".history");
        jmxFile = tempDir.resolve("test.jmx");
        Files.writeString(jmxFile, "<jmeterTestPlan>initial</jmeterTestPlan>");
    }

    @Test
    void createSnapshotCreatesFileAndEntry() throws IOException {
        VersionIndex index = VersionIndex.createDefault(20, ".history");

        VersionEntry entry = snapshotEngine.createSnapshot(jmxFile, storageDir, index,
                TriggerType.CHECKPOINT, null);

        assertNotNull(entry);
        assertEquals(1, entry.getVersion());
        assertEquals("v001.jmxv", entry.getFile());
        assertEquals(TriggerType.CHECKPOINT, entry.getTrigger());
        assertNull(entry.getNote());
        assertTrue(Files.exists(storageDir.resolve("v001.jmxv")));
        assertEquals(1, index.getVersions().size());
    }

    @Test
    void createSnapshotWithNote() throws IOException {
        VersionIndex index = VersionIndex.createDefault(20, ".history");

        VersionEntry entry = snapshotEngine.createSnapshot(jmxFile, storageDir, index,
                TriggerType.CHECKPOINT, "Before load test");

        assertNotNull(entry);
        assertEquals("Before load test", entry.getNote());
        assertEquals(TriggerType.CHECKPOINT, entry.getTrigger());
    }

    @Test
    void deduplicationSkipsIdenticalContent() throws IOException {
        VersionIndex index = VersionIndex.createDefault(20, ".history");

        VersionEntry first = snapshotEngine.createSnapshot(jmxFile, storageDir, index,
                TriggerType.AUTO_CHECKPOINT, null);
        assertNotNull(first);

        // Same content — AUTO_CHECKPOINT should skip
        VersionEntry second = snapshotEngine.createSnapshot(jmxFile, storageDir, index,
                TriggerType.AUTO_CHECKPOINT, null);
        assertNull(second);
        assertEquals(1, index.getVersions().size());
    }

    @Test
    void manualCheckpointAlwaysCreatesVersion() throws IOException {
        VersionIndex index = VersionIndex.createDefault(20, ".history");

        VersionEntry first = snapshotEngine.createSnapshot(jmxFile, storageDir, index,
                TriggerType.CHECKPOINT, null);
        assertNotNull(first);

        // Same content — manual CHECKPOINT should still create
        VersionEntry second = snapshotEngine.createSnapshot(jmxFile, storageDir, index,
                TriggerType.CHECKPOINT, null);
        assertNotNull(second);
        assertEquals(2, index.getVersions().size());
    }

    @Test
    void deduplicationAllowsDifferentContent() throws IOException {
        VersionIndex index = VersionIndex.createDefault(20, ".history");

        snapshotEngine.createSnapshot(jmxFile, storageDir, index, TriggerType.CHECKPOINT, null);

        Files.writeString(jmxFile, "<jmeterTestPlan>modified</jmeterTestPlan>");

        VersionEntry second = snapshotEngine.createSnapshot(jmxFile, storageDir, index,
                TriggerType.CHECKPOINT, null);
        assertNotNull(second);
        assertEquals(2, index.getVersions().size());
    }

    @Test
    void versionNumberIncrementsGlobally() throws IOException {
        VersionIndex index = VersionIndex.createDefault(20, ".history");

        snapshotEngine.createSnapshot(jmxFile, storageDir, index, TriggerType.CHECKPOINT, null);
        Files.writeString(jmxFile, "v2");
        snapshotEngine.createSnapshot(jmxFile, storageDir, index, TriggerType.CHECKPOINT, null);
        Files.writeString(jmxFile, "v3");
        snapshotEngine.createSnapshot(jmxFile, storageDir, index, TriggerType.CHECKPOINT, null);

        assertEquals(3, index.getVersions().size());
        assertEquals(1, index.getVersions().get(0).getVersion());
        assertEquals(2, index.getVersions().get(1).getVersion());
        assertEquals(3, index.getVersions().get(2).getVersion());
    }

    @Test
    void restoreAutoSnapshotsCurrentState() throws IOException {
        VersionIndex index = VersionIndex.createDefault(20, ".history");

        snapshotEngine.createSnapshot(jmxFile, storageDir, index, TriggerType.CHECKPOINT, null);
        Files.writeString(jmxFile, "modified content");
        snapshotEngine.createSnapshot(jmxFile, storageDir, index, TriggerType.CHECKPOINT, null);

        // Modify file again so auto-snapshot before restore isn't a dedup skip
        Files.writeString(jmxFile, "further changes");

        // Restore to v1
        snapshotEngine.restore(jmxFile, storageDir, index, 1);

        // Should have 3 versions now (auto-snapshot of "further changes" before restore)
        assertEquals(3, index.getVersions().size());
        // File should contain original content
        String content = Files.readString(jmxFile);
        assertEquals("<jmeterTestPlan>initial</jmeterTestPlan>", content);
    }

    @Test
    void restoreThrowsForMissingVersion() {
        VersionIndex index = VersionIndex.createDefault(20, ".history");

        assertThrows(IllegalArgumentException.class, () ->
                snapshotEngine.restore(jmxFile, storageDir, index, 99));
    }

    @Test
    void deleteVersionRemovesFileAndEntry() throws IOException {
        VersionIndex index = VersionIndex.createDefault(20, ".history");

        snapshotEngine.createSnapshot(jmxFile, storageDir, index, TriggerType.CHECKPOINT, null);
        Files.writeString(jmxFile, "v2");
        snapshotEngine.createSnapshot(jmxFile, storageDir, index, TriggerType.CHECKPOINT, null);

        snapshotEngine.deleteVersion(storageDir, index, 1);

        assertEquals(1, index.getVersions().size());
        assertFalse(Files.exists(storageDir.resolve("v001.jmxv")));
        assertTrue(Files.exists(storageDir.resolve("v002.jmxv")));
    }

    @Test
    void deleteLatestVersionBlocked() throws IOException {
        VersionIndex index = VersionIndex.createDefault(20, ".history");

        snapshotEngine.createSnapshot(jmxFile, storageDir, index, TriggerType.CHECKPOINT, null);

        assertThrows(IllegalStateException.class, () ->
                snapshotEngine.deleteVersion(storageDir, index, 1));
    }

    @Test
    void deleteNonExistentVersionThrows() {
        VersionIndex index = VersionIndex.createDefault(20, ".history");

        assertThrows(IllegalArgumentException.class, () ->
                snapshotEngine.deleteVersion(storageDir, index, 99));
    }
}
