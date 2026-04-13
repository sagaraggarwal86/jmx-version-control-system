package io.github.sagaraggarwal86.jmeter.scm.config;

import io.github.sagaraggarwal86.jmeter.scm.model.VersionIndex;
import org.apache.jmeter.util.JMeterUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Hybrid configuration: global defaults from jmeter.properties + per-plan overrides from index.json.
 * <p>
 * Property keys:
 * <ul>
 *   <li>{@code scm.storage.location} — default storage directory (default: {@code .history})</li>
 *   <li>{@code scm.max.retention} — default max versions retained (default: 20)</li>
 *   <li>{@code scm.lock.stale.minutes} — stale lock timeout in minutes (default: 60)</li>
 *   <li>{@code scm.autosave.enabled} — auto-save enabled (default: false)</li>
 *   <li>{@code scm.autosave.interval.minutes} — auto-save interval in minutes (default: 5)</li>
 * </ul>
 */
public final class ScmConfigManager {

    public static final String PROP_STORAGE_LOCATION = "scm.storage.location";
    public static final String PROP_MAX_RETENTION = "scm.max.retention";
    public static final String PROP_LOCK_STALE_MINUTES = "scm.lock.stale.minutes";
    public static final String PROP_AUTOSAVE_ENABLED = "scm.autosave.enabled";
    public static final String PROP_AUTOSAVE_INTERVAL = "scm.autosave.interval.minutes";
    public static final String PROP_TOOLBAR_VISIBLE = "scm.toolbar.visible";
    public static final String DEFAULT_STORAGE_LOCATION = ".history";
    public static final int DEFAULT_MAX_RETENTION = 20;
    public static final int DEFAULT_LOCK_STALE_MINUTES = 60;
    public static final boolean DEFAULT_AUTOSAVE_ENABLED = false;
    public static final int DEFAULT_AUTOSAVE_INTERVAL = 5;
    public static final boolean DEFAULT_TOOLBAR_VISIBLE = true;
    private static final Logger log = LoggerFactory.getLogger(ScmConfigManager.class);

    private ScmConfigManager() {
        // utility class
    }

    /**
     * Returns the global storage location from jmeter.properties or the default.
     * This is the single source of truth for storage resolution — index.json storageLocation
     * is a record-only field and is NOT used for path resolution.
     */
    public static String getGlobalStorageLocation() {
        String prop = getProperty(PROP_STORAGE_LOCATION);
        return (prop != null && !prop.isBlank()) ? prop : DEFAULT_STORAGE_LOCATION;
    }


    /**
     * Returns max retention, resolved with priority: index.json override > jmeter.properties > default.
     */
    public static int getMaxRetention(VersionIndex index) {
        if (index != null && index.getMaxRetention() > 0) {
            return index.getMaxRetention();
        }
        return getGlobalMaxRetention();
    }

    public static int getGlobalMaxRetention() {
        return getPositiveIntProperty(PROP_MAX_RETENTION, DEFAULT_MAX_RETENTION);
    }

    public static int getStaleLockMinutes() {
        return getPositiveIntProperty(PROP_LOCK_STALE_MINUTES, DEFAULT_LOCK_STALE_MINUTES);
    }

    public static boolean isAutoSaveEnabled() {
        return getBooleanProperty(PROP_AUTOSAVE_ENABLED, DEFAULT_AUTOSAVE_ENABLED);
    }

    public static int getAutoSaveIntervalMinutes() {
        return getPositiveIntProperty(PROP_AUTOSAVE_INTERVAL, DEFAULT_AUTOSAVE_INTERVAL);
    }

    public static boolean isToolbarVisible() {
        return getBooleanProperty(PROP_TOOLBAR_VISIBLE, DEFAULT_TOOLBAR_VISIBLE);
    }

    /**
     * Persists a property to user.properties and sets it in-memory.
     * Overwrites the value if the key already exists in user.properties.
     */
    public static void setAndPersist(String key, String value) {
        JMeterUtils.setProperty(key, value);
        try {
            Path userProps = getUserPropertiesPath();
            if (userProps == null) return;

            // Read existing content, replace or append
            // Escape backslashes for .properties format (\ is escape char)
            String content = Files.exists(userProps) ? Files.readString(userProps) : "";
            String escapedValue = value.replace("\\", "\\\\");
            String line = key + "=" + escapedValue;

            if (hasProperty(content, key)) {
                content = content.replaceAll("(?m)^" + Pattern.quote(key) + "=.*$",
                        java.util.regex.Matcher.quoteReplacement(line));
            } else {
                // Append with SCM header if this is the first scm property
                if (!content.contains("# JVCS")) {
                    content += "\n# JVCS Settings\n";
                }
                content += line + "\n";
            }

            Files.writeString(userProps, content, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
            log.debug("Persisted {}={} to user.properties", key, value);
        } catch (IOException e) {
            log.warn("Could not persist {} to user.properties: {}", key, e.getMessage());
        }
    }

    /**
     * Writes default SCM properties to user.properties if they are not already present.
     * Called once on plugin first activation.
     */
    public static void ensureDefaultsPersisted() {
        Path userProps = getUserPropertiesPath();
        if (userProps == null) return;

        try {
            String content = Files.exists(userProps) ? Files.readString(userProps) : "";

            Map<String, String> defaults = new LinkedHashMap<>();
            defaults.put(PROP_STORAGE_LOCATION, DEFAULT_STORAGE_LOCATION);
            defaults.put(PROP_MAX_RETENTION, String.valueOf(DEFAULT_MAX_RETENTION));
            defaults.put(PROP_LOCK_STALE_MINUTES, String.valueOf(DEFAULT_LOCK_STALE_MINUTES));
            defaults.put(PROP_AUTOSAVE_ENABLED, String.valueOf(DEFAULT_AUTOSAVE_ENABLED));
            defaults.put(PROP_AUTOSAVE_INTERVAL, String.valueOf(DEFAULT_AUTOSAVE_INTERVAL));
            defaults.put(PROP_TOOLBAR_VISIBLE, String.valueOf(DEFAULT_TOOLBAR_VISIBLE));

            StringBuilder toAppend = new StringBuilder();
            for (var entry : defaults.entrySet()) {
                if (!hasProperty(content, entry.getKey())) {
                    toAppend.append(entry.getKey()).append("=").append(entry.getValue()).append("\n");
                }
            }

            if (toAppend.length() > 0) {
                if (!content.contains("# JVCS")) {
                    toAppend.insert(0, "\n# JVCS Settings\n" +
                            "# Recommended: Use Tools > Version Control > Settings for guided migration.\n" +
                            "#\n" +
                            "# Changing storage location via this file:\n" +
                            "#   1. Close JMeter\n" +
                            "#   2. Update scm.storage.location below\n" +
                            "#   3. Manually move existing .history folders to the new location\n" +
                            "#   4. Start JMeter — the new location is used automatically\n" +
                            "#   Note: Changes while JMeter is running have no effect until restart.\n" +
                            "#\n" +
                            "# Property reference:\n" +
                            "#   scm.storage.location          — Relative (to .jmx) or absolute path (default: .history)\n" +
                            "#   scm.max.retention             — Max versions per test plan (default: 20)\n" +
                            "#   scm.lock.stale.minutes        — Lock timeout in minutes (default: 60)\n" +
                            "#   scm.autosave.enabled          — Enable auto-checkpoint (default: false)\n" +
                            "#   scm.autosave.interval.minutes — Auto-checkpoint interval in minutes (default: 5)\n" +
                            "#   scm.toolbar.visible           — Show SCM toolbar buttons (default: true)\n");
                }
                Files.writeString(userProps, content + toAppend,
                        StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
                log.info("Default SCM properties written to user.properties");
            }
        } catch (IOException e) {
            log.warn("Could not write defaults to user.properties: {}", e.getMessage());
        }
    }

    private static Path getUserPropertiesPath() {
        try {
            String jmeterHome = JMeterUtils.getJMeterHome();
            if (jmeterHome != null) {
                return Path.of(jmeterHome, "bin", "user.properties");
            }
        } catch (Exception e) {
            log.debug("Could not resolve user.properties path: {}", e.getMessage());
        }
        return null;
    }

    private static int getPositiveIntProperty(String key, int defaultValue) {
        String prop = getProperty(key);
        if (prop != null && !prop.isBlank()) {
            try {
                int value = Integer.parseInt(prop.trim());
                if (value > 0) {
                    return value;
                }
                log.warn("{} must be positive, using default: {}", key, defaultValue);
            } catch (NumberFormatException e) {
                log.warn("Invalid {} value '{}', using default: {}", key, prop, defaultValue);
            }
        }
        return defaultValue;
    }

    private static boolean getBooleanProperty(String key, boolean defaultValue) {
        String prop = getProperty(key);
        if (prop != null && !prop.isBlank()) {
            return Boolean.parseBoolean(prop.trim());
        }
        return defaultValue;
    }

    private static boolean hasProperty(String content, String key) {
        return Pattern.compile("(?m)^" + Pattern.quote(key) + "=").matcher(content).find();
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
