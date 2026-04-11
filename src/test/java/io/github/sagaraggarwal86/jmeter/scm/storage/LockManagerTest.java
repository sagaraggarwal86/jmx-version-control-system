package io.github.sagaraggarwal86.jmeter.scm.storage;

import io.github.sagaraggarwal86.jmeter.scm.model.LockInfo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

class LockManagerTest {

    @TempDir
    Path tempDir;

    private LockManager lockManager;

    @BeforeEach
    void setUp() {
        lockManager = new LockManager();
    }

    @Test
    void acquireCreatesLockFile() {
        assertTrue(lockManager.acquire(tempDir));
        assertTrue(Files.exists(tempDir.resolve(".lock")));
    }

    @Test
    void acquireTwiceByCurrentProcessSucceeds() {
        assertTrue(lockManager.acquire(tempDir));
        assertTrue(lockManager.acquire(tempDir)); // Should refresh, not fail
    }

    @Test
    void releaseRemovesLockFile() {
        lockManager.acquire(tempDir);
        lockManager.release(tempDir);
        assertFalse(Files.exists(tempDir.resolve(".lock")));
    }

    @Test
    void releaseNoOpWhenNoLock() {
        assertDoesNotThrow(() -> lockManager.release(tempDir));
    }

    @Test
    void getLockInfoReturnsNullWhenNoLock() {
        assertNull(lockManager.getLockInfo(tempDir));
    }

    @Test
    void getLockInfoReturnsInfoAfterAcquire() {
        lockManager.acquire(tempDir);
        LockInfo info = lockManager.getLockInfo(tempDir);

        assertNotNull(info);
        assertEquals(ProcessHandle.current().pid(), info.getPid());
        assertNotNull(info.getHostname());
        assertNotNull(info.getTimestamp());
    }

    @Test
    void isLockedByOtherReturnsFalseWhenNoLock() {
        assertFalse(lockManager.isLockedByOther(tempDir));
    }

    @Test
    void isLockedByOtherReturnsFalseForCurrentProcess() {
        lockManager.acquire(tempDir);
        assertFalse(lockManager.isLockedByOther(tempDir));
    }

    @Test
    void staleLockOverridden() throws Exception {
        // Write a lock with a very old timestamp (stale)
        String staleLock = """
                {
                  "pid": 99999,
                  "hostname": "OTHER-HOST",
                  "timestamp": "2020-01-01T00:00:00",
                  "jmeterVersion": "5.6.3"
                }
                """;
        Files.createDirectories(tempDir);
        Files.writeString(tempDir.resolve(".lock"), staleLock);

        // Should override stale lock
        assertTrue(lockManager.acquire(tempDir));

        LockInfo info = lockManager.getLockInfo(tempDir);
        assertEquals(ProcessHandle.current().pid(), info.getPid());
    }

    @Test
    void nonStaleLockBlocksAcquisition() throws Exception {
        // Write a lock with current timestamp from another PID
        String recentLock = String.format("""
                {
                  "pid": 99999,
                  "hostname": "OTHER-HOST",
                  "timestamp": "%s",
                  "jmeterVersion": "5.6.3"
                }
                """, LocalDateTime.now().toString());
        Files.createDirectories(tempDir);
        Files.writeString(tempDir.resolve(".lock"), recentLock);

        // Should fail — not stale, different PID
        assertFalse(lockManager.acquire(tempDir));
    }

    @Test
    void releaseDoesNotRemoveOtherProcessLock() throws Exception {
        String otherLock = String.format("""
                {
                  "pid": 99999,
                  "hostname": "OTHER-HOST",
                  "timestamp": "%s",
                  "jmeterVersion": "5.6.3"
                }
                """, LocalDateTime.now().toString());
        Files.createDirectories(tempDir);
        Files.writeString(tempDir.resolve(".lock"), otherLock);

        lockManager.release(tempDir);

        // Lock should still exist — not ours to release
        assertTrue(Files.exists(tempDir.resolve(".lock")));
    }

    @Test
    void corruptLockFileHandledGracefully() throws Exception {
        Files.createDirectories(tempDir);
        Files.writeString(tempDir.resolve(".lock"), "not valid json}}}}");

        // Should handle gracefully and acquire
        assertNull(lockManager.getLockInfo(tempDir));
        assertTrue(lockManager.acquire(tempDir));
    }
}
