package io.github.sagaraggarwal86.jmeter.scm.core;

import io.github.sagaraggarwal86.jmeter.scm.config.ScmConfigManager;
import io.github.sagaraggarwal86.jmeter.scm.storage.IndexManager;
import io.github.sagaraggarwal86.jmeter.scm.storage.LockManager;
import io.github.sagaraggarwal86.jmeter.scm.ui.DirtyIndicator;
import io.github.sagaraggarwal86.jmeter.scm.ui.ScmMenuHandler;
import io.github.sagaraggarwal86.jmeter.scm.ui.VersionHistoryPanel;
import org.apache.jmeter.gui.GuiPackage;
import org.apache.jmeter.gui.MainFrame;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.*;
import java.awt.*;
import java.lang.reflect.Field;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Lazy initializer for the SCM plugin. Installs toolbar, history panel, and key bindings once.
 * Re-initializes ScmContext per test plan.
 */
public final class ScmInitializer {

    private static final Logger log = LoggerFactory.getLogger(ScmInitializer.class);
    private static ScmInitializer instance;

    private final IndexManager indexManager;
    private final LockManager lockManager;
    private final List<Component> scmToolbarComponents = new ArrayList<>();
    private volatile ScmContext currentContext;
    private DirtyIndicator dirtyIndicator;
    private VersionHistoryPanel historyPanel;
    private AutoSaveScheduler autoCheckpointScheduler;
    private boolean uiInstalled;

    private ScmInitializer() {
        this.indexManager = new IndexManager();
        this.lockManager = new LockManager();
        this.autoCheckpointScheduler = new AutoSaveScheduler(this);
        this.uiInstalled = false;

        // Release lock on JMeter exit
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            ScmContext ctx = currentContext;
            if (ctx != null && !ctx.isDisposed()) {
                ctx.dispose();
                log.debug("Lock released via shutdown hook");
            }
        }, "SCM-ShutdownHook"));
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
     */
    public void initializeForTestPlan(Path jmxFile) {
        if (jmxFile == null) {
            log.debug("No test plan file — skipping SCM initialization");
            return;
        }

        try {
            autoCheckpointScheduler.stop();

            if (currentContext != null && !currentContext.isDisposed()) {
                currentContext.dispose();
            }

            currentContext = new ScmContext(jmxFile, indexManager, lockManager);
            currentContext.initialize();

            if (!uiInstalled) {
                installUi();
            }

            notifyVersionsChanged();
            autoCheckpointScheduler.start();

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

    public ScmContext getCurrentContext() {
        return currentContext;
    }

    public void notifyVersionsChanged() {
        SwingUtilities.invokeLater(() -> {
            ScmContext ctx = currentContext;
            if (ctx != null) {
                if (historyPanel != null) {
                    historyPanel.refresh(ctx);
                }
                if (dirtyIndicator != null) {
                    dirtyIndicator.refresh(ctx);
                }
            }
        });
    }

    /**
     * Restarts the auto-checkpoint scheduler with current config.
     */
    public void restartAutoCheckpoint() {
        autoCheckpointScheduler.start();
    }

    /**
     * Disposes the current context and releases the lock. Called on File > Close.
     */
    public void disposeCurrentContext() {
        autoCheckpointScheduler.stop();
        if (currentContext != null && !currentContext.isDisposed()) {
            currentContext.dispose();
            currentContext = null;
            notifyVersionsChanged();
            log.info("SCM context disposed (file closed)");
        }
    }

    public VersionHistoryPanel getHistoryPanel() {
        return historyPanel;
    }

    /**
     * Ensures the plugin UI is installed. Idempotent.
     */
    public void ensureInitialized() {
        if (uiInstalled) return;
        log.info("SCM Plugin bootstrapping");
        ScmConfigManager.ensureDefaultsPersisted();
        installUi();
    }

    /**
     * Ensures UI is installed and context is initialized for the current test plan.
     */
    public void ensureInitializedWithContext() {
        ensureInitialized();
        try {
            String testPlanFile = GuiPackage.getInstance().getTestPlanFile();
            if (testPlanFile != null) {
                Path jmxPath = Path.of(testPlanFile);
                ScmContext ctx = currentContext;
                if (ctx == null || ctx.isDisposed() || !ctx.getJmxFile().equals(jmxPath)) {
                    initializeForTestPlan(jmxPath);
                }
            }
        } catch (Exception e) {
            log.warn("Could not initialize context: {}", e.getMessage());
        }
    }

    /**
     * Installs toolbar, history panel, and key bindings. Called once.
     */
    private void installUi() {
        try {
            installToolbar();
        } catch (Exception e) {
            log.warn("Could not install toolbar: {}", e.getMessage());
        }

        try {
            installBottomPanel();
        } catch (Exception e) {
            log.warn("Could not install bottom panel: {}", e.getMessage());
        }

        try {
            installKeyBindings();
        } catch (Exception e) {
            log.warn("Could not install key bindings: {}", e.getMessage());
        }

        uiInstalled = true;
        log.info("SCM Plugin UI installed");
    }

    private void installKeyBindings() {
        MainFrame mainFrame = getMainFrame();
        if (mainFrame == null) return;

        JRootPane rootPane = mainFrame.getRootPane();
        InputMap inputMap = rootPane.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW);
        ActionMap actionMap = rootPane.getActionMap();

        inputMap.put(KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_H,
                java.awt.event.InputEvent.CTRL_DOWN_MASK), "scm.openHistory");
        actionMap.put("scm.openHistory", new javax.swing.AbstractAction() {
            @Override
            public void actionPerformed(java.awt.event.ActionEvent e) {
                if (historyPanel != null) {
                    historyPanel.toggleVisibility();
                }
            }
        });

        inputMap.put(KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_K,
                java.awt.event.InputEvent.CTRL_DOWN_MASK), "scm.createCheckpoint");
        actionMap.put("scm.createCheckpoint", new javax.swing.AbstractAction() {
            @Override
            public void actionPerformed(java.awt.event.ActionEvent e) {
                ScmMenuHandler.triggerCheckpoint(ScmInitializer.this);
            }
        });

        log.debug("Key bindings installed (Ctrl+H, Ctrl+K)");
    }

    /**
     * Sets visibility of all SCM toolbar components (buttons + indicator).
     */
    public void setToolbarVisible(boolean visible) {
        for (Component c : scmToolbarComponents) {
            c.setVisible(visible);
        }
    }

    private void installToolbar() throws Exception {
        MainFrame mainFrame = getMainFrame();
        if (mainFrame == null) {
            log.warn("MainFrame not available — toolbar not installed");
            return;
        }

        Field toolbarField = findField(mainFrame.getClass(), JToolBar.class);
        if (toolbarField == null) {
            log.warn("Toolbar field not found — toolbar not installed");
            return;
        }
        toolbarField.setAccessible(true);
        JToolBar toolbar = (JToolBar) toolbarField.get(mainFrame);
        if (toolbar == null) {
            log.warn("Toolbar is null — toolbar not installed");
            return;
        }

        // Find insertion point: before "Function Helper Dialog" button
        int insertIndex = toolbar.getComponentCount();
        for (int i = 0; i < toolbar.getComponentCount(); i++) {
            Component comp = toolbar.getComponent(i);
            if (comp instanceof JButton btn) {
                String tooltip = btn.getToolTipText();
                if (tooltip != null && tooltip.toLowerCase().contains("function helper")) {
                    insertIndex = i;
                    break;
                }
            }
        }

        // Measure reference button size from existing toolbar (e.g., help icon)
        Dimension refSize = null;
        for (int i = 0; i < toolbar.getComponentCount(); i++) {
            Component comp = toolbar.getComponent(i);
            if (comp instanceof JButton btn && btn.getIcon() != null) {
                refSize = btn.getPreferredSize();
                break;
            }
        }

        // Separator before SCM buttons
        JToolBar.Separator separator = new JToolBar.Separator();
        toolbar.add(separator, insertIndex++);
        scmToolbarComponents.add(separator);

        // Order: C, H, I, L, D
        JButton checkpointBtn = createToolbarButton("C", "Save Checkpoint (Ctrl+K)",
                () -> ScmMenuHandler.triggerCheckpoint(this));
        toolbar.add(checkpointBtn, insertIndex++);
        scmToolbarComponents.add(checkpointBtn);

        JButton historyBtn = createToolbarButton("H", "Show History (Ctrl+H)",
                () -> ScmMenuHandler.triggerShowHistory(this));
        toolbar.add(historyBtn, insertIndex++);
        scmToolbarComponents.add(historyBtn);

        dirtyIndicator = new DirtyIndicator();
        toolbar.add(dirtyIndicator, insertIndex++);
        scmToolbarComponents.add(dirtyIndicator);

        JButton breakLockBtn = createToolbarButton("L", "Break Lock",
                () -> ScmMenuHandler.triggerBreakLock(this));
        toolbar.add(breakLockBtn, insertIndex++);
        scmToolbarComponents.add(breakLockBtn);

        JButton deleteBtn = createToolbarButton("D", "Delete Versions",
                () -> ScmMenuHandler.triggerDeleteVersions(this));
        toolbar.add(deleteBtn, insertIndex);
        scmToolbarComponents.add(deleteBtn);

        // Match all SCM buttons to the reference toolbar button size
        if (refSize != null) {
            for (Component c : scmToolbarComponents) {
                if (c instanceof JButton btn) {
                    btn.setPreferredSize(refSize);
                    btn.setMinimumSize(refSize);
                    btn.setMaximumSize(refSize);
                }
            }
        }

        setToolbarVisible(ScmConfigManager.isToolbarVisible());

        toolbar.revalidate();
        toolbar.repaint();
        log.debug("Toolbar buttons and indicator installed");
    }

    private JButton createToolbarButton(String label, String tooltip, Runnable action) {
        JButton button = new JButton(label);
        button.setToolTipText(tooltip);
        button.setFocusable(false);
        button.setFont(button.getFont().deriveFont(Font.BOLD, 13f));
        button.setMargin(new Insets(2, 4, 2, 4));
        button.addActionListener(evt -> action.run());
        return button;
    }

    private void installBottomPanel() throws Exception {
        historyPanel = new VersionHistoryPanel(this);
        historyPanel.setVisible(false);

        MainFrame mainFrame = getMainFrame();
        if (mainFrame == null) {
            log.warn("MainFrame not available — panel not installed");
            return;
        }

        try {
            Field splitField = findField(mainFrame.getClass(), JSplitPane.class);
            if (splitField != null) {
                splitField.setAccessible(true);
                JSplitPane splitPane = (JSplitPane) splitField.get(mainFrame);
                if (splitPane != null) {
                    Component bottomComponent = splitPane.getBottomComponent();
                    JSplitPane bottomSplit = new JSplitPane(JSplitPane.VERTICAL_SPLIT,
                            bottomComponent, historyPanel);
                    bottomSplit.setResizeWeight(1.0);
                    bottomSplit.setOneTouchExpandable(true);
                    bottomSplit.setContinuousLayout(true);
                    bottomSplit.setDividerSize(6);
                    splitPane.setBottomComponent(bottomSplit);
                    splitPane.revalidate();
                    log.debug("Bottom panel installed in JSplitPane");
                    return;
                }
            }
        } catch (Exception e) {
            log.debug("JSplitPane reflection failed: {}", e.getMessage());
        }

        mainFrame.getContentPane().add(historyPanel, BorderLayout.SOUTH);
        mainFrame.revalidate();
        log.debug("Bottom panel installed via fallback");
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
