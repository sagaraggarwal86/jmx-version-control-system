package io.github.sagaraggarwal86.jmeter.scm.ui;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class CheckpointDialogTest {

    @Test
    void checkpointResultStoresNoteAndFreeze() {
        CheckpointDialog.CheckpointResult result =
                new CheckpointDialog.CheckpointResult("my note", true);

        assertEquals("my note", result.note());
        assertTrue(result.freeze());
    }

    @Test
    void checkpointResultWithNullNote() {
        CheckpointDialog.CheckpointResult result =
                new CheckpointDialog.CheckpointResult(null, false);

        assertNull(result.note());
        assertFalse(result.freeze());
    }

    @Test
    void checkpointResultWithFreezeTrue() {
        CheckpointDialog.CheckpointResult result =
                new CheckpointDialog.CheckpointResult("frozen", true);

        assertTrue(result.freeze());
        assertEquals("frozen", result.note());
    }

    @Test
    void checkpointResultWithEmptyNote() {
        CheckpointDialog.CheckpointResult result =
                new CheckpointDialog.CheckpointResult("", false);

        assertEquals("", result.note());
    }

    @Test
    void checkpointResultEquality() {
        CheckpointDialog.CheckpointResult a =
                new CheckpointDialog.CheckpointResult("note", true);
        CheckpointDialog.CheckpointResult b =
                new CheckpointDialog.CheckpointResult("note", true);

        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
    }

    @Test
    void checkpointResultInequality() {
        CheckpointDialog.CheckpointResult a =
                new CheckpointDialog.CheckpointResult("note", true);
        CheckpointDialog.CheckpointResult b =
                new CheckpointDialog.CheckpointResult("note", false);

        assertNotEquals(a, b);
    }

    @Test
    void checkpointResultToString() {
        CheckpointDialog.CheckpointResult result =
                new CheckpointDialog.CheckpointResult("test", true);
        String str = result.toString();
        assertTrue(str.contains("test"));
        assertTrue(str.contains("true"));
    }
}
