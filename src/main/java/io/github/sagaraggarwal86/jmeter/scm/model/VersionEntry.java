package io.github.sagaraggarwal86.jmeter.scm.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.LocalDateTime;
import java.util.Objects;

/**
 * A single version snapshot entry in the version index.
 */
public final class VersionEntry {

    private final int version;
    private final String file;
    private final LocalDateTime timestamp;
    private final TriggerType trigger;
    private final String note;
    private final String checksum;

    @JsonCreator
    public VersionEntry(
            @JsonProperty("version") int version,
            @JsonProperty("file") String file,
            @JsonProperty("timestamp") LocalDateTime timestamp,
            @JsonProperty("trigger") TriggerType trigger,
            @JsonProperty("note") String note,
            @JsonProperty("checksum") String checksum) {
        this.version = version;
        this.file = Objects.requireNonNull(file, "file must not be null");
        this.timestamp = Objects.requireNonNull(timestamp, "timestamp must not be null");
        this.trigger = Objects.requireNonNull(trigger, "trigger must not be null");
        this.note = note;
        this.checksum = Objects.requireNonNull(checksum, "checksum must not be null");
    }

    @JsonProperty("version")
    public int getVersion() {
        return version;
    }

    @JsonProperty("file")
    public String getFile() {
        return file;
    }

    @JsonProperty("timestamp")
    public LocalDateTime getTimestamp() {
        return timestamp;
    }

    @JsonProperty("trigger")
    public TriggerType getTrigger() {
        return trigger;
    }

    @JsonProperty("note")
    public String getNote() {
        return note;
    }

    @JsonProperty("checksum")
    public String getChecksum() {
        return checksum;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof VersionEntry that)) return false;
        return version == that.version && Objects.equals(file, that.file);
    }

    @Override
    public int hashCode() {
        return Objects.hash(version, file);
    }

    @Override
    public String toString() {
        return "VersionEntry{v" + version + ", " + file + ", " + trigger + "}";
    }
}
