package io.github.sagaraggarwal86.jmeter.scm.model;

/**
 * Trigger that caused a version snapshot to be created.
 */
public enum TriggerType {
    /**
     * Manually created by user (Ctrl+K).
     */
    CHECKPOINT,
    /**
     * Periodically created by the auto-checkpoint scheduler.
     * Saves the script to disk first, then creates a version.
     */
    AUTO_CHECKPOINT,
    /**
     * Auto-created backup of current state before a restore operation.
     */
    RESTORE
}
