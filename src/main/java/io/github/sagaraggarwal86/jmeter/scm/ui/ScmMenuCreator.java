package io.github.sagaraggarwal86.jmeter.scm.ui;

import io.github.sagaraggarwal86.jmeter.scm.core.ScmInitializer;
import org.apache.jmeter.gui.plugin.MenuCreator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.*;

/**
 * JMeter MenuCreator entry point. Discovered at startup via classpath scanning.
 * Provides "Version Manager" submenu under Tools menu.
 * Also schedules deferred auto-init for files pre-loaded at startup.
 */
public final class ScmMenuCreator implements MenuCreator {

    private static final Logger log = LoggerFactory.getLogger(ScmMenuCreator.class);

    public ScmMenuCreator() {
        log.info("SCM Plugin discovered by JMeter");

        // Deferred auto-init: catches files loaded at startup (CLI -t or last-used auto-open).
        // No OPEN action fires for these — this invokeLater runs after the startup file load completes.
        SwingUtilities.invokeLater(() -> SwingUtilities.invokeLater(() -> {
            try {
                ScmInitializer.getInstance().ensureInitializedWithContext();
            } catch (Throwable t) {
                log.debug("Deferred startup init skipped: {}", t.getMessage());
            }
        }));
    }

    @Override
    public JMenu[] getTopLevelMenus() {
        return new JMenu[0];
    }

    @Override
    public JMenuItem[] getMenuItemsAtLocation(MENU_LOCATION location) {
        if (location == MENU_LOCATION.TOOLS) {
            return ScmMenuHandler.createMenuItems(ScmInitializer.getInstance());
        }
        return new JMenuItem[0];
    }

    @Override
    public void localeChanged() {
        // no-op
    }

    @Override
    public boolean localeChanged(MenuElement menu) {
        return false;
    }
}
