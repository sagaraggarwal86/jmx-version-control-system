package io.github.sagaraggarwal86.jmeter.scm.ui;

import io.github.sagaraggarwal86.jmeter.scm.core.ScmContext;
import io.github.sagaraggarwal86.jmeter.scm.core.ScmInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.*;
import javax.swing.border.TitledBorder;
import java.awt.*;

/**
 * Three-button bordered JPanel for the JMeter toolbar:
 * Save+DirtyIndicator, Manual Checkpoint, Toggle History Panel.
 */
public final class ScmToolbarGroup extends JPanel {

    private static final Logger log = LoggerFactory.getLogger(ScmToolbarGroup.class);

    private final ScmInitializer initializer;
    private final DirtyIndicator dirtyIndicator;
    private final JButton saveButton;
    private final JButton checkpointButton;
    private final JButton toggleButton;

    public ScmToolbarGroup(ScmInitializer initializer) {
        this.initializer = initializer;
        this.dirtyIndicator = new DirtyIndicator();

        setLayout(new FlowLayout(FlowLayout.LEFT, 2, 0));
        setBorder(BorderFactory.createTitledBorder(
                BorderFactory.createEtchedBorder(), "SCM Plugin",
                TitledBorder.LEFT, TitledBorder.TOP,
                new Font(Font.SANS_SERIF, Font.PLAIN, 9)));

        saveButton = new JButton("Save");
        saveButton.setToolTipText("Save test plan (Ctrl+S)");
        JPanel savePanel = new JPanel(new BorderLayout(0, 0));
        savePanel.setOpaque(false);
        savePanel.add(saveButton, BorderLayout.CENTER);
        savePanel.add(dirtyIndicator, BorderLayout.EAST);
        add(savePanel);

        checkpointButton = new JButton("Checkpoint");
        checkpointButton.setToolTipText("Create a manual checkpoint with optional note");
        checkpointButton.addActionListener(e -> createCheckpoint());
        add(checkpointButton);

        toggleButton = new JButton("History");
        toggleButton.setToolTipText("Show/hide version history panel");
        toggleButton.addActionListener(e -> toggleHistoryPanel());
        add(toggleButton);
    }

    /**
     * Refreshes the toolbar state from the current ScmContext.
     */
    public void refresh(ScmContext context) {
        if (context == null || context.isDisposed()) {
            dirtyIndicator.setDirty(false);
            checkpointButton.setEnabled(false);
            return;
        }
        dirtyIndicator.setDirty(context.getDirtyTracker().isDirty());
        checkpointButton.setEnabled(!context.isReadOnly());
    }

    private void createCheckpoint() {
        ScmContext context = initializer.getCurrentContext();
        if (context == null || context.isDisposed() || context.isReadOnly()) {
            JOptionPane.showMessageDialog(this,
                    "No active test plan or read-only mode.",
                    "SCM Plugin", JOptionPane.WARNING_MESSAGE);
            return;
        }

        String note = CheckpointDialog.showDialog(SwingUtilities.getWindowAncestor(this));

        if (note == null) {
            return;
        }

        SwingWorker<Void, Void> worker = new SwingWorker<>() {
            @Override
            protected Void doInBackground() throws Exception {
                context.createCheckpoint(note.isBlank() ? null : note);
                return null;
            }

            @Override
            protected void done() {
                try {
                    get(); // Propagate exceptions
                    initializer.notifyVersionsChanged();
                } catch (Exception e) {
                    log.error("Checkpoint failed: {}", e.getMessage(), e);
                    JOptionPane.showMessageDialog(ScmToolbarGroup.this,
                            "Checkpoint failed: " + e.getMessage(),
                            "SCM Plugin", JOptionPane.ERROR_MESSAGE);
                }
            }
        };
        worker.execute();
    }

    private void toggleHistoryPanel() {
        VersionHistoryPanel panel = initializer.getHistoryPanel();
        if (panel != null) {
            boolean visible = !panel.isVisible();
            panel.setVisible(visible);
            toggleButton.setSelected(visible);
            Container parent = panel.getParent();
            if (parent != null) {
                parent.revalidate();
                parent.repaint();
            }
        }
    }
}
