package io.github.sagaraggarwal86.jmeter.scm.core;

import org.apache.jmeter.gui.action.ActionNames;
import org.apache.jmeter.gui.action.Command;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.event.ActionEvent;
import java.util.HashSet;
import java.util.Set;

/**
 * Bootstrap hook for the SCM plugin. Discovered by JMeter via classpath scanning.
 * Triggers plugin initialization on first save. Does NOT create version snapshots —
 * versions are created only via manual checkpoint (Ctrl+K) or auto-checkpoint.
 */
public final class SaveCommandWrapper implements Command {

    private static final Logger log = LoggerFactory.getLogger(SaveCommandWrapper.class);

    public SaveCommandWrapper() {
        // Required by JMeter auto-discovery
    }

    @Override
    public void doAction(ActionEvent e) {
        try {
            ScmInitializer.getInstance().ensureInitializedWithContext();
        } catch (Throwable t) {
            log.error("JVCS bootstrap failed: {}", t.getMessage(), t);
        }
    }

    @Override
    public Set<String> getActionNames() {
        Set<String> names = new HashSet<>();
        names.add(ActionNames.SAVE);
        return names;
    }
}
