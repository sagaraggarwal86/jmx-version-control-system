package io.github.sagaraggarwal86.jmeter.scm.ui;

import io.github.sagaraggarwal86.jmeter.scm.config.ScmConfigManager;
import io.github.sagaraggarwal86.jmeter.scm.core.ScmContext;
import io.github.sagaraggarwal86.jmeter.scm.core.ScmInitializer;
import io.github.sagaraggarwal86.jmeter.scm.model.TriggerType;
import io.github.sagaraggarwal86.jmeter.scm.model.VersionEntry;
import io.github.sagaraggarwal86.jmeter.scm.model.VersionIndex;
import io.github.sagaraggarwal86.jmeter.scm.storage.AuditLogger;
import io.github.sagaraggarwal86.jmeter.scm.storage.FileOperations;
import org.apache.jmeter.gui.GuiPackage;
import org.apache.jmeter.gui.action.ActionNames;
import org.apache.jmeter.gui.action.ActionRouter;
import org.apache.jmeter.save.SaveService;
import org.apache.jmeter.services.FileServer;
import org.apache.jorphan.collections.HashTree;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.*;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.TableCellRenderer;
import java.awt.*;
import java.io.IOException;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.stream.Collectors;

/**
 * Bottom dockable panel with JTable showing version history.
 * Header: title, version count badge, total size, dirty indicator, retention, close button.
 */
public final class VersionHistoryPanel extends JPanel {

    private static final Logger log = LoggerFactory.getLogger(VersionHistoryPanel.class);

    private static final Color COLOR_CHECKPOINT = new Color(111, 66, 193);
    private static final Color COLOR_CHECKPOINT_BG = new Color(111, 66, 193, 40);
    private static final Color COLOR_AUTO_CHECKPOINT = new Color(0, 123, 255);
    private static final Color COLOR_AUTO_CHECKPOINT_BG = new Color(0, 123, 255, 40);
    private static final Color COLOR_RESTORE = new Color(230, 126, 34);
    private static final Color COLOR_RESTORE_BG = new Color(230, 126, 34, 40);
    private static final Color COLOR_STEEL_BLUE = new Color(70, 130, 180);
    private static final Color COLOR_AMBER = new Color(255, 191, 0);
    private static final Color COLOR_FREEZE_GREEN = new Color(40, 167, 69);
    private static final Color COLOR_UNFREEZE_RED = new Color(220, 53, 69);

    private final ScmInitializer initializer;
    private Font boldFont;
    private final JLabel titleLabel;
    private final JLabel versionCountLabel;
    private final JLabel storageSizeLabel;
    private final JLabel dirtyLabel;
    private final JLabel retentionLabel;
    private final VersionTableModel tableModel;
    private final JTable table;
    private final JLabel periodicLegendLabel;

    public VersionHistoryPanel(ScmInitializer initializer) {
        this.initializer = initializer;
        setLayout(new BorderLayout());
        setPreferredSize(new Dimension(0, 300));
        setMinimumSize(new Dimension(0, 100));

        // Header
        JPanel header = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 4));
        titleLabel = new JLabel("Version History (Ctrl+H)");
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
        tableModel.setNoteEditHandler(this::performEditNote);
        table = new JTable(tableModel) {
            @Override
            public Component prepareRenderer(javax.swing.table.TableCellRenderer renderer, int row, int column) {
                Component c = super.prepareRenderer(renderer, row, column);
                VersionEntry entry = tableModel.getEntryAt(row);
                if (tableModel.pinnedVersions.contains(entry.getVersion())) {
                    c.setFont(boldFont);
                }
                return c;
            }

            @Override
            public String getToolTipText(java.awt.event.MouseEvent e) {
                int row = rowAtPoint(e.getPoint());
                int col = columnAtPoint(e.getPoint());
                if (row >= 0 && col == 5) {
                    if (tableModel.isFileMissing(row)) {
                        return "Snapshot file missing from disk — restore and export unavailable";
                    }
                    if (tableModel.isLatest(row)) {
                        return "This is the current version — restore and freeze unavailable";
                    }
                }
                return super.getToolTipText(e);
            }
        };
        table.setRowHeight(28);
        table.setAutoResizeMode(JTable.AUTO_RESIZE_ALL_COLUMNS);
        table.setFillsViewportHeight(true);
        tableModel.ownerTable = table;
        boldFont = table.getFont().deriveFont(Font.BOLD);

        // Fixed columns with min/max; Note is the flexible column (no maxWidth)
        table.getColumnModel().getColumn(0).setMinWidth(30);   // Keep
        table.getColumnModel().getColumn(0).setMaxWidth(40);
        table.getColumnModel().getColumn(1).setMinWidth(30);   // #
        table.getColumnModel().getColumn(1).setMaxWidth(50);
        table.getColumnModel().getColumn(2).setMinWidth(110);  // Trigger
        table.getColumnModel().getColumn(2).setMaxWidth(140);
        table.getColumnModel().getColumn(3).setMinWidth(130);  // Timestamp
        table.getColumnModel().getColumn(3).setMaxWidth(170);
        table.getColumnModel().getColumn(4).setMinWidth(100);  // Note (flexible, absorbs remaining space)
        table.getColumnModel().getColumn(4).setPreferredWidth(200);
        table.getColumnModel().getColumn(5).setMinWidth(270);  // Actions (Restore, Freeze/Unfreeze, Export)
        table.getColumnModel().getColumn(5).setMaxWidth(350);

        // Column header tooltips
        String[] tooltips = {
                "Select versions for bulk deletion",
                "Version number (monotonically increasing)",
                "What created this snapshot: CHECKPOINT, AUTO_CHECKPOINT, or RESTORE",
                "When this snapshot was created",
                "Optional note — double-click to edit",
                "Restore, Freeze/Unfreeze, or Export this version"
        };
        table.getTableHeader().setDefaultRenderer(new TooltipHeaderRenderer(table, tooltips));

        // Select-all checkbox header for column 0
        table.getColumnModel().getColumn(0).setHeaderRenderer(new SelectAllHeaderRenderer(table, tableModel));
        table.getTableHeader().addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseClicked(java.awt.event.MouseEvent e) {
                int col = table.columnAtPoint(e.getPoint());
                if (col == 0) {
                    if (tableModel.isAllSelected()) {
                        tableModel.deselectAll();
                    } else {
                        tableModel.selectAll();
                    }
                    table.getTableHeader().repaint();
                }
            }
        });

        // Selection checkbox renderer — disabled for latest and frozen rows
        table.getColumnModel().getColumn(0).setCellRenderer(new SelectionCheckboxRenderer());

        // Trigger column renderer
        table.getColumnModel().getColumn(2).setCellRenderer(new TriggerCellRenderer());

        // Note column renderer — shows "Current version" for latest
        table.getColumnModel().getColumn(4).setCellRenderer(new NoteCellRenderer());

        // Timestamp column — relative time with full timestamp tooltip
        table.getColumnModel().getColumn(3).setCellRenderer(new TimestampCellRenderer());

        // Actions column
        table.getColumnModel().getColumn(5).setCellRenderer(new ActionCellRenderer());
        table.getColumnModel().getColumn(5).setCellEditor(new ActionCellEditor());

        JScrollPane scrollPane = new JScrollPane(table,
                JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED,
                JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        add(scrollPane, BorderLayout.CENTER);

        // Legend
        JPanel legend = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 2));
        legend.add(createLegendLabel("CHECKPOINT", COLOR_CHECKPOINT));
        legend.add(new JLabel("= Manual (Ctrl+K)"));
        legend.add(Box.createHorizontalStrut(12));
        legend.add(createLegendLabel("AUTO_CHECKPOINT", COLOR_AUTO_CHECKPOINT));
        int interval = ScmConfigManager.getAutoSaveIntervalMinutes();
        periodicLegendLabel = new JLabel("= Periodic (" + interval + "m)");
        legend.add(periodicLegendLabel);
        legend.add(Box.createHorizontalStrut(12));
        legend.add(createLegendLabel("RESTORE", COLOR_RESTORE));
        legend.add(new JLabel("= Pre-restore backup"));
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
        tableModel.setPinnedVersions(index.getPinnedVersions());

        // Detect orphaned index entries (missing .jmxv files)
        Set<Integer> missing = new HashSet<>();
        Path storageDir = context.getStorageDir();
        for (VersionEntry entry : entries) {
            if (!java.nio.file.Files.exists(storageDir.resolve(entry.getFile()))) {
                missing.add(entry.getVersion());
            }
        }
        tableModel.setMissingFiles(missing);

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

        int interval = ScmConfigManager.getAutoSaveIntervalMinutes();
        periodicLegendLabel.setText("= Periodic (" + interval + "m)");
    }

    /**
     * Toggles visibility and revalidates the parent split pane chain.
     */
    public void toggleVisibility() {
        boolean showing = !isVisible();
        setVisible(showing);

        // Find the enclosing JSplitPane and set divider position
        Container parent = getParent();
        while (parent != null) {
            if (parent instanceof JSplitPane split) {
                if (showing) {
                    // Give history panel ~30% of the split height
                    split.setDividerLocation(0.7);
                }
                split.revalidate();
                split.repaint();
                break;
            }
            parent = parent.getParent();
        }
    }

    /**
     * Deletes all selected (checked) versions. Skips latest and frozen versions.
     * Called by menu/toolbar "Delete Versions" action.
     */
    public void deleteSelectedVersions() {
        ScmContext context = initializer.getCurrentContext();
        if (context == null || context.isReadOnly()) {
            JOptionPane.showMessageDialog(this,
                    "No active test plan or read-only mode.",
                    "SCM Plugin", JOptionPane.WARNING_MESSAGE);
            return;
        }

        Set<Integer> selected = tableModel.getSelectedVersions();
        if (selected.isEmpty()) {
            JOptionPane.showMessageDialog(this,
                    "No versions selected. Use the checkboxes to select versions for deletion.",
                    "SCM Plugin", JOptionPane.INFORMATION_MESSAGE);
            return;
        }

        // Filter out latest and frozen versions
        VersionIndex index = context.getVersionIndex();
        VersionEntry latest = index.getLatestVersion();
        Set<Integer> deletable = new HashSet<>();
        int skippedFrozen = 0;
        int skippedLatest = 0;

        for (int version : selected) {
            if (latest != null && version == latest.getVersion()) {
                skippedLatest++;
            } else if (index.isPinned(version)) {
                skippedFrozen++;
            } else {
                deletable.add(version);
            }
        }

        if (deletable.isEmpty()) {
            StringBuilder msg = new StringBuilder("No deletable versions in selection.");
            if (skippedLatest > 0) msg.append("\n• Latest version cannot be deleted.");
            if (skippedFrozen > 0) msg.append("\n• ").append(skippedFrozen).append(" frozen version(s) skipped.");
            JOptionPane.showMessageDialog(this, msg.toString(),
                    "SCM Plugin", JOptionPane.INFORMATION_MESSAGE);
            return;
        }

        StringBuilder confirmMsg = new StringBuilder("Delete " + deletable.size() + " version(s)?");
        if (skippedLatest > 0 || skippedFrozen > 0) {
            confirmMsg.append("\n\nSkipped:");
            if (skippedLatest > 0) confirmMsg.append("\n• Latest version (always preserved)");
            if (skippedFrozen > 0) confirmMsg.append("\n• ").append(skippedFrozen).append(" frozen version(s)");
        }
        confirmMsg.append("\n\nThis action cannot be undone.");

        int confirm = JOptionPane.showConfirmDialog(this, confirmMsg.toString(),
                "Delete Versions", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
        if (confirm != JOptionPane.YES_OPTION) return;

        SwingWorker<Integer, Void> worker = new SwingWorker<>() {
            @Override
            protected Integer doInBackground() throws Exception {
                int deleted = 0;
                for (int version : deletable) {
                    try {
                        context.deleteVersion(version);
                        deleted++;
                    } catch (Exception e) {
                        log.warn("Could not delete version {}: {}", version, e.getMessage());
                    }
                }
                return deleted;
            }

            @Override
            protected void done() {
                try {
                    int deleted = get();
                    initializer.notifyVersionsChanged();
                    Toast.show("Deleted " + deleted + " version(s)");
                } catch (Exception e) {
                    Throwable cause = e.getCause() != null ? e.getCause() : e;
                    log.error("Delete selected versions failed: {}", cause.getMessage(), e);
                    JOptionPane.showMessageDialog(VersionHistoryPanel.this,
                            "Delete failed: " + cause.getMessage(),
                            "SCM Plugin", JOptionPane.ERROR_MESSAGE);
                }
            }
        };
        worker.execute();
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
        if (context == null) return;
        if (context.isReadOnly()) {
            JOptionPane.showMessageDialog(this,
                    "Cannot restore — this test plan is in read-only mode.\n" +
                            "Another JMeter instance holds the lock.",
                    "SCM Plugin", JOptionPane.WARNING_MESSAGE);
            return;
        }

        VersionEntry entry = tableModel.getEntryAt(row);
        int confirm = JOptionPane.showConfirmDialog(this,
                "Restore to version " + entry.getVersion() + "?\n" +
                        "Current state will be auto-saved before restoring.",
                "Confirm Restore", JOptionPane.YES_NO_OPTION);
        if (confirm != JOptionPane.YES_OPTION) return;

        Path jmxFile = context.getJmxFile();

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
                    reloadTestPlan(jmxFile);
                } catch (Exception e) {
                    Throwable cause = e.getCause() != null ? e.getCause() : e;
                    log.warn("Restore failed: {}", cause.getMessage());
                    JOptionPane.showMessageDialog(VersionHistoryPanel.this,
                            "Restore failed: " + cause.getMessage(),
                            "SCM Plugin", JOptionPane.ERROR_MESSAGE);
                }
            }
        };
        worker.execute();
    }

    /**
     * Reloads the .jmx file into JMeter's GUI after a restore.
     * Parses the file off-EDT, then applies the tree on EDT.
     */
    private void reloadTestPlan(Path jmxFile) {
        java.io.File file = jmxFile.toFile();

        SwingWorker<HashTree, Void> worker = new SwingWorker<>() {
            @Override
            protected HashTree doInBackground() throws Exception {
                FileServer.getFileServer().setBaseForScript(file);
                return SaveService.loadTree(file);
            }

            @Override
            protected void done() {
                try {
                    HashTree tree = get();
                    GuiPackage gui = GuiPackage.getInstance();
                    gui.getTreeModel().clearTestPlan();
                    gui.addSubTree(tree);
                    gui.setTestPlanFile(file.getAbsolutePath());
                    gui.updateCurrentGui();

                    JTree jTree = gui.getMainFrame().getTree();
                    if (jTree != null) {
                        jTree.expandRow(0);
                    }

                    // Normalize file: JMeter's save may produce slightly different XML
                    // than the original. Reset dirty tracker so auto-checkpoint doesn't
                    // create a redundant version from the re-serialized output.
                    try {
                        ActionRouter.getInstance().doActionNow(
                                new java.awt.event.ActionEvent(this,
                                        java.awt.event.ActionEvent.ACTION_PERFORMED, ActionNames.SAVE));
                        ScmContext ctx = initializer.getCurrentContext();
                        if (ctx != null && !ctx.isDisposed()) {
                            ctx.getDirtyTracker().reset();
                        }
                    } catch (Exception ex) {
                        log.debug("Post-restore save normalization failed: {}", ex.getMessage());
                    }

                    Toast.show("Restored and reloaded");
                    log.info("Test plan reloaded after restore");
                } catch (Exception e) {
                    log.warn("Auto-reload failed: {}", e.getMessage());
                    JOptionPane.showMessageDialog(VersionHistoryPanel.this,
                            "Restored successfully but auto-reload failed.\n" +
                                    "Please reopen the file manually (Ctrl+O).",
                            "SCM Plugin", JOptionPane.WARNING_MESSAGE);
                }
            }
        };
        worker.execute();
    }

    private void performExport(int row) {
        ScmContext context = initializer.getCurrentContext();
        if (context == null) return;

        VersionEntry entry = tableModel.getEntryAt(row);
        String jmxName = context.getJmxFile().getFileName().toString().replaceFirst("\\.[^.]+$", "");
        JFileChooser chooser = new JFileChooser();
        chooser.setSelectedFile(new java.io.File(jmxName + "_v" + entry.getVersion() + ".jmx"));
        chooser.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter("JMeter (.jmx)", "jmx"));
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
                    AuditLogger.logExport(context.getStorageDir(), entry.getVersion(),
                            destination.getFileName().toString());
                    Toast.show("Exported v" + entry.getVersion() + " to " + destination.getFileName());
                } catch (Exception e) {
                    Throwable cause = e.getCause() != null ? e.getCause() : e;
                    log.warn("Export failed: {}", cause.getMessage());
                    JOptionPane.showMessageDialog(VersionHistoryPanel.this,
                            "Export failed: " + cause.getMessage(),
                            "SCM Plugin", JOptionPane.ERROR_MESSAGE);
                }
            }
        };
        worker.execute();
    }

    private void performTogglePin(int versionNumber) {
        ScmContext context = initializer.getCurrentContext();
        if (context == null || context.isDisposed()) return;

        VersionIndex index = context.getVersionIndex();
        boolean wasPinned = index.isPinned(versionNumber);
        if (wasPinned) {
            index.unpin(versionNumber);
        } else {
            index.pin(versionNumber);
            tableModel.selectedVersions.remove(versionNumber);
        }

        try {
            context.getIndexManager().save(context.getStorageDir(), index);
            if (wasPinned) {
                AuditLogger.logUnpin(context.getStorageDir(), versionNumber);
            } else {
                AuditLogger.logPin(context.getStorageDir(), versionNumber);
            }
        } catch (IOException e) {
            log.error("Failed to save pin state: {}", e.getMessage(), e);
        }

        initializer.notifyVersionsChanged();
    }

    private void performEditNote(int versionNumber, String newNote) {
        ScmContext context = initializer.getCurrentContext();
        if (context == null || context.isDisposed() || context.isReadOnly()) return;

        VersionIndex index = context.getVersionIndex();
        for (VersionEntry entry : index.getVersions()) {
            if (entry.getVersion() == versionNumber) {
                entry.setNote(newNote.isBlank() ? null : newNote);
                break;
            }
        }

        try {
            context.getIndexManager().save(context.getStorageDir(), index);
        } catch (IOException e) {
            log.error("Failed to save note: {}", e.getMessage(), e);
        }
    }

    private static final class VersionTableModel extends AbstractTableModel {

        private static final String[] COLUMNS = {"", "#", "Trigger", "Timestamp", "Note", "Actions"};
        private JTable ownerTable;
        private Set<Integer> missingFiles = new HashSet<>();
        private List<VersionEntry> entries = new ArrayList<>();
        private Set<Integer> pinnedVersions = new HashSet<>();
        private final Set<Integer> selectedVersions = new HashSet<>();
        private VersionEntry latestVersion;
        private BiConsumer<Integer, String> noteEditHandler;

        public void setEntries(List<VersionEntry> entries) {
            this.entries = new ArrayList<>(entries);
            if (!selectedVersions.isEmpty()) {
                selectedVersions.retainAll(
                        entries.stream()
                                .map(VersionEntry::getVersion)
                                .filter(this::isSelectable)
                                .collect(Collectors.toSet()));
            }
            fireTableDataChanged();
            if (ownerTable != null) {
                ownerTable.getTableHeader().repaint();
            }
        }

        public void setLatestVersion(VersionEntry latestVersion) {
            this.latestVersion = latestVersion;
        }

        public void setPinnedVersions(Set<Integer> pinnedVersions) {
            this.pinnedVersions = pinnedVersions != null ? pinnedVersions : new HashSet<>();
        }

        public void setMissingFiles(Set<Integer> missingFiles) {
            this.missingFiles = missingFiles != null ? missingFiles : new HashSet<>();
        }

        public boolean isFileMissing(int row) {
            return missingFiles.contains(entries.get(row).getVersion());
        }

        public void setNoteEditHandler(BiConsumer<Integer, String> handler) {
            this.noteEditHandler = handler;
        }

        public VersionEntry getEntryAt(int row) {
            return entries.get(row);
        }

        public boolean isLatest(int row) {
            return latestVersion != null && entries.get(row).getVersion() == latestVersion.getVersion();
        }

        private boolean isSelectable(int version) {
            return (latestVersion == null || version != latestVersion.getVersion())
                    && !pinnedVersions.contains(version);
        }

        public Set<Integer> getSelectedVersions() {
            return new HashSet<>(selectedVersions);
        }

        public void selectAll() {
            selectedVersions.clear();
            for (VersionEntry entry : entries) {
                int version = entry.getVersion();
                if (isSelectable(version)) {
                    selectedVersions.add(version);
                }
            }
            fireTableDataChanged();
        }

        public void deselectAll() {
            selectedVersions.clear();
            fireTableDataChanged();
        }

        public boolean isAllSelected() {
            if (entries.isEmpty()) return false;
            boolean hasSelectable = false;
            for (VersionEntry entry : entries) {
                int version = entry.getVersion();
                if (!isSelectable(version)) continue;
                hasSelectable = true;
                if (!selectedVersions.contains(version)) return false;
            }
            return hasSelectable;
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
        public Class<?> getColumnClass(int columnIndex) {
            if (columnIndex == 0) return Boolean.class;
            return super.getColumnClass(columnIndex);
        }

        @Override
        public Object getValueAt(int rowIndex, int columnIndex) {
            VersionEntry entry = entries.get(rowIndex);
            return switch (columnIndex) {
                case 0 -> selectedVersions.contains(entry.getVersion());
                case 1 -> entry.getVersion();
                case 2 -> entry.getTrigger();
                case 3 -> entry.getTimestamp();
                case 4 -> entry.getNote() != null ? entry.getNote() : "";
                case 5 -> entry;
                default -> null;
            };
        }

        @Override
        public void setValueAt(Object aValue, int rowIndex, int columnIndex) {
            if (columnIndex == 0) {
                int version = entries.get(rowIndex).getVersion();
                if (latestVersion != null && version == latestVersion.getVersion()) return;
                if (selectedVersions.contains(version)) {
                    selectedVersions.remove(version);
                } else {
                    selectedVersions.add(version);
                }
                fireTableCellUpdated(rowIndex, columnIndex);
                if (ownerTable != null) {
                    ownerTable.getTableHeader().repaint();
                }
            } else if (columnIndex == 4 && noteEditHandler != null) {
                String newNote = aValue != null ? aValue.toString() : "";
                noteEditHandler.accept(entries.get(rowIndex).getVersion(), newNote);
            }
        }

        @Override
        public boolean isCellEditable(int rowIndex, int columnIndex) {
            if (columnIndex == 0) {
                return isSelectable(entries.get(rowIndex).getVersion());
            }
            return columnIndex == 4 || columnIndex == 5;
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
                    label.setBackground(switch (trigger) {
                        case CHECKPOINT -> COLOR_CHECKPOINT_BG;
                        case AUTO_CHECKPOINT -> COLOR_AUTO_CHECKPOINT_BG;
                        case RESTORE -> COLOR_RESTORE_BG;
                    });
                }
                label.setHorizontalAlignment(SwingConstants.CENTER);
            }
            return label;
        }
    }

    private static final class TimestampCellRenderer extends DefaultTableCellRenderer {
        @Override
        public Component getTableCellRendererComponent(JTable table, Object value,
                                                       boolean isSelected, boolean hasFocus,
                                                       int row, int column) {
            JLabel label = (JLabel) super.getTableCellRendererComponent(
                    table, value, isSelected, hasFocus, row, column);
            if (value instanceof LocalDateTime ts) {
                label.setText(TimeFormatUtils.formatRelative(ts));
                label.setToolTipText(ts.format(TimeFormatUtils.TIMESTAMP_FORMAT));
            }
            return label;
        }
    }

    /**
     * Header renderer that adds per-column tooltips while preserving the default look.
     */
    private static final class TooltipHeaderRenderer implements TableCellRenderer {
        private final TableCellRenderer delegate;
        private final String[] tooltips;

        TooltipHeaderRenderer(JTable table, String[] tooltips) {
            this.delegate = table.getTableHeader().getDefaultRenderer();
            this.tooltips = tooltips;
        }

        @Override
        public Component getTableCellRendererComponent(JTable table, Object value,
                                                       boolean isSelected, boolean hasFocus,
                                                       int row, int column) {
            Component c = delegate.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
            if (c instanceof JComponent jc && column < tooltips.length) {
                jc.setToolTipText(tooltips[column]);
            }
            return c;
        }
    }

    private final class SelectionCheckboxRenderer implements TableCellRenderer {
        private final JCheckBox checkBox = new JCheckBox();

        SelectionCheckboxRenderer() {
            checkBox.setHorizontalAlignment(SwingConstants.CENTER);
        }

        @Override
        public Component getTableCellRendererComponent(JTable table, Object value,
                                                       boolean isSelected, boolean hasFocus,
                                                       int row, int column) {
            checkBox.setSelected(value instanceof Boolean && (Boolean) value);
            boolean editable = tableModel.isCellEditable(row, column);
            checkBox.setEnabled(editable);
            checkBox.setBackground(isSelected ? table.getSelectionBackground() : table.getBackground());
            return checkBox;
        }
    }

    private static final class SelectAllHeaderRenderer implements TableCellRenderer {
        private final JCheckBox checkBox = new JCheckBox();
        private final VersionTableModel model;

        SelectAllHeaderRenderer(JTable table, VersionTableModel model) {
            this.model = model;
            checkBox.setHorizontalAlignment(SwingConstants.CENTER);
            checkBox.setToolTipText("Select/deselect all versions for deletion");
            checkBox.setBorderPainted(true);
        }

        @Override
        public Component getTableCellRendererComponent(JTable table, Object value,
                                                       boolean isSelected, boolean hasFocus,
                                                       int row, int column) {
            checkBox.setSelected(model.isAllSelected());
            checkBox.setBackground(table.getTableHeader().getBackground());
            return checkBox;
        }
    }

    private final class NoteCellRenderer extends DefaultTableCellRenderer {
        @Override
        public Component getTableCellRendererComponent(JTable table, Object value,
                                                       boolean isSelected, boolean hasFocus,
                                                       int row, int column) {
            JLabel label = (JLabel) super.getTableCellRendererComponent(
                    table, value, isSelected, hasFocus, row, column);
            String note = value != null ? value.toString() : "";
            if (tableModel.isLatest(row)) {
                label.setText(note.isEmpty() ? "Current version" : "Current version \u2014 " + note);
                if (!isSelected) {
                    label.setForeground(COLOR_STEEL_BLUE);
                }
            } else {
                label.setText(note);
                if (!isSelected) {
                    label.setForeground(table.getForeground());
                }
            }
            return label;
        }
    }

    private static void styleFreezeButton(JButton button, boolean isPinned, Font boldFont) {
        if (isPinned) {
            button.setText("Unfreeze");
            button.setForeground(COLOR_UNFREEZE_RED);
        } else {
            button.setText("Freeze");
            button.setForeground(COLOR_FREEZE_GREEN);
        }
        button.setFont(boldFont);
    }

    private final class ActionCellRenderer extends DefaultTableCellRenderer {
        private final JPanel actionPanel;
        private final JButton restoreBtn;
        private final JButton freezeBtn;
        private final JButton exportBtn;

        ActionCellRenderer() {
            actionPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 4, 0));
            restoreBtn = new JButton("Restore");
            freezeBtn = new JButton("Freeze");
            exportBtn = new JButton("Export");
            actionPanel.add(restoreBtn);
            actionPanel.add(freezeBtn);
            actionPanel.add(exportBtn);
        }

        @Override
        public Component getTableCellRendererComponent(JTable table, Object value,
                                                       boolean isSelected, boolean hasFocus,
                                                       int row, int column) {
            boolean isLatest = tableModel.isLatest(row);
            VersionEntry entry = tableModel.getEntryAt(row);
            boolean isPinned = tableModel.pinnedVersions.contains(entry.getVersion());
            boolean fileMissing = tableModel.isFileMissing(row);

            restoreBtn.setEnabled(!isLatest && !fileMissing);
            freezeBtn.setEnabled(!isLatest);
            exportBtn.setEnabled(!fileMissing);
            styleFreezeButton(freezeBtn, isPinned, boldFont);

            actionPanel.setBackground(isSelected ? table.getSelectionBackground() : table.getBackground());
            return actionPanel;
        }
    }

    private final class ActionCellEditor extends AbstractCellEditor implements javax.swing.table.TableCellEditor {

        private final JPanel panel;
        private final JButton restoreButton;
        private final JButton freezeButton;
        private final JButton exportButton;
        private int editingRow;

        ActionCellEditor() {
            panel = new JPanel(new FlowLayout(FlowLayout.CENTER, 4, 0));
            restoreButton = new JButton("Restore");
            freezeButton = new JButton("Freeze");
            exportButton = new JButton("Export");

            panel.add(restoreButton);
            panel.add(freezeButton);
            panel.add(exportButton);

            restoreButton.addActionListener(e -> {
                fireEditingStopped();
                performRestore(editingRow);
            });
            freezeButton.addActionListener(e -> {
                fireEditingStopped();
                performTogglePin(tableModel.getEntryAt(editingRow).getVersion());
            });
            exportButton.addActionListener(e -> {
                fireEditingStopped();
                performExport(editingRow);
            });
        }

        @Override
        public Component getTableCellEditorComponent(JTable table, Object value,
                                                     boolean isSelected, int row, int column) {
            editingRow = row;
            boolean isLatest = tableModel.isLatest(row);
            VersionEntry entry = tableModel.getEntryAt(row);
            boolean isPinned = tableModel.pinnedVersions.contains(entry.getVersion());
            boolean fileMissing = tableModel.isFileMissing(row);

            restoreButton.setEnabled(!isLatest && !fileMissing);
            freezeButton.setEnabled(!isLatest);
            exportButton.setEnabled(!fileMissing);
            styleFreezeButton(freezeButton, isPinned, boldFont);

            return panel;
        }

        @Override
        public Object getCellEditorValue() {
            return null;
        }
    }
}
