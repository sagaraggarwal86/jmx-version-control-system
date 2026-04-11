package io.github.sagaraggarwal86.jmeter.scm.model;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

class VersionEntryTest {

    @Test
    void constructorSetsAllFields() {
        LocalDateTime now = LocalDateTime.now();
        VersionEntry entry = new VersionEntry(1, "v001.jmxv", now, TriggerType.SAVE, "note", "abc123");

        assertEquals(1, entry.getVersion());
        assertEquals("v001.jmxv", entry.getFile());
        assertEquals(now, entry.getTimestamp());
        assertEquals(TriggerType.SAVE, entry.getTrigger());
        assertEquals("note", entry.getNote());
        assertEquals("abc123", entry.getChecksum());
    }

    @Test
    void constructorAllowsNullNote() {
        VersionEntry entry = new VersionEntry(1, "v001.jmxv", LocalDateTime.now(),
                TriggerType.CHECKPOINT, null, "abc123");
        assertNull(entry.getNote());
    }

    @Test
    void constructorRejectsNullFile() {
        assertThrows(NullPointerException.class, () ->
                new VersionEntry(1, null, LocalDateTime.now(), TriggerType.SAVE, null, "abc"));
    }

    @Test
    void constructorRejectsNullTimestamp() {
        assertThrows(NullPointerException.class, () ->
                new VersionEntry(1, "v001.jmxv", null, TriggerType.SAVE, null, "abc"));
    }

    @Test
    void constructorRejectsNullTrigger() {
        assertThrows(NullPointerException.class, () ->
                new VersionEntry(1, "v001.jmxv", LocalDateTime.now(), null, null, "abc"));
    }

    @Test
    void constructorRejectsNullChecksum() {
        assertThrows(NullPointerException.class, () ->
                new VersionEntry(1, "v001.jmxv", LocalDateTime.now(), TriggerType.SAVE, null, null));
    }

    @Test
    void equalsByVersionAndFile() {
        LocalDateTime t1 = LocalDateTime.of(2026, 1, 1, 0, 0);
        LocalDateTime t2 = LocalDateTime.of(2026, 6, 1, 0, 0);

        VersionEntry a = new VersionEntry(1, "v001.jmxv", t1, TriggerType.SAVE, null, "abc");
        VersionEntry b = new VersionEntry(1, "v001.jmxv", t2, TriggerType.CHECKPOINT, "note", "xyz");

        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
    }

    @Test
    void notEqualWhenVersionDiffers() {
        VersionEntry a = new VersionEntry(1, "v001.jmxv", LocalDateTime.now(), TriggerType.SAVE, null, "abc");
        VersionEntry b = new VersionEntry(2, "v001.jmxv", LocalDateTime.now(), TriggerType.SAVE, null, "abc");

        assertNotEquals(a, b);
    }

    @Test
    void toStringContainsKey() {
        VersionEntry entry = new VersionEntry(5, "v005.jmxv", LocalDateTime.now(),
                TriggerType.CHECKPOINT, null, "abc");
        String str = entry.toString();
        assertTrue(str.contains("v5"));
        assertTrue(str.contains("v005.jmxv"));
        assertTrue(str.contains("CHECKPOINT"));
    }
}
