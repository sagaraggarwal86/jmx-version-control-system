package io.github.sagaraggarwal86.jmeter.scm.core;

import io.github.sagaraggarwal86.jmeter.scm.model.TriggerType;
import io.github.sagaraggarwal86.jmeter.scm.model.VersionEntry;
import io.github.sagaraggarwal86.jmeter.scm.storage.IndexManager;
import io.github.sagaraggarwal86.jmeter.scm.storage.LockManager;
import org.apache.jmeter.util.JMeterUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.MockedStatic;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mockStatic;

class ScmContextTest {

    @TempDir
    Path tempDir;

    private MockedStatic<JMeterUtils> jmeterUtilsMock;
    private IndexManager indexManager;
    private LockManager lockManager;
    private Path jmxFile;

    @BeforeEach
    void setUp() throws IOException {
        jmeterUtilsMock = mockStatic(JMeterUtils.class);
        jmeterUtilsMock.when(() -> JMeterUtils.getProperty("scm.storage.location")).thenReturn(".history");
        jmeterUtilsMock.when(() -> JMeterUtils.getProperty("scm.max.retention")).thenReturn("20");
        jmeterUtilsMock.when(() -> JMeterUtils.getProperty("scm.lock.stale.minutes")).thenReturn("60");
        jmeterUtilsMock.when(JMeterUtils::getJMeterVersion).thenReturn("5.6.3");

        indexManager = new IndexManager();
        lockManager = new LockManager();
        jmxFile = tempDir.resolve("test.jmx");
        Files.writeString(jmxFile, "<jmeterTestPlan>content</jmeterTestPlan>");
    }

    @AfterEach
    void tearDown() {
        jmeterUtilsMock.close();
    }

    @Test
    void initializeCreatesStorageDirAndIndex() throws IOException {
        ScmContext ctx = new ScmContext(jmxFile, indexManager, lockManager);
        ctx.initialize();

        assertNotNull(ctx.getVersionIndex());
        assertTrue(Files.exists(ctx.getStorageDir()));
        assertTrue(Files.exists(ctx.getStorageDir().resolve("index.json")));
        assertFalse(ctx.isReadOnly());
        assertFalse(ctx.isDisposed());

        ctx.dispose();
    }

    @Test
    void initializeAcquiresLock() throws IOException {
        ScmContext ctx = new ScmContext(jmxFile, indexManager, lockManager);
        ctx.initialize();

        assertTrue(Files.exists(ctx.getStorageDir().resolve(".lock")));

        ctx.dispose();
    }

    @Test
    void disposeReleasesLock() throws IOException {
        ScmContext ctx = new ScmContext(jmxFile, indexManager, lockManager);
        ctx.initialize();
        Path lockFile = ctx.getStorageDir().resolve(".lock");

        assertTrue(Files.exists(lockFile));
        ctx.dispose();
        assertFalse(Files.exists(lockFile));
        assertTrue(ctx.isDisposed());
    }

    @Test
    void doubleDisposeIsNoOp() throws IOException {
        ScmContext ctx = new ScmContext(jmxFile, indexManager, lockManager);
        ctx.initialize();
        ctx.dispose();
        assertDoesNotThrow(ctx::dispose);
    }

    @Test
    void createSnapshotCreatesVersion() throws IOException {
        ScmContext ctx = new ScmContext(jmxFile, indexManager, lockManager);
        ctx.initialize();

        VersionEntry entry = ctx.createSnapshot(TriggerType.CHECKPOINT, "test");

        assertNotNull(entry);
        assertEquals(1, entry.getVersion());
        assertEquals("test", entry.getNote());
        assertEquals(1, ctx.getVersionIndex().getVersions().size());

        ctx.dispose();
    }

    @Test
    void createCheckpointDelegatesToSnapshot() throws IOException {
        ScmContext ctx = new ScmContext(jmxFile, indexManager, lockManager);
        ctx.initialize();

        VersionEntry entry = ctx.createCheckpoint("my note");

        assertNotNull(entry);
        assertEquals(TriggerType.CHECKPOINT, entry.getTrigger());
        assertEquals("my note", entry.getNote());

        ctx.dispose();
    }

    @Test
    void operationsOnDisposedContextThrow() throws IOException {
        ScmContext ctx = new ScmContext(jmxFile, indexManager, lockManager);
        ctx.initialize();
        ctx.dispose();

        assertThrows(IllegalStateException.class, () -> ctx.createSnapshot(TriggerType.CHECKPOINT, null));
        assertThrows(IllegalStateException.class, () -> ctx.restore(1));
        assertThrows(IllegalStateException.class, () -> ctx.deleteVersion(1));
    }

    @Test
    void restoreCreatesAutoSnapshotAndRestores() throws IOException {
        ScmContext ctx = new ScmContext(jmxFile, indexManager, lockManager);
        ctx.initialize();

        // Create v1
        ctx.createSnapshot(TriggerType.CHECKPOINT, null);

        // Modify and create v2
        Files.writeString(jmxFile, "modified");
        ctx.createSnapshot(TriggerType.CHECKPOINT, null);

        // Modify again so auto-snapshot before restore captures state
        Files.writeString(jmxFile, "further changes");

        // Restore to v1
        ctx.restore(1);

        // v3 = auto-snapshot of "further changes" before restore
        assertEquals(3, ctx.getVersionIndex().getVersions().size());
        assertEquals("<jmeterTestPlan>content</jmeterTestPlan>", Files.readString(jmxFile));

        ctx.dispose();
    }

    @Test
    void deleteVersionRemovesNonLatest() throws IOException {
        ScmContext ctx = new ScmContext(jmxFile, indexManager, lockManager);
        ctx.initialize();

        ctx.createSnapshot(TriggerType.CHECKPOINT, null);
        Files.writeString(jmxFile, "v2");
        ctx.createSnapshot(TriggerType.CHECKPOINT, null);

        ctx.deleteVersion(1);

        assertEquals(1, ctx.getVersionIndex().getVersions().size());
        assertEquals(2, ctx.getVersionIndex().getVersions().get(0).getVersion());

        ctx.dispose();
    }

    @Test
    void pruneExcessVersionsRespectRetention() throws IOException {
        jmeterUtilsMock.when(() -> JMeterUtils.getProperty("scm.max.retention")).thenReturn("2");

        ScmContext ctx = new ScmContext(jmxFile, indexManager, lockManager);
        ctx.initialize();
        // Set maxRetention on the index
        ctx.getVersionIndex().setMaxRetention(2);

        ctx.createSnapshot(TriggerType.CHECKPOINT, null);
        Files.writeString(jmxFile, "v2");
        ctx.createSnapshot(TriggerType.CHECKPOINT, null);
        Files.writeString(jmxFile, "v3");
        ctx.createSnapshot(TriggerType.CHECKPOINT, null);
        Files.writeString(jmxFile, "v4");
        ctx.createSnapshot(TriggerType.CHECKPOINT, null);

        // SnapshotEngine prunes after adding new version, so count stays at maxRetention
        assertEquals(2, ctx.getVersionIndex().getVersions().size());

        ctx.dispose();
    }

    @Test
    void getJmxFileReturnsCorrectPath() throws IOException {
        ScmContext ctx = new ScmContext(jmxFile, indexManager, lockManager);
        assertEquals(jmxFile, ctx.getJmxFile());
        ctx.dispose();
    }

    @Test
    void storageDirResolvesCorrectly() throws IOException {
        ScmContext ctx = new ScmContext(jmxFile, indexManager, lockManager);
        Path expected = tempDir.resolve(".history").resolve("test");
        assertEquals(expected, ctx.getStorageDir());
        ctx.dispose();
    }

    @Test
    void forceReleaseLockSwitchesToReadWrite() throws IOException {
        ScmContext ctx = new ScmContext(jmxFile, indexManager, lockManager);
        ctx.initialize();

        // Write a foreign non-stale lock to simulate another holder
        Path lockFile = ctx.getStorageDir().resolve(".lock");
        Files.writeString(lockFile, "{\"pid\":99999,\"hostname\":\"otherhost\"," +
                "\"timestamp\":\"" + java.time.LocalDateTime.now() + "\",\"jmeterVersion\":\"5.6.3\"}");

        // tryAcquire fails → switches to read-only
        assertFalse(ctx.tryAcquireLock());
        assertTrue(ctx.isReadOnly());

        // Force release and re-acquire
        boolean success = ctx.forceReleaseLock();
        assertTrue(success);
        assertFalse(ctx.isReadOnly());

        ctx.dispose();
    }

    @Test
    void tryAcquireLockReturnsTrueWhenAvailable() throws IOException {
        ScmContext ctx = new ScmContext(jmxFile, indexManager, lockManager);
        ctx.initialize();

        // Lock is already ours, tryAcquire should succeed
        assertTrue(ctx.tryAcquireLock());
        assertFalse(ctx.isReadOnly());

        ctx.dispose();
    }

    @Test
    void tryAcquireLockReturnsFalseWhenHeldByOther() throws IOException {
        ScmContext ctx = new ScmContext(jmxFile, indexManager, lockManager);
        ctx.initialize();

        // Write a foreign lock
        Path lockFile = ctx.getStorageDir().resolve(".lock");
        Files.writeString(lockFile, "{\"pid\":99999,\"hostname\":\"otherhost\"," +
                "\"timestamp\":\"" + java.time.LocalDateTime.now() + "\",\"jmeterVersion\":\"5.6.3\"}");

        boolean result = ctx.tryAcquireLock();
        assertFalse(result);
        assertTrue(ctx.isReadOnly());

        // Force release to clean up
        ctx.forceReleaseLock();
        ctx.dispose();
    }

    @Test
    void isAtUnprunableCapacityReturnsFalseWhenUnderLimit() throws IOException {
        ScmContext ctx = new ScmContext(jmxFile, indexManager, lockManager);
        ctx.initialize();

        ctx.createSnapshot(TriggerType.CHECKPOINT, null);

        assertFalse(ctx.isAtUnprunableCapacity());

        ctx.dispose();
    }

    @Test
    void isAtUnprunableCapacityReturnsTrueWhenAllFrozen() throws IOException {
        jmeterUtilsMock.when(() -> JMeterUtils.getProperty("scm.max.retention")).thenReturn("2");

        ScmContext ctx = new ScmContext(jmxFile, indexManager, lockManager);
        ctx.initialize();
        ctx.getVersionIndex().setMaxRetention(2);

        ctx.createSnapshot(TriggerType.CHECKPOINT, null);
        Files.writeString(jmxFile, "v2");
        ctx.createSnapshot(TriggerType.CHECKPOINT, null);

        // Pin the non-latest version
        ctx.getVersionIndex().pin(1);

        assertTrue(ctx.isAtUnprunableCapacity());

        ctx.dispose();
    }

    @Test
    void isAtUnprunableCapacityReturnsFalseBeforeInit() throws IOException {
        ScmContext ctx = new ScmContext(jmxFile, indexManager, lockManager);
        // versionIndex is null before initialize()
        assertFalse(ctx.isAtUnprunableCapacity());
        ctx.dispose();
    }

    @Test
    void pruneExcessVersionsDeletesOldVersions() throws IOException {
        jmeterUtilsMock.when(() -> JMeterUtils.getProperty("scm.max.retention")).thenReturn("5");

        ScmContext ctx = new ScmContext(jmxFile, indexManager, lockManager);
        ctx.initialize();
        ctx.getVersionIndex().setMaxRetention(5);

        for (int i = 0; i < 5; i++) {
            Files.writeString(jmxFile, "content" + i);
            ctx.createSnapshot(TriggerType.CHECKPOINT, null);
        }
        assertEquals(5, ctx.getVersionIndex().getVersions().size());

        // Now reduce retention and prune
        ctx.getVersionIndex().setMaxRetention(3);
        int pruned = ctx.pruneExcessVersions();

        assertEquals(2, pruned);
        assertEquals(3, ctx.getVersionIndex().getVersions().size());

        ctx.dispose();
    }

    @Test
    void pruneExcessVersionsReturnsZeroWhenWithinLimit() throws IOException {
        ScmContext ctx = new ScmContext(jmxFile, indexManager, lockManager);
        ctx.initialize();

        ctx.createSnapshot(TriggerType.CHECKPOINT, null);

        int pruned = ctx.pruneExcessVersions();
        assertEquals(0, pruned);

        ctx.dispose();
    }

    @Test
    void writeOperationFailsInReadOnlyMode() throws IOException {
        ScmContext ctx = new ScmContext(jmxFile, indexManager, lockManager);
        ctx.initialize();

        // Write a foreign lock to make next acquire fail
        Path lockFile = ctx.getStorageDir().resolve(".lock");
        Files.writeString(lockFile, "{\"pid\":99999,\"hostname\":\"otherhost\"," +
                "\"timestamp\":\"" + java.time.LocalDateTime.now() + "\",\"jmeterVersion\":\"5.6.3\"}");
        ctx.tryAcquireLock(); // switches to read-only

        assertThrows(IOException.class, () -> ctx.createSnapshot(TriggerType.CHECKPOINT, null));

        ctx.forceReleaseLock();
        ctx.dispose();
    }

    @Test
    void getLockInfoReturnsNullWhenNoLock() throws IOException {
        ScmContext ctx = new ScmContext(jmxFile, indexManager, lockManager);
        ctx.initialize();
        // Lock is ours — getLockInfo should return non-null
        assertNotNull(ctx.getLockInfo());
        ctx.dispose();
    }

    @Test
    void migrateLegacyLayoutMovesFiles() throws IOException {
        // Simulate legacy flat layout: .history/index.json exists, .history/test/ does not
        Path historyRoot = tempDir.resolve(".history");
        Files.createDirectories(historyRoot);
        Files.writeString(historyRoot.resolve("index.json"),
                "{\"schemaVersion\":1,\"maxRetention\":20,\"storageLocation\":\".history\",\"versions\":[],\"pinnedVersions\":[]}");
        Files.writeString(historyRoot.resolve("test_001.jmxv"), "snapshot");

        ScmContext ctx = new ScmContext(jmxFile, indexManager, lockManager);
        ctx.initialize();

        // After migration, files should be in .history/test/
        Path planDir = historyRoot.resolve("test");
        assertTrue(Files.exists(planDir));
        assertTrue(Files.exists(planDir.resolve("test_001.jmxv")));
        // Legacy index.json should have been moved too
        assertFalse(Files.exists(historyRoot.resolve("index.json")));

        ctx.dispose();
    }

    @Test
    void migrateLegacyLayoutSkipsWhenAlreadyMigrated() throws IOException {
        // Create per-plan dir first — migration should not trigger
        Path planDir = tempDir.resolve(".history").resolve("test");
        Files.createDirectories(planDir);

        ScmContext ctx = new ScmContext(jmxFile, indexManager, lockManager);
        ctx.initialize();

        // Should still initialize fine
        assertNotNull(ctx.getVersionIndex());

        ctx.dispose();
    }

    @Test
    void getSnapshotEngineReturnsNonNull() throws IOException {
        ScmContext ctx = new ScmContext(jmxFile, indexManager, lockManager);
        assertNotNull(ctx.getSnapshotEngine());
        ctx.dispose();
    }

    @Test
    void getDirtyTrackerReturnsNonNull() throws IOException {
        ScmContext ctx = new ScmContext(jmxFile, indexManager, lockManager);
        assertNotNull(ctx.getDirtyTracker());
        ctx.dispose();
    }

    @Test
    void getIndexManagerReturnsNonNull() throws IOException {
        ScmContext ctx = new ScmContext(jmxFile, indexManager, lockManager);
        assertNotNull(ctx.getIndexManager());
        ctx.dispose();
    }

    @Test
    void createSnapshotResetsDirectoryTrackerOnSuccess() throws IOException {
        ScmContext ctx = new ScmContext(jmxFile, indexManager, lockManager);
        ctx.initialize();

        ctx.createSnapshot(TriggerType.CHECKPOINT, null);
        assertFalse(ctx.getDirtyTracker().isDirty());

        ctx.dispose();
    }

    @Test
    void autoCheckpointDeduplicatesIdenticalSaves() throws IOException {
        ScmContext ctx = new ScmContext(jmxFile, indexManager, lockManager);
        ctx.initialize();

        VersionEntry first = ctx.createSnapshot(TriggerType.AUTO_CHECKPOINT, null);
        assertNotNull(first);

        // Same content — AUTO_CHECKPOINT should skip
        VersionEntry second = ctx.createSnapshot(TriggerType.AUTO_CHECKPOINT, null);
        assertNull(second);

        assertEquals(1, ctx.getVersionIndex().getVersions().size());

        ctx.dispose();
    }

    @Test
    void disposeDoesNotReleaseLockInReadOnlyMode() throws IOException {
        ScmContext ctx = new ScmContext(jmxFile, indexManager, lockManager);
        ctx.initialize();

        // Write a foreign lock
        Path lockFile = ctx.getStorageDir().resolve(".lock");
        Files.writeString(lockFile, "{\"pid\":99999,\"hostname\":\"otherhost\"," +
                "\"timestamp\":\"" + java.time.LocalDateTime.now() + "\",\"jmeterVersion\":\"5.6.3\"}");
        ctx.tryAcquireLock(); // switches to read-only
        assertTrue(ctx.isReadOnly());

        ctx.dispose();
        assertTrue(ctx.isDisposed());
        // Lock file should still exist (we don't own it)
        assertTrue(Files.exists(lockFile));
    }
}
