package io.github.sagaraggarwal86.jmeter.scm.model;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class VersionIndexTest {

    private static VersionEntry createEntry(int version) {
        return new VersionEntry(version, "v" + String.format("%03d", version) + ".jmxv",
                LocalDateTime.now(), TriggerType.CHECKPOINT, null, "checksum" + version);
    }

    @Test
    void createDefaultSetsCorrectValues() {
        VersionIndex index = VersionIndex.createDefault(20, ".history");

        assertEquals(1, index.getSchemaVersion());
        assertEquals(20, index.getMaxRetention());
        assertEquals(".history", index.getStorageLocation());
        assertTrue(index.getVersions().isEmpty());
    }

    @Test
    void getNextVersionNumberReturnsOneForEmpty() {
        VersionIndex index = VersionIndex.createDefault(20, ".history");
        assertEquals(1, index.getNextVersionNumber());
    }

    @Test
    void getNextVersionNumberReturnsMaxPlusOne() {
        VersionIndex index = VersionIndex.createDefault(20, ".history");
        index.getVersions().add(createEntry(3));
        index.getVersions().add(createEntry(7));
        index.getVersions().add(createEntry(5));

        assertEquals(8, index.getNextVersionNumber());
    }

    @Test
    void getLatestVersionReturnsLastInList() {
        VersionIndex index = VersionIndex.createDefault(20, ".history");
        index.getVersions().add(createEntry(1));
        index.getVersions().add(createEntry(2));
        index.getVersions().add(createEntry(3));

        assertEquals(3, index.getLatestVersion().getVersion());
    }

    @Test
    void getLatestVersionReturnsNullWhenEmpty() {
        VersionIndex index = VersionIndex.createDefault(20, ".history");
        assertNull(index.getLatestVersion());
    }

    @Test
    void setMaxRetentionUpdatesValue() {
        VersionIndex index = VersionIndex.createDefault(20, ".history");
        index.setMaxRetention(50);
        assertEquals(50, index.getMaxRetention());
    }

    @Test
    void setStorageLocationUpdatesValue() {
        VersionIndex index = VersionIndex.createDefault(20, ".history");
        index.setStorageLocation("/custom/path");
        assertEquals("/custom/path", index.getStorageLocation());
    }

    @Test
    void setStorageLocationRejectsNull() {
        VersionIndex index = VersionIndex.createDefault(20, ".history");
        assertThrows(NullPointerException.class, () -> index.setStorageLocation(null));
    }

    @Test
    void constructorHandlesNullVersionsList() {
        VersionIndex index = new VersionIndex(1, 20, ".history", null, null);
        assertNotNull(index.getVersions());
        assertTrue(index.getVersions().isEmpty());
    }

    @Test
    void constructorDefensiveCopiesList() {
        List<VersionEntry> original = new ArrayList<>();
        original.add(createEntry(1));

        VersionIndex index = new VersionIndex(1, 20, ".history", original, null);
        original.clear();

        assertEquals(1, index.getVersions().size());
    }
}
