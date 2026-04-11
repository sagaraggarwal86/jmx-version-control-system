package io.github.sagaraggarwal86.jmeter.scm.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Root object for index.json — contains schema version, settings, and version history.
 */
public final class VersionIndex {

    private final int schemaVersion;
    private final List<VersionEntry> versions;
    private int maxRetention;
    private String storageLocation;

    @JsonCreator
    public VersionIndex(
            @JsonProperty("schemaVersion") int schemaVersion,
            @JsonProperty("maxRetention") int maxRetention,
            @JsonProperty("storageLocation") String storageLocation,
            @JsonProperty("versions") List<VersionEntry> versions) {
        this.schemaVersion = schemaVersion;
        this.maxRetention = maxRetention;
        this.storageLocation = Objects.requireNonNull(storageLocation, "storageLocation must not be null");
        this.versions = versions != null ? new ArrayList<>(versions) : new ArrayList<>();
    }

    /**
     * Creates a new default index.
     */
    public static VersionIndex createDefault(int maxRetention, String storageLocation) {
        return new VersionIndex(1, maxRetention, storageLocation, new ArrayList<>());
    }

    @JsonProperty("schemaVersion")
    public int getSchemaVersion() {
        return schemaVersion;
    }

    @JsonProperty("maxRetention")
    public int getMaxRetention() {
        return maxRetention;
    }

    public void setMaxRetention(int maxRetention) {
        this.maxRetention = maxRetention;
    }

    @JsonProperty("storageLocation")
    public String getStorageLocation() {
        return storageLocation;
    }

    public void setStorageLocation(String storageLocation) {
        this.storageLocation = Objects.requireNonNull(storageLocation, "storageLocation must not be null");
    }

    @JsonProperty("versions")
    public List<VersionEntry> getVersions() {
        return versions;
    }

    /**
     * Returns the next version number (max existing + 1, or 1 if empty).
     */
    public int getNextVersionNumber() {
        return versions.stream()
                .mapToInt(VersionEntry::getVersion)
                .max()
                .orElse(0) + 1;
    }

    /**
     * Returns the latest version entry, or null if no versions exist.
     */
    public VersionEntry getLatestVersion() {
        if (versions.isEmpty()) {
            return null;
        }
        return versions.get(versions.size() - 1);
    }

    @Override
    public String toString() {
        return "VersionIndex{schema=" + schemaVersion + ", versions=" + versions.size() + "}";
    }
}
