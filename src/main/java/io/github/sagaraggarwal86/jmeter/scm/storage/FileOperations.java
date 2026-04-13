package io.github.sagaraggarwal86.jmeter.scm.storage;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.module.SimpleModule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.Objects;

/**
 * Low-level file operations: copy, delete, atomic restore, export, and checksum computation.
 */
public final class FileOperations {

    private static final Logger log = LoggerFactory.getLogger(FileOperations.class);

    /**
     * Thread-safe shared ObjectMapper configured for this plugin's JSON needs.
     * Custom LocalDateTime handling (ISO 8601), indented output, ignores unknown properties (R11).
     */
    private static final ObjectMapper SHARED_MAPPER = createMapper();

    private FileOperations() {
    }

    private static ObjectMapper createMapper() {
        ObjectMapper mapper = new ObjectMapper();

        // Custom LocalDateTime ser/deser — avoids jackson-datatype-jsr310 dependency
        // which is not shipped with JMeter. Output is identical: ISO 8601 strings.
        SimpleModule module = new SimpleModule();
        module.addSerializer(LocalDateTime.class, new JsonSerializer<>() {
            @Override
            public void serialize(LocalDateTime value, JsonGenerator gen, SerializerProvider provider)
                    throws IOException {
                gen.writeString(value.toString());
            }
        });
        module.addDeserializer(LocalDateTime.class, new JsonDeserializer<>() {
            @Override
            public LocalDateTime deserialize(JsonParser p, DeserializationContext ctxt)
                    throws IOException {
                return LocalDateTime.parse(p.getText());
            }
        });
        mapper.registerModule(module);

        mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        mapper.configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false);
        mapper.enable(SerializationFeature.INDENT_OUTPUT);
        return mapper;
    }

    /**
     * Returns the shared ObjectMapper instance. Thread-safe once configured.
     */
    public static ObjectMapper objectMapper() {
        return SHARED_MAPPER;
    }

    /**
     * Moves a file to a target, attempting atomic move first with standard fallback.
     *
     * @param source the source file
     * @param target the target file
     * @throws IOException if the move fails
     */
    public static void atomicMove(Path source, Path target) throws IOException {
        try {
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException e) {
            log.warn("Atomic move not supported, falling back to standard move");
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    /**
     * Copies a .jmx file to the storage directory as a .jmxv snapshot.
     *
     * @param source     the .jmx file to snapshot
     * @param storageDir the storage directory (.history)
     * @param fileName   the snapshot filename (e.g., v001.jmxv)
     * @return the path to the created snapshot
     * @throws IOException if the copy fails
     */
    public static Path createSnapshot(Path source, Path storageDir, String fileName) throws IOException {
        Objects.requireNonNull(source, "source must not be null");
        Objects.requireNonNull(storageDir, "storageDir must not be null");
        Objects.requireNonNull(fileName, "fileName must not be null");

        Files.createDirectories(storageDir);
        Path target = storageDir.resolve(fileName);
        Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING);
        log.debug("Created snapshot: {}", target);
        return target;
    }

    /**
     * Atomically restores a snapshot to the .jmx file.
     * Writes to a .tmp file first, then performs atomic move.
     *
     * @param snapshotPath the .jmxv snapshot to restore from
     * @param targetJmx    the .jmx file to restore to
     * @throws IOException if the restore fails
     */
    public static void atomicRestore(Path snapshotPath, Path targetJmx) throws IOException {
        Objects.requireNonNull(snapshotPath, "snapshotPath must not be null");
        Objects.requireNonNull(targetJmx, "targetJmx must not be null");

        Path tmpFile = targetJmx.resolveSibling(targetJmx.getFileName() + ".tmp");
        try {
            Files.copy(snapshotPath, tmpFile, StandardCopyOption.REPLACE_EXISTING);
            atomicMove(tmpFile, targetJmx);
            log.debug("Restored {} from {}", targetJmx, snapshotPath);
        } catch (IOException e) {
            // Clean up tmp file on failure — previous state intact
            try {
                Files.deleteIfExists(tmpFile);
            } catch (IOException suppressed) {
                e.addSuppressed(suppressed);
            }
            throw e;
        }
    }

    /**
     * Exports a snapshot to a user-specified location.
     *
     * @param snapshotPath the .jmxv snapshot to export
     * @param destination  the export destination
     * @throws IOException if the export fails
     */
    public static void exportSnapshot(Path snapshotPath, Path destination) throws IOException {
        Objects.requireNonNull(snapshotPath, "snapshotPath must not be null");
        Objects.requireNonNull(destination, "destination must not be null");

        Files.copy(snapshotPath, destination, StandardCopyOption.REPLACE_EXISTING);
        log.debug("Exported {} to {}", snapshotPath, destination);
    }

    /**
     * Deletes a snapshot file.
     *
     * @param snapshotPath the .jmxv file to delete
     * @throws IOException if the delete fails
     */
    public static void deleteSnapshot(Path snapshotPath) throws IOException {
        Objects.requireNonNull(snapshotPath, "snapshotPath must not be null");
        Files.deleteIfExists(snapshotPath);
        log.debug("Deleted snapshot: {}", snapshotPath);
    }

    /**
     * Computes SHA-256 checksum of a file.
     *
     * @param file the file to checksum
     * @return hex-encoded SHA-256 hash
     * @throws IOException if reading the file fails
     */
    public static String computeChecksum(Path file) throws IOException {
        Objects.requireNonNull(file, "file must not be null");

        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (InputStream is = Files.newInputStream(file)) {
                byte[] buffer = new byte[8192];
                int bytesRead;
                while ((bytesRead = is.read(buffer)) != -1) {
                    digest.update(buffer, 0, bytesRead);
                }
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException e) {
            throw new IOException("SHA-256 algorithm not available", e);
        }
    }

    /**
     * Generates the snapshot filename for a given version number.
     * Extracts the filename without extension from a path.
     * E.g., "test1.jmx" → "test1", "HTTP Request.jmx" → "HTTP Request".
     *
     * @param path the file path
     * @return filename without extension
     */
    public static String extractStem(Path path) {
        return path.getFileName().toString().replaceFirst("\\.[^.]+$", "");
    }

    /**
     * Format: {@code <stem>_<NNN>.jmxv} (e.g., "HTTP Request_001.jmxv").
     *
     * @param stem          the jmx filename without extension
     * @param versionNumber the version number
     * @return formatted filename
     */
    public static String snapshotFileName(String stem, int versionNumber) {
        return String.format("%s_%03d.jmxv", stem, versionNumber);
    }

    /**
     * Calculates total size of all files in the storage directory in bytes.
     *
     * @param storageDir the storage directory
     * @return total size in bytes
     */
    public static long calculateStorageSize(Path storageDir) {
        if (!Files.isDirectory(storageDir)) {
            return 0L;
        }
        try (var stream = Files.list(storageDir)) {
            return stream
                    .filter(Files::isRegularFile)
                    .mapToLong(p -> {
                        try {
                            return Files.size(p);
                        } catch (IOException e) {
                            return 0L;
                        }
                    })
                    .sum();
        } catch (IOException e) {
            log.warn("Could not calculate storage size: {}", e.getMessage());
            return 0L;
        }
    }
}
