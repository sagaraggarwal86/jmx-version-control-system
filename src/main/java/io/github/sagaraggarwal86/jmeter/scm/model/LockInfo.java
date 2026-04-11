package io.github.sagaraggarwal86.jmeter.scm.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.LocalDateTime;
import java.util.Objects;

/**
 * Lock file content — identifies the JMeter instance holding the lock.
 */
public final class LockInfo {

    private final long pid;
    private final String hostname;
    private final LocalDateTime timestamp;
    private final String jmeterVersion;

    @JsonCreator
    public LockInfo(
            @JsonProperty("pid") long pid,
            @JsonProperty("hostname") String hostname,
            @JsonProperty("timestamp") LocalDateTime timestamp,
            @JsonProperty("jmeterVersion") String jmeterVersion) {
        this.pid = pid;
        this.hostname = Objects.requireNonNull(hostname, "hostname must not be null");
        this.timestamp = Objects.requireNonNull(timestamp, "timestamp must not be null");
        this.jmeterVersion = Objects.requireNonNull(jmeterVersion, "jmeterVersion must not be null");
    }

    @JsonProperty("pid")
    public long getPid() {
        return pid;
    }

    @JsonProperty("hostname")
    public String getHostname() {
        return hostname;
    }

    @JsonProperty("timestamp")
    public LocalDateTime getTimestamp() {
        return timestamp;
    }

    @JsonProperty("jmeterVersion")
    public String getJmeterVersion() {
        return jmeterVersion;
    }

    @Override
    public String toString() {
        return "LockInfo{pid=" + pid + ", host=" + hostname + ", time=" + timestamp + "}";
    }
}
