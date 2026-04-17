package io.github.sagaraggarwal86.jmeter.scm.storage;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Append-only audit log for SCM actions. Writes JSON Lines to audit.log
 * in the per-plan storage directory. Rotates when file exceeds 1 MB.
 */
public final class AuditLogger {

    private static final Logger log = LoggerFactory.getLogger(AuditLogger.class);
    private static final String AUDIT_FILE = "audit.log";
    private static final String AUDIT_BACKUP = "audit.log.1";
    private static final long MAX_SIZE_BYTES = 1024 * 1024; // 1 MB

    private AuditLogger() {
    }

    public static void logCheckpoint(Path storageDir, int version, String note) {
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("version", version);
        if (note != null) fields.put("note", note);
        write(storageDir, "CHECKPOINT", fields);
    }

    public static void logAutoCheckpoint(Path storageDir, int version) {
        write(storageDir, "AUTO_CHECKPOINT", Map.of("version", version));
    }

    public static void logDelete(Path storageDir, int version) {
        write(storageDir, "DELETE", Map.of("version", version));
    }

    public static void logRestore(Path storageDir, int fromVersion) {
        write(storageDir, "RESTORE", Map.of("fromVersion", fromVersion));
    }

    public static void logExport(Path storageDir, int version, String destination) {
        write(storageDir, "EXPORT", Map.of("version", version, "destination", destination));
    }

    public static void logRetentionPrune(Path storageDir, int deletedCount) {
        write(storageDir, "RETENTION_PRUNE", Map.of("deletedCount", deletedCount));
    }

    public static void logPin(Path storageDir, int version) {
        write(storageDir, "PIN", Map.of("version", version));
    }

    public static void logUnpin(Path storageDir, int version) {
        write(storageDir, "UNPIN", Map.of("version", version));
    }

    public static void logForceReleaseLock(Path storageDir) {
        write(storageDir, "FORCE_RELEASE_LOCK", Map.of());
    }

    public static void logStorageMigrate(Path storageDir, String oldLocation, String newLocation) {
        write(storageDir, "STORAGE_MIGRATE", Map.of("oldLocation", oldLocation, "newLocation", newLocation));
    }

    public static void logStorageReset(Path storageDir, String oldLocation, String backupFile) {
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("oldLocation", oldLocation);
        if (backupFile != null) fields.put("backupFile", backupFile);
        write(storageDir, "STORAGE_RESET", fields);
    }

    private static void write(Path storageDir, String action, Map<String, Object> fields) {
        try {
            Files.createDirectories(storageDir);
            Path auditFile = storageDir.resolve(AUDIT_FILE);

            // Rotate if file exceeds max size
            if (Files.exists(auditFile) && Files.size(auditFile) > MAX_SIZE_BYTES) {
                Files.move(auditFile, storageDir.resolve(AUDIT_BACKUP),
                    StandardCopyOption.REPLACE_EXISTING);
            }

            StringBuilder sb = new StringBuilder();
            sb.append("{\"timestamp\":\"").append(LocalDateTime.now()).append("\"");
            sb.append(",\"action\":\"").append(action).append("\"");
            for (var entry : fields.entrySet()) {
                sb.append(",\"").append(entry.getKey()).append("\":");
                Object val = entry.getValue();
                if (val instanceof String s) {
                    sb.append("\"").append(escapeJson(s)).append("\"");
                } else {
                    sb.append(val);
                }
            }
            sb.append("}\n");

            Files.writeString(auditFile, sb.toString(),
                StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException e) {
            log.warn("Could not write audit log: {}", e.getMessage());
        }
    }

    private static String escapeJson(String s) {
        return s.replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
            .replace("\t", "\\t");
    }
}
