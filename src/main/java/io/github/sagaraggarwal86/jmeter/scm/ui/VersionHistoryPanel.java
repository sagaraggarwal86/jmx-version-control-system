package io.github.sagaraggarwal86.jmeter.scm.ui;

import io.github.sagaraggarwal86.jmeter.scm.core.ScmContext;
import io.github.sagaraggarwal86.jmeter.scm.core.ScmInitializer;
import io.github.sagaraggarwal86.jmeter.scm.model.TriggerType;
import io.github.sagaraggarwal86.jmeter.scm.model.VersionEntry;
import io.github.sagaraggarwal86.jmeter.scm.model.VersionIndex;
import io.github.sagaraggarwal86.jmeter.scm.storage.FileOperations;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.*;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import java.awt.*;
import java.nio.file.Path;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * Bottom dockable panel with JTable showing version history.
 * Header: title, version count badge, total size, dirty indicator, retention, close button.
 */
public final class VersionHistoryPanel extends JPanel {

    private static final Logger log = LoggerFactory.getLogger(VersionHistoryPanel.class);
    private static final DateTimeFormatter TIMESTAMP_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private static final Color COLOR_SAVE = new Color(40, 167, 69);
    private static final Color COLOR_SAVE_BG = new Color(40, 167, 69, 40);
    private static final Color COLOR_CHECKPOINT = new Color(111, 66, 193);
    private static final Color COLOR_CHECKPOINT_BG = new Color(111, 66, 193, 40);
    private static final Color COLOR_STEEL_BLUE = new Color(70, 130, 180);
    private static final Color COLOR_AMBER = new Color(255, 191, 0);

    private final ScmInitializer initializer;
    private final JLabel titleLabel;
    private final JLabel versionCountLabel;
    private final JLabel storageSizeLabel;
    private final JLabel dirtyLabel;
    private final JLabel retentionLabel;
    private final VersionTableModel tableModel;
    private final JTable table;

    public VersionHistoryPanel(ScmInitializer initializer) {
        this.initializer = initializer;
        setLayout(new BorderLayout());
        setPreferredSize(new Dimension(0, 200));

        // Header
        JPanel header = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 4));
        titleLabel = new JLabel("Version History");
        titleLabel.setFont(titleLabel.getFont().deriveFont(Font.BOLD));
        header.add(titleLabel);

        versionCountLabel = new JLabel("0 versions");
        versionCountLabel.setForeground(COLOR_STEEL_BLUE);
        header.add(versionCountLabel);

        storageSizeLabel = new JLabel("");
        storageSizeLabel.setForeground(Color.GRAY);
        header.add(storageSizeLabel);

        dirtyLabel = new JLabel("");
        dirtyLabel.setForeground(COLOR_AMBER);
        header.add(dirtyLabel);

        header.add(Box.createHorizontalGlue());

        retentionLabel = new JLabel("");
        retentionLabel.setForeground(Color.GRAY);
        header.add(retentionLabel);

        JButton closeButton = new JButton("Close");
        closeButton.setMargin(new Insets(1, 4, 1, 4));
        closeButton.addActionListener(e -> setVisible(false));
        header.add(closeButton);

        add(header, BorderLayout.NORTH);

        // Table
        tableModel = new VersionTableModel();
        table = new JTable(tableModel);
        table.setRowHeight(28);
        table.getColumnModel().getColumn(0).setPreferredWidth(40);  // #
        table.getColumnModel().getColumn(1).setPreferredWidth(90);  // Trigger
        table.getColumnModel().getColumn(2).setPreferredWidth(150); // Timestamp
        table.getColumnModel().getColumn(3).setPreferredWidth(200); // Note
        table.getColumnModel().getColumn(4).setPreferredWidth(200); // Actions

        // Trigger column renderer
        table.getColumnModel().getColumn(1).setCellRenderer(new TriggerCellRenderer());

        // Actions column
        table.getColumnModel().getColumn(4).setCellRenderer(new ActionCellRenderer());
        table.getColumnModel().getColumn(4).setCellEditor(new ActionCellEditor());

        JScrollPane scrollPane = new JScrollPane(table);
        add(scrollPane, BorderLayout.CENTER);

        // Legend
        JPanel legend = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 2));
        legend.add(createLegendLabel("SAVE", COLOR_SAVE));
        legend.add(new JLabel("= Auto-created on Ctrl+S"));
        legend.add(Box.createHorizontalStrut(16));
        legend.add(createLegendLabel("CHECKPOINT", COLOR_CHECKPOINT));
        legend.add(new JLabel("= Manual checkpoint by user"));
        add(legend, BorderLayout.SOUTH);
    }

    private static String formatSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format("%.1f KB", bytes / 1024.0);
        return String.format("%.1f MB", bytes / (1024.0 * 1024.0));
    }

    /**
     * Refreshes the panel with the current ScmContext state.
     */
    public void refresh(ScmContext context) {
        if (context == null || context.isDisposed()) {
            tableModel.setEntries(List.of());
            versionCountLabel.setText("0 versions");
            storageSizeLabel.setText("");
            dirtyLabel.setText("");
            retentionLabel.setText("");
            return;
        }

        VersionIndex index = context.getVersionIndex();
        List<VersionEntry> entries = new ArrayList<>(index.getVersions());
        java.util.Collections.reverse(entries);
        tableModel.setLatestVersion(index.getLatestVersion());
        tableModel.setEntries(entries);

        versionCountLabel.setText(index.getVersions().size() + " versions");

        long sizeBytes = FileOperations.calculateStorageSize(context.getStorageDir());
        storageSizeLabel.setText(formatSize(sizeBytes));

        if (context.getDirtyTracker().isDirty()) {
            VersionEntry latest = index.getLatestVersion();
            dirtyLabel.setText("Modified since v" + (latest != null ? latest.getVersion() : "?"));
        } else {
            dirtyLabel.setText("");
        }

        retentionLabel.setText("Retention: " + index.getMaxRetention());
    }

    private JLabel createLegendLabel(String text, Color color) {
        JLabel label = new JLabel(text);
        label.setOpaque(true);
        label.setBackground(color);
        label.setForeground(Color.WHITE);
        label.setFont(label.getFont().deriveFont(Font.BOLD, 10f));
        label.setBorder(BorderFactory.createEmptyBorder(1, 4, 1, 4));
        return label;
    }

    private void performRestore(int row) {
        ScmContext context = initializer.getCurrentContext();
        if (context == null || context.isReadOnly()) return;

        VersionEntry entry = tableModel.getEntryAt(row);
        int confirm = JOptionPane.showConfirmDialog(this,
                "Restore to version " + entry.getVersion() + "?\n" +
                        "Current state will be auto-saved before restoring.",
                "Confirm Restore", JOptionPane.YES_NO_OPTION);
        if (confirm != JOptionPane.YES_OPTION) return;

        SwingWorker<Void, Void> worker = new SwingWorker<>() {
            @Override
            protected Void doInBackground() throws Exception {
                context.restore(entry.getVersion());
                return null;
            }

            @Override
            protected void done() {
                try {
                    get();
                    initializer.notifyVersionsChanged();
                    JOptionPane.showMessageDialog(VersionHistoryPanel.this,
                            "Restored to version " + entry.getVersion() + ".\nPlease reload the test plan.",
                            "SCM Plugin", JOptionPane.INFORMATION_MESSAGE);
                } catch (Exception e) {
                    log.error("Restore failed: {}", e.getMessage(), e);
                    JOptionPane.showMessageDialog(VersionHistoryPanel.this,
                            "Restore failed: " + e.getMessage(),
                            "SCM Plugin", JOptionPane.ERROR_MESSAGE);
                }
            }
        };
        worker.execute();
    }

    private void performExport(int row) {
        ScmContext context = initializer.getCurrentContext();
        if (context == null) return;

        VersionEntry entry = tableModel.getEntryAt(row);
        JFileChooser chooser = new JFileChooser();
        chooser.setSelectedFile(new java.io.File("v" + entry.getVersion() + ".jmx"));
        int result = chooser.showSaveDialog(this);
        if (result != JFileChooser.APPROVE_OPTION) return;

        Path destination = chooser.getSelectedFile().toPath();
        Path snapshotPath = context.getStorageDir().resolve(entry.getFile());

        SwingWorker<Void, Void> worker = new SwingWorker<>() {
            @Override
            protected Void doInBackground() throws Exception {
                FileOperations.exportSnapshot(snapshotPath, destination);
                return null;
            }

            @Override
            protected void done() {
                try {
                    get();
                    JOptionPane.showMessageDialog(VersionHistoryPanel.this,
                            "Exported version " + entry.getVersion() + " to:\n" + destination,
                            "SCM Plugin", JOptionPane.INFORMATION_MESSAGE);
                } catch (Exception e) {
                    log.error("Export failed: {}", e.getMessage(), e);
                    JOptionPane.showMessageDialog(VersionHistoryPanel.this,
                            "Export failed: " + e.getMessage(),
                            "SCM Plugin", JOptionPane.ERROR_MESSAGE);
                }
            }
        };
        worker.execute();
    }

    private void performDelete(int row) {
        ScmContext context = initializer.getCurrentContext();
        if (context == null || context.isReadOnly()) return;

        VersionEntry entry = tableModel.getEntryAt(row);

        if (tableModel.isLatest(row)) { // R5
            JOptionPane.showMessageDialog(this,
                    "Cannot delete the latest version.",
                    "SCM Plugin", JOptionPane.WARNING_MESSAGE);
            return;
        }

        int confirm = JOptionPane.showConfirmDialog(this,
                "Delete version " + entry.getVersion() + "?\nThis action cannot be undone.",
                "Confirm Delete", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
        if (confirm != JOptionPane.YES_OPTION) return;

        SwingWorker<Void, Void> worker = new SwingWorker<>() {
            @Override
            protected Void doInBackground() throws Exception {
                context.deleteVersion(entry.getVersion());
                return null;
            }

            @Override
            protected void done() {
                try {
                    get();
                    initializer.notifyVersionsChanged();
                } catch (Exception e) {
                    log.error("Delete failed: {}", e.getMessage(), e);
                    JOptionPane.showMessageDialog(VersionHistoryPanel.this,
                            "Delete failed: " + e.getMessage(),
                            "SCM Plugin", JOptionPane.ERROR_MESSAGE);
                }
            }
        };
        worker.execute();
    }

    private static final class VersionTableModel extends AbstractTableModel {

        private static final String[] COLUMNS = {"#", "Trigger", "Timestamp", "Note", "Actions"};
        private List<VersionEntry> entries = new ArrayList<>();
        private VersionEntry latestVersion;

        public void setEntries(List<VersionEntry> entries) {
            this.entries = new ArrayList<>(entries);
            fireTableDataChanged();
        }

        public void setLatestVersion(VersionEntry latestVersion) {
            this.latestVersion = latestVersion;
        }

        public VersionEntry getEntryAt(int row) {
            return entries.get(row);
        }

        public boolean isLatest(int row) {
            return latestVersion != null && entries.get(row).getVersion() == latestVersion.getVersion();
        }

        @Override
        public int getRowCount() {
            return entries.size();
        }

        @Override
        public int getColumnCount() {
            return COLUMNS.length;
        }

        @Override
        public String getColumnName(int column) {
            return COLUMNS[column];
        }

        @Override
        public Object getValueAt(int rowIndex, int columnIndex) {
            VersionEntry entry = entries.get(rowIndex);
            return switch (columnIndex) {
                case 0 -> entry.getVersion();
                case 1 -> entry.getTrigger();
                case 2 -> entry.getTimestamp().format(TIMESTAMP_FORMAT);
                case 3 -> entry.getNote() != null ? entry.getNote() : "";
                case 4 -> entry; // Pass full entry for action buttons
                default -> null;
            };
        }

        @Override
        public boolean isCellEditable(int rowIndex, int columnIndex) {
            return columnIndex == 4; // Actions column
        }
    }

    private static final class TriggerCellRenderer extends DefaultTableCellRenderer {
        @Override
        public Component getTableCellRendererComponent(JTable table, Object value,
                                                       boolean isSelected, boolean hasFocus,
                                                       int row, int column) {
            JLabel label = (JLabel) super.getTableCellRendererComponent(
                    table, value, isSelected, hasFocus, row, column);
            if (value instanceof TriggerType trigger) {
                label.setText(trigger.name());
                label.setOpaque(true);
                if (!isSelected) {
                    label.setBackground(trigger == TriggerType.SAVE ? COLOR_SAVE_BG : COLOR_CHECKPOINT_BG);
                }
                label.setHorizontalAlignment(SwingConstants.CENTER);
            }
            return label;
        }
    }

    private final class ActionCellRenderer extends DefaultTableCellRenderer {
        private final JLabel currentLabel;
        private final JPanel actionPanel;

        ActionCellRenderer() {
            currentLabel = new JLabel("Current");
            currentLabel.setHorizontalAlignment(SwingConstants.CENTER);
            currentLabel.setForeground(COLOR_SAVE);
            currentLabel.setFont(currentLabel.getFont().deriveFont(Font.BOLD));

            actionPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 4, 0));
            actionPanel.add(new JButton("Restore"));
            actionPanel.add(new JButton("Export"));
            actionPanel.add(new JButton("Delete"));
        }

        @Override
        public Component getTableCellRendererComponent(JTable table, Object value,
                                                       boolean isSelected, boolean hasFocus,
                                                       int row, int column) {
            if (tableModel.isLatest(row)) {
                return currentLabel;
            }
            actionPanel.setBackground(isSelected ? table.getSelectionBackground() : table.getBackground());
            return actionPanel;
        }
    }

    private final class ActionCellEditor extends AbstractCellEditor implements javax.swing.table.TableCellEditor {

        private final JPanel panel;
        private final JButton restoreButton;
        private final JButton exportButton;
        private final JButton deleteButton;
        private int editingRow;

        ActionCellEditor() {
            panel = new JPanel(new FlowLayout(FlowLayout.CENTER, 4, 0));
            restoreButton = new JButton("Restore");
            exportButton = new JButton("Export");
            deleteButton = new JButton("Delete");

            panel.add(restoreButton);
            panel.add(exportButton);
            panel.add(deleteButton);

            restoreButton.addActionListener(e -> {
                fireEditingStopped();
                performRestore(editingRow);
            });
            exportButton.addActionListener(e -> {
                fireEditingStopped();
                performExport(editingRow);
            });
            deleteButton.addActionListener(e -> {
                fireEditingStopped();
                performDelete(editingRow);
            });
        }

        @Override
        public Component getTableCellEditorComponent(JTable table, Object value,
                                                     boolean isSelected, int row, int column) {
            editingRow = row;
            boolean isLatest = tableModel.isLatest(row);
            restoreButton.setEnabled(!isLatest);
            deleteButton.setEnabled(!isLatest);
            return panel;
        }

        @Override
        public Object getCellEditorValue() {
            return null;
        }
    }
}
