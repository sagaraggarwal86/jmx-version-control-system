package io.github.sagaraggarwal86.jmeter.scm.config;

import io.github.sagaraggarwal86.jmeter.scm.model.VersionIndex;
import org.apache.jmeter.util.JMeterUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Hybrid configuration: global defaults from jmeter.properties + per-plan overrides from index.json.
 * <p>
 * Property keys:
 * <ul>
 *   <li>{@code scm.storage.location} — default storage directory (default: {@code .history})</li>
 *   <li>{@code scm.max.retention} — default max versions retained (default: 20)</li>
 *   <li>{@code scm.lock.stale.minutes} — stale lock timeout in minutes (default: 60)</li>
 * </ul>
 */
public final class ScmConfigManager {

    public static final String PROP_STORAGE_LOCATION = "scm.storage.location";
    public static final String PROP_MAX_RETENTION = "scm.max.retention";
    public static final String PROP_LOCK_STALE_MINUTES = "scm.lock.stale.minutes";
    public static final String DEFAULT_STORAGE_LOCATION = ".history";
    public static final int DEFAULT_MAX_RETENTION = 20;
    public static final int DEFAULT_LOCK_STALE_MINUTES = 60;
    private static final Logger log = LoggerFactory.getLogger(ScmConfigManager.class);

    private ScmConfigManager() {
        // utility class
    }

    /**
     * Returns the storage location, resolved with priority: index.json override > jmeter.properties > default.
     *
     * @param index the version index (may be null for initial creation)
     * @return resolved storage location
     */
    public static String getStorageLocation(VersionIndex index) {
        if (index != null && index.getStorageLocation() != null
                && !index.getStorageLocation().isBlank()) {
            return index.getStorageLocation();
        }
        return getGlobalStorageLocation();
    }

    /**
     * Returns the global storage location from jmeter.properties or the default.
     */
    public static String getGlobalStorageLocation() {
        String prop = getProperty(PROP_STORAGE_LOCATION);
        return (prop != null && !prop.isBlank()) ? prop : DEFAULT_STORAGE_LOCATION;
    }

    /**
     * Returns max retention, resolved with priority: index.json override > jmeter.properties > default.
     *
     * @param index the version index (may be null)
     * @return resolved max retention
     */
    public static int getMaxRetention(VersionIndex index) {
        if (index != null && index.getMaxRetention() > 0) {
            return index.getMaxRetention();
        }
        return getGlobalMaxRetention();
    }

    /**
     * Returns the global max retention from jmeter.properties or the default.
     */
    public static int getGlobalMaxRetention() {
        String prop = getProperty(PROP_MAX_RETENTION);
        if (prop != null && !prop.isBlank()) {
            try {
                int value = Integer.parseInt(prop.trim());
                if (value > 0) {
                    return value;
                }
                log.warn("{} must be positive, using default: {}", PROP_MAX_RETENTION, DEFAULT_MAX_RETENTION);
            } catch (NumberFormatException e) {
                log.warn("Invalid {} value '{}', using default: {}", PROP_MAX_RETENTION, prop, DEFAULT_MAX_RETENTION);
            }
        }
        return DEFAULT_MAX_RETENTION;
    }

    /**
     * Returns the stale lock timeout in minutes.
     */
    public static int getStaleLockMinutes() {
        String prop = getProperty(PROP_LOCK_STALE_MINUTES);
        if (prop != null && !prop.isBlank()) {
            try {
                int value = Integer.parseInt(prop.trim());
                if (value > 0) {
                    return value;
                }
                log.warn("{} must be positive, using default: {}", PROP_LOCK_STALE_MINUTES, DEFAULT_LOCK_STALE_MINUTES);
            } catch (NumberFormatException e) {
                log.warn("Invalid {} value '{}', using default: {}",
                        PROP_LOCK_STALE_MINUTES, prop, DEFAULT_LOCK_STALE_MINUTES);
            }
        }
        return DEFAULT_LOCK_STALE_MINUTES;
    }

    private static String getProperty(String key) {
        try {
            return JMeterUtils.getProperty(key);
        } catch (Exception e) {
            log.debug("Could not read JMeter property '{}': {}", key, e.getMessage());
            return null;
        }
    }
}
