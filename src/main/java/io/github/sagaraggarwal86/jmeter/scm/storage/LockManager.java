package io.github.sagaraggarwal86.jmeter.scm.storage;

import io.github.sagaraggarwal86.jmeter.scm.config.ScmConfigManager;
import io.github.sagaraggarwal86.jmeter.scm.model.LockInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.InetAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Objects;

/**
 * Timestamp-based .lock file management for concurrency control.
 * Application-level locking (no FileLock) — uses JSON file with PID, hostname, and timestamp.
 */
public final class LockManager {

    private static final Logger log = LoggerFactory.getLogger(LockManager.class);
    private static final String LOCK_FILE = ".lock";

    /**
     * Attempts to acquire the lock for the given storage directory.
     *
     * @param storageDir the storage directory
     * @return true if lock acquired, false if another instance holds it
     */
    public boolean acquire(Path storageDir) {
        Objects.requireNonNull(storageDir, "storageDir must not be null");

        try {
            Files.createDirectories(storageDir);

            LockInfo existing = readLock(storageDir.resolve(LOCK_FILE));
            if (existing != null) {
                if (isCurrentProcess(existing)) {
                    writeLock(storageDir, createCurrentLockInfo());
                    return true;
                }
                if (!isStale(existing)) {
                    log.info("Lock held by PID {} on {} since {}",
                            existing.getPid(), existing.getHostname(), existing.getTimestamp());
                    return false;
                }
                log.info("Stale lock detected (PID {} on {}), overriding", existing.getPid(), existing.getHostname());
            }

            writeLock(storageDir, createCurrentLockInfo());
            log.debug("Lock acquired in {}", storageDir);
            return true;
        } catch (IOException e) {
            log.error("Failed to acquire lock in {}: {}", storageDir, e.getMessage());
            return false;
        }
    }

    /**
     * Releases the lock if held by the current process.
     *
     * @param storageDir the storage directory
     */
    public void release(Path storageDir) {
        Objects.requireNonNull(storageDir, "storageDir must not be null");

        Path lockFile = storageDir.resolve(LOCK_FILE);
        try {
            if (Files.exists(lockFile)) {
                LockInfo existing = readLock(lockFile);
                if (existing != null && isCurrentProcess(existing)) {
                    Files.deleteIfExists(lockFile);
                    log.debug("Lock released in {}", storageDir);
                } else {
                    log.debug("Lock not owned by current process, skipping release");
                }
            }
        } catch (IOException e) {
            log.warn("Failed to release lock in {}: {}", storageDir, e.getMessage());
        }
    }

    /**
     * Reads the current lock info, or null if no lock exists or is unreadable.
     *
     * @param storageDir the storage directory
     * @return the lock info, or null
     */
    public LockInfo getLockInfo(Path storageDir) {
        Path lockFile = storageDir.resolve(LOCK_FILE);
        if (!Files.exists(lockFile)) {
            return null;
        }
        return readLock(lockFile);
    }

    /**
     * Checks whether the lock is held by another (non-stale) process.
     *
     * @param storageDir the storage directory
     * @return true if locked by another process
     */
    public boolean isLockedByOther(Path storageDir) {
        LockInfo info = getLockInfo(storageDir);
        if (info == null) {
            return false;
        }
        return !isCurrentProcess(info) && !isStale(info);
    }

    private LockInfo createCurrentLockInfo() {
        String hostname;
        try {
            hostname = InetAddress.getLocalHost().getHostName();
        } catch (Exception e) {
            hostname = "unknown";
        }
        String jmeterVersion;
        try {
            jmeterVersion = org.apache.jmeter.util.JMeterUtils.getJMeterVersion();
        } catch (Exception e) {
            jmeterVersion = "unknown";
        }
        return new LockInfo(ProcessHandle.current().pid(), hostname, LocalDateTime.now(), jmeterVersion);
    }

    private boolean isCurrentProcess(LockInfo lockInfo) {
        return lockInfo.getPid() == ProcessHandle.current().pid();
    }

    private boolean isStale(LockInfo lockInfo) {
        int staleMinutes = ScmConfigManager.getStaleLockMinutes();
        long minutesElapsed = ChronoUnit.MINUTES.between(lockInfo.getTimestamp(), LocalDateTime.now());
        return minutesElapsed > staleMinutes;
    }

    private void writeLock(Path storageDir, LockInfo lockInfo) throws IOException {
        Path lockFile = storageDir.resolve(LOCK_FILE);
        Path tmpFile = storageDir.resolve(LOCK_FILE + ".tmp");

        FileOperations.objectMapper().writeValue(tmpFile.toFile(), lockInfo);
        FileOperations.atomicMove(tmpFile, lockFile);
    }

    private LockInfo readLock(Path lockFile) {
        try {
            return FileOperations.objectMapper().readValue(lockFile.toFile(), LockInfo.class);
        } catch (Exception e) {
            log.warn("Could not read lock file: {}", e.getMessage());
            return null;
        }
    }
}
