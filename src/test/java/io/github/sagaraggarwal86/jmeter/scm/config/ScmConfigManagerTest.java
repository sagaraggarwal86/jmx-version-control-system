package io.github.sagaraggarwal86.jmeter.scm.config;

import io.github.sagaraggarwal86.jmeter.scm.model.VersionIndex;
import org.apache.jmeter.util.JMeterUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.MockedStatic;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ScmConfigManagerTest {

    @TempDir
    Path tempDir;

    private MockedStatic<JMeterUtils> jmeterUtilsMock;
    private Path userPropsFile;

    @BeforeEach
    void setUp() throws IOException {
        userPropsFile = tempDir.resolve("bin/user.properties");
        Files.createDirectories(userPropsFile.getParent());

        jmeterUtilsMock = mockStatic(JMeterUtils.class);
        jmeterUtilsMock.when(JMeterUtils::getJMeterHome).thenReturn(tempDir.toString());
    }

    @AfterEach
    void tearDown() {
        jmeterUtilsMock.close();
    }

    // --- getGlobalStorageLocation ---

    @Test
    void globalStorageLocationReturnsDefault() {
        jmeterUtilsMock.when(() -> JMeterUtils.getProperty("scm.storage.location")).thenReturn(null);
        assertEquals(".history", ScmConfigManager.getGlobalStorageLocation());
    }

    @Test
    void globalStorageLocationReturnsProperty() {
        jmeterUtilsMock.when(() -> JMeterUtils.getProperty("scm.storage.location")).thenReturn("/custom/path");
        assertEquals("/custom/path", ScmConfigManager.getGlobalStorageLocation());
    }

    @Test
    void globalStorageLocationBlankFallsToDefault() {
        jmeterUtilsMock.when(() -> JMeterUtils.getProperty("scm.storage.location")).thenReturn("   ");
        assertEquals(".history", ScmConfigManager.getGlobalStorageLocation());
    }

    // --- getMaxRetention ---

    @Test
    void globalMaxRetentionReturnsDefault() {
        jmeterUtilsMock.when(() -> JMeterUtils.getProperty("scm.max.retention")).thenReturn(null);
        assertEquals(20, ScmConfigManager.getGlobalMaxRetention());
    }

    @Test
    void globalMaxRetentionReturnsProperty() {
        jmeterUtilsMock.when(() -> JMeterUtils.getProperty("scm.max.retention")).thenReturn("50");
        assertEquals(50, ScmConfigManager.getGlobalMaxRetention());
    }

    @Test
    void globalMaxRetentionInvalidFallsToDefault() {
        jmeterUtilsMock.when(() -> JMeterUtils.getProperty("scm.max.retention")).thenReturn("abc");
        assertEquals(20, ScmConfigManager.getGlobalMaxRetention());
    }

    @Test
    void globalMaxRetentionZeroFallsToDefault() {
        jmeterUtilsMock.when(() -> JMeterUtils.getProperty("scm.max.retention")).thenReturn("0");
        assertEquals(20, ScmConfigManager.getGlobalMaxRetention());
    }

    @Test
    void globalMaxRetentionNegativeFallsToDefault() {
        jmeterUtilsMock.when(() -> JMeterUtils.getProperty("scm.max.retention")).thenReturn("-5");
        assertEquals(20, ScmConfigManager.getGlobalMaxRetention());
    }

    @Test
    void maxRetentionUsesIndexOverride() {
        jmeterUtilsMock.when(() -> JMeterUtils.getProperty("scm.max.retention")).thenReturn("20");
        VersionIndex index = VersionIndex.createDefault(50, ".history");
        assertEquals(50, ScmConfigManager.getMaxRetention(index));
    }

    @Test
    void maxRetentionFallsToGlobalWhenIndexZero() {
        jmeterUtilsMock.when(() -> JMeterUtils.getProperty("scm.max.retention")).thenReturn("20");
        VersionIndex index = VersionIndex.createDefault(0, ".history");
        assertEquals(20, ScmConfigManager.getMaxRetention(index));
    }

    @Test
    void maxRetentionFallsToGlobalWhenIndexNull() {
        jmeterUtilsMock.when(() -> JMeterUtils.getProperty("scm.max.retention")).thenReturn("30");
        assertEquals(30, ScmConfigManager.getMaxRetention(null));
    }

    // --- getStaleLockMinutes ---

    @Test
    void staleLockMinutesReturnsDefault() {
        jmeterUtilsMock.when(() -> JMeterUtils.getProperty("scm.lock.stale.minutes")).thenReturn(null);
        assertEquals(60, ScmConfigManager.getStaleLockMinutes());
    }

    @Test
    void staleLockMinutesReturnsProperty() {
        jmeterUtilsMock.when(() -> JMeterUtils.getProperty("scm.lock.stale.minutes")).thenReturn("120");
        assertEquals(120, ScmConfigManager.getStaleLockMinutes());
    }

    // --- isAutoSaveEnabled ---

    @Test
    void autoSaveDefaultDisabled() {
        jmeterUtilsMock.when(() -> JMeterUtils.getProperty("scm.autosave.enabled")).thenReturn(null);
        assertFalse(ScmConfigManager.isAutoSaveEnabled());
    }

    @Test
    void autoSaveEnabled() {
        jmeterUtilsMock.when(() -> JMeterUtils.getProperty("scm.autosave.enabled")).thenReturn("true");
        assertTrue(ScmConfigManager.isAutoSaveEnabled());
    }

    // --- isToolbarVisible ---

    @Test
    void toolbarVisibleDefaultTrue() {
        jmeterUtilsMock.when(() -> JMeterUtils.getProperty("scm.toolbar.visible")).thenReturn(null);
        assertTrue(ScmConfigManager.isToolbarVisible());
    }

    // --- setAndPersist ---

    @Test
    void setAndPersistWritesNewProperty() throws IOException {
        Files.writeString(userPropsFile, "# existing content\n");

        ScmConfigManager.setAndPersist("scm.storage.location", ".history");

        String content = Files.readString(userPropsFile);
        assertTrue(content.contains("scm.storage.location=.history"));
        jmeterUtilsMock.verify(() -> JMeterUtils.setProperty("scm.storage.location", ".history"));
    }

    @Test
    void setAndPersistReplacesExistingProperty() throws IOException {
        Files.writeString(userPropsFile, "scm.storage.location=.old\n");

        ScmConfigManager.setAndPersist("scm.storage.location", ".new");

        String content = Files.readString(userPropsFile);
        assertTrue(content.contains("scm.storage.location=.new"));
        assertFalse(content.contains("scm.storage.location=.old"));
    }

    @Test
    void setAndPersistEscapesBackslashes() throws IOException {
        Files.writeString(userPropsFile, "");

        ScmConfigManager.setAndPersist("scm.storage.location", "C:\\Users\\test");

        String content = Files.readString(userPropsFile);
        assertTrue(content.contains("scm.storage.location=C:\\\\Users\\\\test"));
    }

    @Test
    void setAndPersistAddsScmHeaderOnFirstProperty() throws IOException {
        Files.writeString(userPropsFile, "other.prop=value\n");

        ScmConfigManager.setAndPersist("scm.storage.location", ".history");

        String content = Files.readString(userPropsFile);
        assertTrue(content.contains("# JVCS"));
    }

    // --- ensureDefaultsPersisted ---

    @Test
    void ensureDefaultsPersistedWritesAllDefaults() throws IOException {
        Files.writeString(userPropsFile, "");

        ScmConfigManager.ensureDefaultsPersisted();

        String content = Files.readString(userPropsFile);
        assertTrue(content.contains("scm.storage.location=.history"));
        assertTrue(content.contains("scm.max.retention=20"));
        assertTrue(content.contains("scm.lock.stale.minutes=60"));
        assertTrue(content.contains("scm.autosave.enabled=false"));
        assertTrue(content.contains("scm.autosave.interval.minutes=5"));
        assertTrue(content.contains("scm.toolbar.visible=true"));
    }

    @Test
    void ensureDefaultsPersistedSkipsExisting() throws IOException {
        Files.writeString(userPropsFile, "scm.storage.location=/custom\nscm.max.retention=50\n" +
                "scm.lock.stale.minutes=120\nscm.autosave.enabled=true\n" +
                "scm.autosave.interval.minutes=10\nscm.toolbar.visible=false\n");

        ScmConfigManager.ensureDefaultsPersisted();

        String content = Files.readString(userPropsFile);
        // Should not overwrite existing values
        assertTrue(content.contains("scm.storage.location=/custom"));
        assertTrue(content.contains("scm.max.retention=50"));
    }

    @Test
    void ensureDefaultsPersistedWritesMigrationInstructions() throws IOException {
        Files.writeString(userPropsFile, "");

        ScmConfigManager.ensureDefaultsPersisted();

        String content = Files.readString(userPropsFile);
        assertTrue(content.contains("Recommended: Use Tools > Version Control > Settings"));
        assertTrue(content.contains("Changes while JMeter is running have no effect until restart"));
    }
}
