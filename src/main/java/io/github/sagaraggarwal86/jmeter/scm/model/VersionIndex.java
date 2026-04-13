package io.github.sagaraggarwal86.jmeter.scm.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.*;

/**
 * Root object for index.json — contains schema version, settings, and version history.
 */
public final class VersionIndex {

    private final int schemaVersion;
    private final List<VersionEntry> versions;
    private final Set<Integer> pinnedVersions;
    private int maxRetention;
    private String storageLocation;

    @JsonCreator
    public VersionIndex(
            @JsonProperty("schemaVersion") int schemaVersion,
            @JsonProperty("maxRetention") int maxRetention,
            @JsonProperty("storageLocation") String storageLocation,
            @JsonProperty("versions") List<VersionEntry> versions,
            @JsonProperty("pinnedVersions") Set<Integer> pinnedVersions) {
        this.schemaVersion = schemaVersion;
        this.maxRetention = maxRetention;
        this.storageLocation = Objects.requireNonNull(storageLocation, "storageLocation must not be null");
        this.versions = versions != null ? new ArrayList<>(versions) : new ArrayList<>();
        this.pinnedVersions = pinnedVersions != null ? new HashSet<>(pinnedVersions) : new HashSet<>();
    }

    /**
     * Creates a new default index.
     */
    public static VersionIndex createDefault(int maxRetention, String storageLocation) {
        return new VersionIndex(1, maxRetention, storageLocation, new ArrayList<>(), new HashSet<>());
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
        return Collections.unmodifiableList(versions);
    }

    public void addVersion(VersionEntry entry) {
        versions.add(Objects.requireNonNull(entry, "entry must not be null"));
    }

    public void addAllVersions(List<VersionEntry> entries) {
        versions.addAll(Objects.requireNonNull(entries, "entries must not be null"));
    }

    public void removeVersionAt(int index) {
        versions.remove(index);
    }

    public void removeVersion(int versionNumber) {
        versions.removeIf(e -> e.getVersion() == versionNumber);
    }

    public void removeVersions(List<VersionEntry> entries) {
        versions.removeAll(entries);
    }

    @JsonProperty("pinnedVersions")
    public Set<Integer> getPinnedVersions() {
        return Collections.unmodifiableSet(pinnedVersions);
    }

    public boolean isPinned(int versionNumber) {
        return pinnedVersions.contains(versionNumber);
    }

    public void pin(int versionNumber) {
        pinnedVersions.add(versionNumber);
    }

    public void unpin(int versionNumber) {
        pinnedVersions.remove(versionNumber);
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
