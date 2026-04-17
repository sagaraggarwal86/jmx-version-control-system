package io.github.sagaraggarwal86.jmeter.scm.ui;

import org.apache.jmeter.util.JMeterUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.MockedStatic;

import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mockStatic;

class SettingsDialogTest {

    @TempDir
    Path tempDir;

    private MockedStatic<JMeterUtils> jmeterUtilsMock;

    @BeforeEach
    void setUp() {
        jmeterUtilsMock = mockStatic(JMeterUtils.class);
        jmeterUtilsMock.when(() -> JMeterUtils.getProperty("scm.storage.location")).thenReturn(".history");
        jmeterUtilsMock.when(() -> JMeterUtils.getProperty("scm.max.retention")).thenReturn("20");
        jmeterUtilsMock.when(() -> JMeterUtils.getProperty("scm.lock.stale.minutes")).thenReturn("60");
        jmeterUtilsMock.when(() -> JMeterUtils.getProperty("scm.autosave.enabled")).thenReturn("false");
        jmeterUtilsMock.when(() -> JMeterUtils.getProperty("scm.autosave.interval.minutes")).thenReturn("5");
        jmeterUtilsMock.when(() -> JMeterUtils.getProperty("scm.toolbar.visible")).thenReturn("true");
    }

    @AfterEach
    void tearDown() {
        jmeterUtilsMock.close();
    }

    @Test
    void backupAndDeleteCreatesZipForNonEmptyDir() throws Exception {
        Path dir = tempDir.resolve("testplan");
        Files.createDirectories(dir);
        Files.writeString(dir.resolve("index.json"), "{}");
        Files.writeString(dir.resolve("v001.jmxv"), "content");

        Method backupAndDelete = SettingsDialog.class.getDeclaredMethod("backupAndDelete", Path.class);
        backupAndDelete.setAccessible(true);

        Path zipPath = (Path) backupAndDelete.invoke(null, dir);

        assertNotNull(zipPath);
        assertTrue(Files.exists(zipPath));
        assertTrue(zipPath.getFileName().toString().endsWith(".zip"));
        assertTrue(zipPath.getFileName().toString().contains("testplan_backup_"));
    }

    @Test
    void backupAndDeleteReturnsNullForNonExistentDir() throws Exception {
        Path dir = tempDir.resolve("nonexistent");

        Method backupAndDelete = SettingsDialog.class.getDeclaredMethod("backupAndDelete", Path.class);
        backupAndDelete.setAccessible(true);

        Path result = (Path) backupAndDelete.invoke(null, dir);

        assertNull(result);
    }

    @Test
    void backupAndDeleteReturnsNullForEmptyDir() throws Exception {
        Path dir = tempDir.resolve("emptydir");
        Files.createDirectories(dir);

        Method backupAndDelete = SettingsDialog.class.getDeclaredMethod("backupAndDelete", Path.class);
        backupAndDelete.setAccessible(true);

        Path result = (Path) backupAndDelete.invoke(null, dir);

        assertNull(result);
    }

    @Test
    void backupAndDeleteRemovesOriginalFiles() throws Exception {
        Path dir = tempDir.resolve("cleanup");
        Files.createDirectories(dir);
        Files.writeString(dir.resolve("file1.txt"), "data1");
        Files.writeString(dir.resolve("file2.txt"), "data2");

        Method backupAndDelete = SettingsDialog.class.getDeclaredMethod("backupAndDelete", Path.class);
        backupAndDelete.setAccessible(true);

        backupAndDelete.invoke(null, dir);

        // Original files should be deleted
        assertFalse(Files.exists(dir.resolve("file1.txt")));
        assertFalse(Files.exists(dir.resolve("file2.txt")));
    }

    @Test
    void migrateFilesMovesFilesToNewDir() throws Exception {
        Path oldDir = tempDir.resolve("old");
        Path newDir = tempDir.resolve("new");
        Files.createDirectories(oldDir);
        Files.writeString(oldDir.resolve("index.json"), "{}");
        Files.writeString(oldDir.resolve("v001.jmxv"), "snapshot");

        Method migrateFiles = SettingsDialog.class.getDeclaredMethod(
                "migrateFiles", java.awt.Window.class, Path.class, Path.class);
        migrateFiles.setAccessible(true);

        migrateFiles.invoke(null, null, oldDir, newDir);

        assertTrue(Files.exists(newDir.resolve("index.json")));
        assertTrue(Files.exists(newDir.resolve("v001.jmxv")));
        assertFalse(Files.exists(oldDir.resolve("index.json")));
        assertFalse(Files.exists(oldDir.resolve("v001.jmxv")));
    }

    @Test
    void migrateFilesHandlesNonExistentOldDir() throws Exception {
        Path oldDir = tempDir.resolve("nonexistent");
        Path newDir = tempDir.resolve("target");

        Method migrateFiles = SettingsDialog.class.getDeclaredMethod(
                "migrateFiles", java.awt.Window.class, Path.class, Path.class);
        migrateFiles.setAccessible(true);

        assertDoesNotThrow(() -> migrateFiles.invoke(null, null, oldDir, newDir));
    }

    @Test
    void migrateFilesCreatesNewDir() throws Exception {
        Path oldDir = tempDir.resolve("source");
        Path newDir = tempDir.resolve("deep/nested/target");
        Files.createDirectories(oldDir);
        Files.writeString(oldDir.resolve("test.txt"), "data");

        Method migrateFiles = SettingsDialog.class.getDeclaredMethod(
                "migrateFiles", java.awt.Window.class, Path.class, Path.class);
        migrateFiles.setAccessible(true);

        migrateFiles.invoke(null, null, oldDir, newDir);

        assertTrue(Files.isDirectory(newDir));
        assertTrue(Files.exists(newDir.resolve("test.txt")));
    }

    @Test
    void resolveStorageDirResolvesRelativePath() throws Exception {
        Path jmxFile = tempDir.resolve("plan.jmx");

        Method resolveStorageDir = SettingsDialog.class.getDeclaredMethod(
                "resolveStorageDir", Path.class, String.class);
        resolveStorageDir.setAccessible(true);

        Path result = (Path) resolveStorageDir.invoke(null, jmxFile, ".history");

        assertTrue(result.toString().contains(".history"));
        assertTrue(result.toString().contains("plan"));
    }

    @Test
    void resolveStorageDirResolvesAbsolutePath() throws Exception {
        Path jmxFile = tempDir.resolve("plan.jmx");
        String absPath = tempDir.resolve("custom-storage").toString();

        Method resolveStorageDir = SettingsDialog.class.getDeclaredMethod(
                "resolveStorageDir", Path.class, String.class);
        resolveStorageDir.setAccessible(true);

        Path result = (Path) resolveStorageDir.invoke(null, jmxFile, absPath);

        assertTrue(result.toString().contains("plan"));
    }
}
