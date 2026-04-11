package io.github.sagaraggarwal86.jmeter.scm.core;

import io.github.sagaraggarwal86.jmeter.scm.storage.IndexManager;
import io.github.sagaraggarwal86.jmeter.scm.storage.LockManager;
import io.github.sagaraggarwal86.jmeter.scm.ui.ScmMenuHandler;
import io.github.sagaraggarwal86.jmeter.scm.ui.ScmToolbarGroup;
import io.github.sagaraggarwal86.jmeter.scm.ui.VersionHistoryPanel;
import org.apache.jmeter.gui.GuiPackage;
import org.apache.jmeter.gui.MainFrame;
import org.apache.jmeter.gui.action.ActionNames;
import org.apache.jmeter.gui.action.ActionRouter;
import org.apache.jmeter.gui.action.Command;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.*;
import java.awt.*;
import java.lang.reflect.Field;
import java.nio.file.Path;
import java.util.Map;
import java.util.Set;

/**
 * Lazy initializer for the SCM plugin. Installs toolbar, menu, and save hook once.
 * Re-initializes ScmContext per test plan.
 */
public final class ScmInitializer {

    private static final Logger log = LoggerFactory.getLogger(ScmInitializer.class);
    private static ScmInitializer instance;

    private final IndexManager indexManager;
    private final LockManager lockManager;

    private volatile ScmContext currentContext;
    private ScmToolbarGroup toolbarGroup;
    private VersionHistoryPanel historyPanel;
    private boolean uiInstalled;

    private ScmInitializer() {
        this.indexManager = new IndexManager();
        this.lockManager = new LockManager();
        this.uiInstalled = false;
    }

    /**
     * Returns the singleton instance.
     */
    public static synchronized ScmInitializer getInstance() {
        if (instance == null) {
            instance = new ScmInitializer();
        }
        return instance;
    }

    /**
     * Finds a field of the given type in the class hierarchy.
     */
    private static Field findField(Class<?> clazz, Class<?> fieldType) {
        Class<?> current = clazz;
        while (current != null) {
            for (Field field : current.getDeclaredFields()) {
                if (fieldType.isAssignableFrom(field.getType())) {
                    return field;
                }
            }
            current = current.getSuperclass();
        }
        return null;
    }

    /**
     * Initializes the SCM plugin for the given test plan file.
     * Called on test plan open/switch. Disposes previous context.
     *
     * @param jmxFile the test plan file path
     */
    public void initializeForTestPlan(Path jmxFile) {
        if (jmxFile == null) {
            log.debug("No test plan file — skipping SCM initialization");
            return;
        }

        try {
            if (currentContext != null && !currentContext.isDisposed()) {
                currentContext.dispose();
            }

            currentContext = new ScmContext(jmxFile, indexManager, lockManager);
            currentContext.initialize();

            if (!uiInstalled) {
                installUi();
            }

            notifyVersionsChanged();

            if (currentContext.isReadOnly()) {
                SwingUtilities.invokeLater(() ->
                        JOptionPane.showMessageDialog(null,
                                "This test plan is locked by another JMeter instance.\n" +
                                        "You can view and export versions but cannot create snapshots.",
                                "SCM Plugin — Read-Only Mode", JOptionPane.INFORMATION_MESSAGE));
            }
        } catch (Exception e) {
            log.error("Failed to initialize SCM for {}: {}", jmxFile, e.getMessage(), e);
            SwingUtilities.invokeLater(() ->
                    JOptionPane.showMessageDialog(null,
                            "SCM Plugin initialization failed: " + e.getMessage(),
                            "SCM Plugin", JOptionPane.ERROR_MESSAGE));
        }
    }

    /**
     * Returns the current ScmContext, or null if none active.
     */
    public ScmContext getCurrentContext() {
        return currentContext;
    }

    /**
     * Notifies UI components that versions have changed.
     */
    public void notifyVersionsChanged() {
        SwingUtilities.invokeLater(() -> {
            ScmContext ctx = currentContext;
            if (ctx != null) {
                if (historyPanel != null) {
                    historyPanel.refresh(ctx);
                }
                if (toolbarGroup != null) {
                    toolbarGroup.refresh(ctx);
                }
            }
        });
    }

    /**
     * Returns the version history panel (may be null before UI install).
     */
    public VersionHistoryPanel getHistoryPanel() {
        return historyPanel;
    }

    /**
     * Installs toolbar group, menu, and save hook. Called once.
     */
    private void installUi() {
        try {
            installToolbar();
        } catch (Exception e) {
            log.warn("Could not install toolbar group: {}", e.getMessage());
        }

        try {
            installBottomPanel();
        } catch (Exception e) {
            log.warn("Could not install bottom panel: {}", e.getMessage());
        }

        try {
            ScmMenuHandler.install(this);
        } catch (Exception e) {
            log.warn("Could not install Tools menu: {}", e.getMessage());
        }

        try {
            installSaveHook();
        } catch (Exception e) {
            log.warn("Could not install save hook: {}", e.getMessage());
        }

        uiInstalled = true;
        log.info("SCM Plugin UI installed");
    }

    /**
     * Installs the toolbar group via MainFrame reflection.
     */
    private void installToolbar() throws Exception {
        MainFrame mainFrame = getMainFrame();
        if (mainFrame == null) {
            log.warn("MainFrame not available — toolbar not installed");
            return;
        }

        // MainFrame doesn't expose getToolBar() publicly — use reflection
        Field toolbarField = findField(mainFrame.getClass(), JToolBar.class);
        if (toolbarField == null) {
            log.warn("Toolbar field not found via reflection — toolbar group not installed");
            return;
        }
        toolbarField.setAccessible(true);
        JToolBar toolbar = (JToolBar) toolbarField.get(mainFrame);
        if (toolbar == null) {
            log.warn("Toolbar is null — toolbar group not installed");
            return;
        }

        toolbarGroup = new ScmToolbarGroup(this);
        toolbar.addSeparator();
        toolbar.add(toolbarGroup);
        toolbar.revalidate();
        toolbar.repaint();
        log.debug("Toolbar group installed");
    }

    /**
     * Installs the bottom history panel via MainFrame JSplitPane reflection.
     */
    private void installBottomPanel() throws Exception {
        historyPanel = new VersionHistoryPanel(this);
        historyPanel.setVisible(false); // Hidden by default

        MainFrame mainFrame = getMainFrame();
        if (mainFrame == null) {
            log.warn("MainFrame not available — using floating dialog fallback");
            return;
        }

        // Try to find the main JSplitPane via reflection
        try {
            Field splitField = findField(mainFrame.getClass(), JSplitPane.class);
            if (splitField != null) {
                splitField.setAccessible(true);
                JSplitPane splitPane = (JSplitPane) splitField.get(mainFrame);
                if (splitPane != null) {
                    Component bottomComponent = splitPane.getBottomComponent();

                    // Wrap existing bottom + history panel in a new split
                    JSplitPane bottomSplit = new JSplitPane(JSplitPane.VERTICAL_SPLIT,
                            bottomComponent, historyPanel);
                    bottomSplit.setResizeWeight(0.7);
                    bottomSplit.setOneTouchExpandable(true);
                    splitPane.setBottomComponent(bottomSplit);
                    splitPane.revalidate();
                    log.debug("Bottom panel installed in JSplitPane");
                    return;
                }
            }
        } catch (Exception e) {
            log.debug("JSplitPane reflection failed: {}", e.getMessage());
        }

        // Fallback: add to mainframe's content pane bottom
        mainFrame.getContentPane().add(historyPanel, BorderLayout.SOUTH);
        mainFrame.revalidate();
        log.debug("Bottom panel installed via fallback");
    }

    /**
     * Installs the save command wrapper via ActionRouter reflection.
     */
    @SuppressWarnings("unchecked")
    private void installSaveHook() throws Exception {
        ActionRouter router = ActionRouter.getInstance();

        // Access the commands map via reflection
        Field commandsField = ActionRouter.class.getDeclaredField("commands");
        commandsField.setAccessible(true);
        Map<String, Set<Command>> commands = (Map<String, Set<Command>>) commandsField.get(router);

        Set<Command> saveCommands = commands.get(ActionNames.SAVE);
        if (saveCommands != null && !saveCommands.isEmpty()) {
            Command originalCommand = saveCommands.iterator().next();
            SaveCommandWrapper wrapper = new SaveCommandWrapper(originalCommand, this);

            saveCommands.clear();
            saveCommands.add(wrapper);
            log.debug("Save hook installed");
        } else {
            log.warn("No SAVE command found to wrap");
        }
    }

    private MainFrame getMainFrame() {
        try {
            GuiPackage guiPackage = GuiPackage.getInstance();
            if (guiPackage != null) {
                return guiPackage.getMainFrame();
            }
        } catch (Exception e) {
            log.debug("Could not get MainFrame: {}", e.getMessage());
        }
        return null;
    }
}
