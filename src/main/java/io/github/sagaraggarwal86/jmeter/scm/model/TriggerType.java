package io.github.sagaraggarwal86.jmeter.scm.model;

/**
 * Trigger that caused a version snapshot to be created.
 */
public enum TriggerType {
    /**
     * Auto-created on Ctrl+S save.
     */
    SAVE,
    /**
     * Manually created by user.
     */
    CHECKPOINT
}
