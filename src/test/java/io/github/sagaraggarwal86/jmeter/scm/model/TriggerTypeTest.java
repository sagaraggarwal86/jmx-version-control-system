package io.github.sagaraggarwal86.jmeter.scm.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TriggerTypeTest {

    @Test
    void allValuesExist() {
        TriggerType[] values = TriggerType.values();
        assertEquals(3, values.length);
    }

    @Test
    void checkpointExists() {
        assertEquals(TriggerType.CHECKPOINT, TriggerType.valueOf("CHECKPOINT"));
    }

    @Test
    void autoCheckpointExists() {
        assertEquals(TriggerType.AUTO_CHECKPOINT, TriggerType.valueOf("AUTO_CHECKPOINT"));
    }

    @Test
    void restoreExists() {
        assertEquals(TriggerType.RESTORE, TriggerType.valueOf("RESTORE"));
    }

    @Test
    void invalidValueThrows() {
        assertThrows(IllegalArgumentException.class, () -> TriggerType.valueOf("INVALID"));
    }

    @Test
    void nameReturnsCorrectString() {
        assertEquals("CHECKPOINT", TriggerType.CHECKPOINT.name());
        assertEquals("AUTO_CHECKPOINT", TriggerType.AUTO_CHECKPOINT.name());
        assertEquals("RESTORE", TriggerType.RESTORE.name());
    }

    @Test
    void ordinalIsSequential() {
        assertEquals(0, TriggerType.CHECKPOINT.ordinal());
        assertEquals(1, TriggerType.AUTO_CHECKPOINT.ordinal());
        assertEquals(2, TriggerType.RESTORE.ordinal());
    }
}
