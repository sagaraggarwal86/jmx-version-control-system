package io.github.sagaraggarwal86.jmeter.scm.ui;

import javax.swing.*;
import java.awt.*;

/**
 * Amber dot overlay painted on the save button when unsaved changes exist.
 */
public final class DirtyIndicator extends JComponent {

    private static final int DOT_SIZE = 8;
    private static final Color AMBER = new Color(255, 191, 0);
    private static final Color AMBER_BORDER = new Color(204, 153, 0);

    private boolean dirty;

    public DirtyIndicator() {
        this.dirty = false;
        setOpaque(false);
        setPreferredSize(new Dimension(DOT_SIZE + 2, DOT_SIZE + 2));
    }

    public boolean isDirty() {
        return dirty;
    }

    /**
     * Sets the dirty state and repaints.
     */
    public void setDirty(boolean dirty) {
        if (this.dirty != dirty) {
            this.dirty = dirty;
            repaint();
        }
    }

    @Override
    protected void paintComponent(Graphics g) {
        if (!dirty) {
            return;
        }
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setColor(AMBER);
        g2.fillOval(1, 1, DOT_SIZE, DOT_SIZE);
        g2.setColor(AMBER_BORDER);
        g2.drawOval(1, 1, DOT_SIZE, DOT_SIZE);
        g2.dispose();
    }
}
