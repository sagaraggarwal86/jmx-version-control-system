package io.github.sagaraggarwal86.jmeter.scm.storage;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class AuditLoggerTest {

    @TempDir
    Path tempDir;

    @Test
    void logCheckpointWritesJsonLine() throws IOException {
        AuditLogger.logCheckpoint(tempDir, 1, "test note");

        Path auditFile = tempDir.resolve("audit.log");
        assertTrue(Files.exists(auditFile));

        String content = Files.readString(auditFile);
        assertTrue(content.contains("\"action\":\"CHECKPOINT\""));
        assertTrue(content.contains("\"version\":1"));
        assertTrue(content.contains("\"note\":\"test note\""));
        assertTrue(content.contains("\"timestamp\":\""));
        assertTrue(content.endsWith("\n"));
    }

    @Test
    void logCheckpointWithoutNote() throws IOException {
        AuditLogger.logCheckpoint(tempDir, 1, null);

        String content = Files.readString(tempDir.resolve("audit.log"));
        assertTrue(content.contains("\"action\":\"CHECKPOINT\""));
        assertFalse(content.contains("\"note\""));
    }

    @Test
    void logAutoCheckpointWritesAction() throws IOException {
        AuditLogger.logAutoCheckpoint(tempDir, 5);

        String content = Files.readString(tempDir.resolve("audit.log"));
        assertTrue(content.contains("\"action\":\"AUTO_CHECKPOINT\""));
        assertTrue(content.contains("\"version\":5"));
    }

    @Test
    void logDeleteWritesAction() throws IOException {
        AuditLogger.logDelete(tempDir, 3);

        String content = Files.readString(tempDir.resolve("audit.log"));
        assertTrue(content.contains("\"action\":\"DELETE\""));
        assertTrue(content.contains("\"version\":3"));
    }

    @Test
    void logRestoreWritesAction() throws IOException {
        AuditLogger.logRestore(tempDir, 2);

        String content = Files.readString(tempDir.resolve("audit.log"));
        assertTrue(content.contains("\"action\":\"RESTORE\""));
        assertTrue(content.contains("\"fromVersion\":2"));
    }

    @Test
    void logExportWritesAction() throws IOException {
        AuditLogger.logExport(tempDir, 1, "/tmp/exported.jmx");

        String content = Files.readString(tempDir.resolve("audit.log"));
        assertTrue(content.contains("\"action\":\"EXPORT\""));
        assertTrue(content.contains("\"destination\":\"/tmp/exported.jmx\""));
    }

    @Test
    void logPinUnpinWritesAction() throws IOException {
        AuditLogger.logPin(tempDir, 3);
        AuditLogger.logUnpin(tempDir, 3);

        String content = Files.readString(tempDir.resolve("audit.log"));
        assertTrue(content.contains("\"action\":\"PIN\""));
        assertTrue(content.contains("\"action\":\"UNPIN\""));
    }

    @Test
    void logForceReleaseLockWritesAction() throws IOException {
        AuditLogger.logForceReleaseLock(tempDir);

        String content = Files.readString(tempDir.resolve("audit.log"));
        assertTrue(content.contains("\"action\":\"FORCE_RELEASE_LOCK\""));
    }

    @Test
    void logStorageMigrateWritesAction() throws IOException {
        AuditLogger.logStorageMigrate(tempDir, ".history", "/new/path");

        String content = Files.readString(tempDir.resolve("audit.log"));
        assertTrue(content.contains("\"action\":\"STORAGE_MIGRATE\""));
        assertTrue(content.contains("\"oldLocation\":\".history\""));
        assertTrue(content.contains("\"newLocation\":\"/new/path\""));
    }

    @Test
    void logStorageResetWritesAction() throws IOException {
        AuditLogger.logStorageReset(tempDir, ".history", "backup_20260412.zip");

        String content = Files.readString(tempDir.resolve("audit.log"));
        assertTrue(content.contains("\"action\":\"STORAGE_RESET\""));
        assertTrue(content.contains("\"oldLocation\":\".history\""));
        assertTrue(content.contains("\"backupFile\":\"backup_20260412.zip\""));
    }

    @Test
    void logStorageResetWithNullBackup() throws IOException {
        AuditLogger.logStorageReset(tempDir, ".history", null);

        String content = Files.readString(tempDir.resolve("audit.log"));
        assertTrue(content.contains("\"action\":\"STORAGE_RESET\""));
        assertFalse(content.contains("\"backupFile\""));
    }

    @Test
    void multipleEntriesAppended() throws IOException {
        AuditLogger.logCheckpoint(tempDir, 1, null);
        AuditLogger.logCheckpoint(tempDir, 2, null);
        AuditLogger.logCheckpoint(tempDir, 3, null);

        String content = Files.readString(tempDir.resolve("audit.log"));
        String[] lines = content.split("\n");
        assertEquals(3, lines.length);
    }

    @Test
    void rotationAt1MB() throws IOException {
        Path auditFile = tempDir.resolve("audit.log");

        // Write a file just over 1MB
        StringBuilder sb = new StringBuilder();
        while (sb.length() < 1024 * 1024 + 100) {
            sb.append("{\"padding\":\"x\"}\n");
        }
        Files.writeString(auditFile, sb.toString());

        // Next write should trigger rotation
        AuditLogger.logCheckpoint(tempDir, 1, null);

        assertTrue(Files.exists(tempDir.resolve("audit.log.1")));
        // New audit.log should contain only the latest entry
        String newContent = Files.readString(auditFile);
        assertTrue(newContent.contains("\"action\":\"CHECKPOINT\""));
        long newSize = Files.size(auditFile);
        assertTrue(newSize < 1024); // Much smaller than 1MB
    }

    @Test
    void jsonEscapesSpecialCharacters() throws IOException {
        AuditLogger.logCheckpoint(tempDir, 1, "note with \"quotes\" and \\backslashes");

        String content = Files.readString(tempDir.resolve("audit.log"));
        assertTrue(content.contains("\\\"quotes\\\""));
        assertTrue(content.contains("\\\\backslashes"));
    }

    @Test
    void createsDirectoryIfMissing() throws IOException {
        Path nested = tempDir.resolve("sub/dir");
        AuditLogger.logCheckpoint(nested, 1, null);

        assertTrue(Files.exists(nested.resolve("audit.log")));
    }
}
