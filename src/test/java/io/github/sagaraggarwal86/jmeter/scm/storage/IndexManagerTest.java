package io.github.sagaraggarwal86.jmeter.scm.storage;

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

class IndexManagerTest {

    @TempDir
    Path tempDir;

    private IndexManager indexManager;

    @BeforeEach
    void setUp() {
        indexManager = new IndexManager();
    }

    @Test
    void loadCreatesDefaultIndexWhenNoneExists() throws IOException {
        VersionIndex index = indexManager.load(tempDir);

        assertNotNull(index);
        assertEquals(1, index.getSchemaVersion());
        assertTrue(index.getVersions().isEmpty());
        assertTrue(Files.exists(tempDir.resolve("index.json")));
    }

    @Test
    void saveAndLoadRoundTrip() throws IOException {
        VersionIndex index = VersionIndex.createDefault(20, ".history");
        index.getVersions().add(new VersionEntry(1, "v001.jmxv", LocalDateTime.now(),
                TriggerType.SAVE, "test note", "abc123"));

        indexManager.save(tempDir, index);

        // Create the referenced .jmxv file so self-healing doesn't remove the entry
        Files.writeString(tempDir.resolve("v001.jmxv"), "content");

        VersionIndex loaded = indexManager.load(tempDir);
        assertEquals(1, loaded.getVersions().size());
        assertEquals("v001.jmxv", loaded.getVersions().get(0).getFile());
        assertEquals("test note", loaded.getVersions().get(0).getNote());
        assertEquals(TriggerType.SAVE, loaded.getVersions().get(0).getTrigger());
    }

    @Test
    void addVersionAppendsAndSaves() throws IOException {
        VersionIndex index = indexManager.load(tempDir);

        VersionEntry entry = new VersionEntry(1, "v001.jmxv", LocalDateTime.now(),
                TriggerType.CHECKPOINT, null, "hash1");
        indexManager.addVersion(tempDir, index, entry);

        assertEquals(1, index.getVersions().size());

        // Create the referenced .jmxv file so self-healing doesn't remove the entry
        Files.writeString(tempDir.resolve("v001.jmxv"), "content");

        // Verify persisted
        VersionIndex reloaded = indexManager.load(tempDir);
        assertEquals(1, reloaded.getVersions().size());
    }

    @Test
    void removeVersionDeletesEntryAndSaves() throws IOException {
        VersionIndex index = indexManager.load(tempDir);
        index.getVersions().add(new VersionEntry(1, "v001.jmxv", LocalDateTime.now(),
                TriggerType.SAVE, null, "a"));
        index.getVersions().add(new VersionEntry(2, "v002.jmxv", LocalDateTime.now(),
                TriggerType.SAVE, null, "b"));
        indexManager.save(tempDir, index);

        indexManager.removeVersion(tempDir, index, 1);

        assertEquals(1, index.getVersions().size());
        assertEquals(2, index.getVersions().get(0).getVersion());
    }

    @Test
    void selfHealingRemovesMissingFiles() throws IOException {
        // Create index with entry pointing to non-existent file
        VersionIndex index = VersionIndex.createDefault(20, ".history");
        index.getVersions().add(new VersionEntry(1, "v001.jmxv", LocalDateTime.now(),
                TriggerType.SAVE, null, "abc"));
        index.getVersions().add(new VersionEntry(2, "v002.jmxv", LocalDateTime.now(),
                TriggerType.SAVE, null, "def"));
        indexManager.save(tempDir, index);

        // Only create v002, not v001
        Files.writeString(tempDir.resolve("v002.jmxv"), "content");

        VersionIndex loaded = indexManager.load(tempDir);
        assertEquals(1, loaded.getVersions().size());
        assertEquals(2, loaded.getVersions().get(0).getVersion());
    }

    @Test
    void corruptIndexRecoveredFromDisk() throws IOException {
        // Write corrupt index.json
        Files.writeString(tempDir.resolve("index.json"), "this is not valid json{{{");

        // Create some .jmxv files
        Files.writeString(tempDir.resolve("v001.jmxv"), "content 1");
        Files.writeString(tempDir.resolve("v003.jmxv"), "content 3");

        VersionIndex recovered = indexManager.load(tempDir);

        assertNotNull(recovered);
        assertEquals(2, recovered.getVersions().size());
        assertEquals(1, recovered.getVersions().get(0).getVersion());
        assertEquals(3, recovered.getVersions().get(1).getVersion());

        // Corrupt file should be renamed to .bak
        assertTrue(Files.exists(tempDir.resolve("index.json.bak")));
    }

    @Test
    void unknownJsonPropertiesIgnored() throws IOException {
        // R11: FAIL_ON_UNKNOWN_PROPERTIES = false
        String json = """
                {
                  "schemaVersion": 1,
                  "maxRetention": 20,
                  "storageLocation": ".history",
                  "futureField": "should be ignored",
                  "versions": []
                }
                """;
        Files.writeString(tempDir.resolve("index.json"), json);

        VersionIndex loaded = indexManager.load(tempDir);
        assertNotNull(loaded);
        assertEquals(1, loaded.getSchemaVersion());
    }

    @Test
    void saveRejectsNull() {
        assertThrows(NullPointerException.class, () -> indexManager.save(null, VersionIndex.createDefault(20, ".")));
        assertThrows(NullPointerException.class, () -> indexManager.save(tempDir, null));
    }
}
