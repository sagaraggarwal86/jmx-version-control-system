package io.github.sagaraggarwal86.jmeter.scm.core;

import org.apache.jmeter.gui.action.ActionNames;
import org.apache.jmeter.gui.action.Command;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.util.HashSet;
import java.util.Set;

/**
 * Listens for file open/close/load actions to manage SCM plugin lifecycle.
 * Discovered by JMeter via classpath scanning (requires no-arg constructor).
 */
public final class ScmOpenListener implements Command {

    private static final Logger log = LoggerFactory.getLogger(ScmOpenListener.class);

    public ScmOpenListener() {
        // Required by JMeter auto-discovery
    }

    @Override
    public void doAction(ActionEvent e) {
        // Defer all handling — by the time invokeLater runs, JMeter's save/cancel
        // dialog will have completed, so the test plan state is final.
        SwingUtilities.invokeLater(() -> {
            try {
                ScmInitializer.getInstance().ensureInitializedWithContext();
            } catch (Throwable t) {
                log.error("SCM Plugin lifecycle failed: {}", t.getMessage(), t);
            }
        });
    }

    @Override
    public Set<String> getActionNames() {
        Set<String> names = new HashSet<>();
        names.add(ActionNames.OPEN);
        names.add(ActionNames.CLOSE);
        names.add(ActionNames.SUB_TREE_LOADED);
        return names;
    }
}
