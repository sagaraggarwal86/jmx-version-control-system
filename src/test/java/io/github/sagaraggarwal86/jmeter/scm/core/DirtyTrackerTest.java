package io.github.sagaraggarwal86.jmeter.scm.core;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class DirtyTrackerTest {

    @TempDir
    Path tempDir;

    @Test
    void initiallyNotDirty() throws IOException {
        Path jmx = createJmxFile("content");
        DirtyTracker tracker = new DirtyTracker(jmx);

        assertFalse(tracker.isDirty());
    }

    @Test
    void markDirtySetsFlag() throws IOException {
        Path jmx = createJmxFile("content");
        DirtyTracker tracker = new DirtyTracker(jmx);

        tracker.markDirty();
        assertTrue(tracker.isDirty());
    }

    @Test
    void resetClearsDirtyFlag() throws IOException {
        Path jmx = createJmxFile("content");
        DirtyTracker tracker = new DirtyTracker(jmx);

        tracker.markDirty();
        tracker.reset();
        assertFalse(tracker.isDirty());
    }

    @Test
    void checksumNullBeforeResetComputedAfter() throws IOException {
        Path jmx = createJmxFile("content");
        DirtyTracker tracker = new DirtyTracker(jmx);

        assertNull(tracker.getLastKnownChecksum());

        tracker.reset();
        assertNotNull(tracker.getLastKnownChecksum());
        assertEquals(64, tracker.getLastKnownChecksum().length()); // SHA-256 hex
    }

    @Test
    void verifyDirtyDetectsChange() throws IOException {
        Path jmx = createJmxFile("original");
        DirtyTracker tracker = new DirtyTracker(jmx);
        tracker.reset();

        Files.writeString(jmx, "modified");
        assertTrue(tracker.verifyDirty());
        assertTrue(tracker.isDirty()); // Flag also updated
    }

    @Test
    void verifyDirtyReturnsFalseForUnchanged() throws IOException {
        Path jmx = createJmxFile("content");
        DirtyTracker tracker = new DirtyTracker(jmx);
        tracker.reset();

        assertFalse(tracker.verifyDirty());
    }

    @Test
    void resetUpdatesChecksum() throws IOException {
        Path jmx = createJmxFile("original");
        DirtyTracker tracker = new DirtyTracker(jmx);
        tracker.reset();
        String original = tracker.getLastKnownChecksum();

        Files.writeString(jmx, "modified");
        tracker.reset();

        assertNotEquals(original, tracker.getLastKnownChecksum());
        assertFalse(tracker.isDirty());
    }

    @Test
    void handlesMissingFile() {
        Path missing = tempDir.resolve("nonexistent.jmx");
        DirtyTracker tracker = new DirtyTracker(missing);

        assertNull(tracker.getLastKnownChecksum());
        assertFalse(tracker.isDirty());
    }

    private Path createJmxFile(String content) throws IOException {
        Path jmx = tempDir.resolve("test.jmx");
        Files.writeString(jmx, content);
        return jmx;
    }
}
