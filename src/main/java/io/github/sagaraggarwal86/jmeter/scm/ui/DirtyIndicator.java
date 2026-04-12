package io.github.sagaraggarwal86.jmeter.scm.ui;

import io.github.sagaraggarwal86.jmeter.scm.core.ScmContext;

import javax.swing.*;
import java.awt.*;

/**
 * Toolbar indicator button: shows "I" with colored foreground.
 * Green (clean), amber (dirty/modified), red (read-only/locked).
 */
public final class DirtyIndicator extends JButton {

    private static final Color GREEN = new Color(40, 167, 69);
    private static final Color AMBER = new Color(255, 191, 0);
    private static final Color RED = new Color(220, 53, 69);

    public DirtyIndicator() {
        super("I");
        setFocusable(false);
        setFont(getFont().deriveFont(Font.BOLD, 13f));
        setMargin(new Insets(2, 4, 2, 4));
        setForeground(GREEN);
        setToolTipText("Version Control: Clean");
        addActionListener(e -> Toast.show("I'm the status light. I don't do tricks."));
    }

    /**
     * Updates the indicator color and tooltip from the current ScmContext.
     */
    public void refresh(ScmContext context) {
        if (context == null || context.isDisposed()) {
            setForeground(GREEN);
            setToolTipText("Version Control: No active context");
        } else if (context.isReadOnly()) {
            setForeground(RED);
            setToolTipText("Version Control: Read-only (locked by another instance)");
        } else if (context.getDirtyTracker().isDirty()) {
            setForeground(AMBER);
            setToolTipText("Version Control: Modified since last snapshot");
        } else {
            setForeground(GREEN);
            setToolTipText("Version Control: Clean");
        }
    }
}
