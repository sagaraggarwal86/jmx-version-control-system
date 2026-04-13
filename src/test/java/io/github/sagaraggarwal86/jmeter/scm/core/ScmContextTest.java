package io.github.sagaraggarwal86.jmeter.scm.core;

import io.github.sagaraggarwal86.jmeter.scm.model.TriggerType;
import io.github.sagaraggarwal86.jmeter.scm.model.VersionEntry;
import io.github.sagaraggarwal86.jmeter.scm.model.VersionIndex;
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
import static org.mockito.Mockito.*;

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
        assertThrows(IllegalStateException.class, () -> ctx.clearHistory());
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
    void clearHistoryPreservesLatestAndPinned() throws IOException {
        ScmContext ctx = new ScmContext(jmxFile, indexManager, lockManager);
        ctx.initialize();

        ctx.createSnapshot(TriggerType.CHECKPOINT, null);
        Files.writeString(jmxFile, "v2");
        ctx.createSnapshot(TriggerType.CHECKPOINT, null);
        Files.writeString(jmxFile, "v3");
        ctx.createSnapshot(TriggerType.CHECKPOINT, null);

        // Pin v1
        ctx.getVersionIndex().pin(1);

        int deleted = ctx.clearHistory();

        assertEquals(1, deleted); // Only v2 deleted (v1 pinned, v3 latest)
        assertEquals(2, ctx.getVersionIndex().getVersions().size());

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

        // SnapshotEngine prunes before adding new version, so count can be maxRetention + 1
        // Without pruning it would be 4; with pruning it should be 3
        assertEquals(3, ctx.getVersionIndex().getVersions().size());

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
}
