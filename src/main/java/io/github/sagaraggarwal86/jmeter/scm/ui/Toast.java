package io.github.sagaraggarwal86.jmeter.scm.ui;

import javax.swing.*;
import java.awt.*;

/**
 * Non-blocking auto-dismissing notification. Shows near the bottom-right of the parent window.
 */
public final class Toast {

    private static final int DURATION_MS = 2500;
    private static final Color BG = new Color(50, 50, 50, 230);
    private static final Color FG = Color.WHITE;

    private Toast() {
    }

    /**
     * Shows a toast using the JMeter main frame as parent.
     */
    public static void show(String message) {
        Window parent = ScmMenuHandler.getParentWindow();
        show(parent, message);
    }

    /**
     * Shows a toast message that auto-dismisses after 2.5 seconds.
     */
    public static void show(Window parent, String message) {
        SwingUtilities.invokeLater(() -> {
            JWindow toast = new JWindow(parent);
            JLabel label = new JLabel(message, SwingConstants.CENTER);
            label.setForeground(FG);
            label.setFont(label.getFont().deriveFont(Font.PLAIN, 12f));
            label.setBorder(BorderFactory.createEmptyBorder(8, 16, 8, 16));
            label.setOpaque(true);
            label.setBackground(BG);

            toast.getContentPane().add(label);
            toast.pack();

            // Position bottom-right of parent
            if (parent != null && parent.isVisible()) {
                int x = parent.getX() + parent.getWidth() - toast.getWidth() - 20;
                int y = parent.getY() + parent.getHeight() - toast.getHeight() - 40;
                toast.setLocation(x, y);
            } else {
                Dimension screen = Toolkit.getDefaultToolkit().getScreenSize();
                toast.setLocation(screen.width - toast.getWidth() - 20,
                    screen.height - toast.getHeight() - 60);
            }

            toast.setAlwaysOnTop(true);
            toast.setVisible(true);

            Timer timer = new Timer(DURATION_MS, e -> {
                toast.setVisible(false);
                toast.dispose();
            });
            timer.setRepeats(false);
            timer.start();
        });
    }
}
