package io.github.sagaraggarwal86.jmeter.scm.storage;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class FileOperationsTest {

    @TempDir
    Path tempDir;

    @Test
    void createSnapshotCopiesFile() throws IOException {
        Path source = tempDir.resolve("test.jmx");
        Files.writeString(source, "<jmeterTestPlan>content</jmeterTestPlan>");

        Path storageDir = tempDir.resolve(".history");
        Path result = FileOperations.createSnapshot(source, storageDir, "v001.jmxv");

        assertTrue(Files.exists(result));
        assertEquals(Files.readString(source), Files.readString(result));
    }

    @Test
    void createSnapshotCreatesDirectories() throws IOException {
        Path source = tempDir.resolve("test.jmx");
        Files.writeString(source, "content");

        Path storageDir = tempDir.resolve("sub/dir/.history");
        FileOperations.createSnapshot(source, storageDir, "v001.jmxv");

        assertTrue(Files.isDirectory(storageDir));
    }

    @Test
    void atomicRestoreWritesToTarget() throws IOException {
        Path snapshot = tempDir.resolve("v001.jmxv");
        Files.writeString(snapshot, "snapshot content");

        Path target = tempDir.resolve("test.jmx");
        Files.writeString(target, "old content");

        FileOperations.atomicRestore(snapshot, target);

        assertEquals("snapshot content", Files.readString(target));
    }

    @Test
    void atomicRestorePreservesOriginalOnFailure() throws IOException {
        Path target = tempDir.resolve("test.jmx");
        Files.writeString(target, "original content");

        Path nonExistent = tempDir.resolve("does-not-exist.jmxv");

        assertThrows(IOException.class, () -> FileOperations.atomicRestore(nonExistent, target));
        assertEquals("original content", Files.readString(target));
    }

    @Test
    void exportSnapshotCopiesFile() throws IOException {
        Path snapshot = tempDir.resolve("v001.jmxv");
        Files.writeString(snapshot, "snapshot data");

        Path dest = tempDir.resolve("export.jmx");
        FileOperations.exportSnapshot(snapshot, dest);

        assertEquals("snapshot data", Files.readString(dest));
    }

    @Test
    void deleteSnapshotRemovesFile() throws IOException {
        Path snapshot = tempDir.resolve("v001.jmxv");
        Files.writeString(snapshot, "data");

        FileOperations.deleteSnapshot(snapshot);

        assertFalse(Files.exists(snapshot));
    }

    @Test
    void deleteSnapshotNoOpForMissing() throws IOException {
        Path missing = tempDir.resolve("missing.jmxv");
        assertDoesNotThrow(() -> FileOperations.deleteSnapshot(missing));
    }

    @Test
    void computeChecksumReturnsDeterministicHash() throws IOException {
        Path file = tempDir.resolve("test.txt");
        Files.writeString(file, "hello world");

        String hash1 = FileOperations.computeChecksum(file);
        String hash2 = FileOperations.computeChecksum(file);

        assertNotNull(hash1);
        assertFalse(hash1.isBlank());
        assertEquals(hash1, hash2);
        assertEquals(64, hash1.length()); // SHA-256 hex = 64 chars
    }

    @Test
    void computeChecksumDiffersForDifferentContent() throws IOException {
        Path file1 = tempDir.resolve("a.txt");
        Path file2 = tempDir.resolve("b.txt");
        Files.writeString(file1, "content A");
        Files.writeString(file2, "content B");

        assertNotEquals(
            FileOperations.computeChecksum(file1),
            FileOperations.computeChecksum(file2));
    }

    @Test
    void snapshotFileNameZeroPads() {
        assertEquals("HTTP Request_001.jmxv", FileOperations.snapshotFileName("HTTP Request", 1));
        assertEquals("test_010.jmxv", FileOperations.snapshotFileName("test", 10));
        assertEquals("my-plan_100.jmxv", FileOperations.snapshotFileName("my-plan", 100));
        assertEquals("script_999.jmxv", FileOperations.snapshotFileName("script", 999));
    }

    @Test
    void calculateStorageSizeSumsFiles() throws IOException {
        Path storageDir = tempDir.resolve(".history");
        Files.createDirectories(storageDir);
        Files.writeString(storageDir.resolve("v001.jmxv"), "aaaa"); // 4 bytes
        Files.writeString(storageDir.resolve("v002.jmxv"), "bbbbbb"); // 6 bytes

        long size = FileOperations.calculateStorageSize(storageDir);
        assertEquals(10, size);
    }

    @Test
    void calculateStorageSizeReturnsZeroForMissing() {
        assertEquals(0L, FileOperations.calculateStorageSize(tempDir.resolve("nonexistent")));
    }

    @Test
    void createSnapshotRejectsNull() {
        assertThrows(NullPointerException.class, () ->
            FileOperations.createSnapshot(null, tempDir, "v001.jmxv"));
        assertThrows(NullPointerException.class, () ->
            FileOperations.createSnapshot(tempDir.resolve("a"), null, "v001.jmxv"));
        assertThrows(NullPointerException.class, () ->
            FileOperations.createSnapshot(tempDir.resolve("a"), tempDir, null));
    }
}
