package io.github.sagaraggarwal86.jmeter.scm.ui;

import io.github.sagaraggarwal86.jmeter.scm.core.ScmInitializer;
import org.apache.jmeter.gui.GuiPackage;
import org.apache.jmeter.gui.MainFrame;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.*;
import java.awt.*;

/**
 * Installs "Tools > Version Manager" submenu with:
 * Open Version History, Create Checkpoint, Settings, About.
 */
public final class ScmMenuHandler {

    private static final Logger log = LoggerFactory.getLogger(ScmMenuHandler.class);

    private ScmMenuHandler() {
        // utility class
    }

    /**
     * Installs the Version Manager submenu in the Tools menu.
     * Uses reflection to find the Tools JMenu. Graceful skip if not found.
     *
     * @param initializer the SCM initializer
     */
    public static void install(ScmInitializer initializer) {
        try {
            MainFrame mainFrame = GuiPackage.getInstance().getMainFrame();
            if (mainFrame == null) {
                log.warn("MainFrame not available — menu not installed");
                return;
            }

            JMenuBar menuBar = mainFrame.getJMenuBar();
            if (menuBar == null) {
                log.warn("MenuBar not available — menu not installed");
                return;
            }

            JMenu toolsMenu = findMenu(menuBar, "Tools");
            if (toolsMenu == null) {
                log.warn("Tools menu not found — menu not installed");
                return;
            }

            JMenu versionManagerMenu = new JMenu("Version Manager");

            JMenuItem openHistory = new JMenuItem("Open Version History");
            openHistory.addActionListener(e -> {
                VersionHistoryPanel panel = initializer.getHistoryPanel();
                if (panel != null) {
                    panel.setVisible(true);
                    Container parent = panel.getParent();
                    if (parent != null) {
                        parent.revalidate();
                        parent.repaint();
                    }
                }
            });
            versionManagerMenu.add(openHistory);

            JMenuItem createCheckpoint = new JMenuItem("Create Checkpoint");
            createCheckpoint.addActionListener(e -> {
                var context = initializer.getCurrentContext();
                if (context == null || context.isDisposed() || context.isReadOnly()) {
                    JOptionPane.showMessageDialog(mainFrame,
                            "No active test plan or read-only mode.",
                            "SCM Plugin", JOptionPane.WARNING_MESSAGE);
                    return;
                }
                String note = CheckpointDialog.showDialog(mainFrame);
                if (note == null) return;
                SwingWorker<Void, Void> worker = new SwingWorker<>() {
                    @Override
                    protected Void doInBackground() throws Exception {
                        context.createCheckpoint(note.isBlank() ? null : note);
                        return null;
                    }

                    @Override
                    protected void done() {
                        try {
                            get();
                            initializer.notifyVersionsChanged();
                        } catch (Exception ex) {
                            log.error("Checkpoint failed: {}", ex.getMessage(), ex);
                            JOptionPane.showMessageDialog(mainFrame,
                                    "Checkpoint failed: " + ex.getMessage(),
                                    "SCM Plugin", JOptionPane.ERROR_MESSAGE);
                        }
                    }
                };
                worker.execute();
            });
            versionManagerMenu.add(createCheckpoint);

            versionManagerMenu.addSeparator();

            JMenuItem settings = new JMenuItem("Settings");
            settings.addActionListener(e -> SettingsDialog.showDialog(mainFrame, initializer));
            versionManagerMenu.add(settings);

            JMenuItem about = new JMenuItem("About");
            about.addActionListener(e -> AboutDialog.showDialog(mainFrame));
            versionManagerMenu.add(about);

            toolsMenu.addSeparator();
            toolsMenu.add(versionManagerMenu);

            log.debug("Version Manager menu installed");
        } catch (Exception e) {
            log.warn("Could not install Tools menu: {}", e.getMessage());
        }
    }

    /**
     * Finds a JMenu by its text label in the menu bar.
     */
    private static JMenu findMenu(JMenuBar menuBar, String name) {
        for (int i = 0; i < menuBar.getMenuCount(); i++) {
            JMenu menu = menuBar.getMenu(i);
            if (menu != null && name.equalsIgnoreCase(menu.getText())) {
                return menu;
            }
        }
        return null;
    }
}
