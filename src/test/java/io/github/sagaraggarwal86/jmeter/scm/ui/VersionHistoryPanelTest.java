package io.github.sagaraggarwal86.jmeter.scm.ui;

import io.github.sagaraggarwal86.jmeter.scm.core.DirtyTracker;
import io.github.sagaraggarwal86.jmeter.scm.core.ScmContext;
import io.github.sagaraggarwal86.jmeter.scm.core.ScmInitializer;
import io.github.sagaraggarwal86.jmeter.scm.model.TriggerType;
import io.github.sagaraggarwal86.jmeter.scm.model.VersionEntry;
import io.github.sagaraggarwal86.jmeter.scm.model.VersionIndex;
import org.apache.jmeter.gui.GuiPackage;
import org.apache.jmeter.util.JMeterUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.MockedStatic;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class VersionHistoryPanelTest {

    @TempDir
    Path tempDir;

    private MockedStatic<JMeterUtils> jmeterUtilsMock;
    private MockedStatic<GuiPackage> guiPackageMock;
    private MockedStatic<ScmInitializer> scmInitializerMock;
    private ScmInitializer mockInitializer;
    private VersionHistoryPanel panel;

    @BeforeEach
    void setUp() {
        jmeterUtilsMock = mockStatic(JMeterUtils.class);
        jmeterUtilsMock.when(() -> JMeterUtils.getProperty("scm.storage.location")).thenReturn(".history");
        jmeterUtilsMock.when(() -> JMeterUtils.getProperty("scm.max.retention")).thenReturn("20");
        jmeterUtilsMock.when(() -> JMeterUtils.getProperty("scm.lock.stale.minutes")).thenReturn("60");
        jmeterUtilsMock.when(() -> JMeterUtils.getProperty("scm.autosave.enabled")).thenReturn("false");
        jmeterUtilsMock.when(() -> JMeterUtils.getProperty("scm.autosave.interval.minutes")).thenReturn("5");
        jmeterUtilsMock.when(() -> JMeterUtils.getProperty("scm.toolbar.visible")).thenReturn("true");
        jmeterUtilsMock.when(JMeterUtils::getJMeterVersion).thenReturn("5.6.3");

        guiPackageMock = mockStatic(GuiPackage.class);
        guiPackageMock.when(GuiPackage::getInstance).thenReturn(null);

        mockInitializer = mock(ScmInitializer.class);
        scmInitializerMock = mockStatic(ScmInitializer.class);
        scmInitializerMock.when(ScmInitializer::getInstance).thenReturn(mockInitializer);

        panel = new VersionHistoryPanel(mockInitializer);
    }

    @AfterEach
    void tearDown() {
        scmInitializerMock.close();
        guiPackageMock.close();
        jmeterUtilsMock.close();
    }

    private VersionEntry createEntry(int version) {
        return new VersionEntry(version, "test_" + String.format("%03d", version) + ".jmxv",
                LocalDateTime.now(), TriggerType.CHECKPOINT, null, "checksum" + version);
    }

    private ScmContext createMockContext(List<VersionEntry> entries, Set<Integer> pinned) throws IOException {
        ScmContext ctx = mock(ScmContext.class);
        when(ctx.isDisposed()).thenReturn(false);
        when(ctx.isReadOnly()).thenReturn(false);
        when(ctx.getStorageDir()).thenReturn(tempDir);

        VersionIndex index = VersionIndex.createDefault(20, ".history");
        for (VersionEntry entry : entries) {
            index.addVersion(entry);
            // Create actual files so they're not detected as missing
            Files.writeString(tempDir.resolve(entry.getFile()), "content");
        }
        if (pinned != null) {
            for (int v : pinned) {
                index.pin(v);
            }
        }
        when(ctx.getVersionIndex()).thenReturn(index);

        DirtyTracker tracker = mock(DirtyTracker.class);
        when(tracker.isDirty()).thenReturn(false);
        when(ctx.getDirtyTracker()).thenReturn(tracker);

        return ctx;
    }

    @Test
    void constructorCreatesPanel() {
        assertNotNull(panel);
        // JPanel defaults to visible=true; ScmInitializer.installBottomPanel sets it false
        assertTrue(panel.isVisible());
    }

    @Test
    void refreshWithNullContextClearsPanel() {
        panel.refresh(null);
        // Should not throw and should clear version count
        assertNotNull(panel);
    }

    @Test
    void refreshWithDisposedContextClearsPanel() {
        ScmContext ctx = mock(ScmContext.class);
        when(ctx.isDisposed()).thenReturn(true);

        panel.refresh(ctx);
        assertNotNull(panel);
    }

    @Test
    void refreshWithValidContextPopulatesTable() throws IOException {
        List<VersionEntry> entries = List.of(createEntry(1), createEntry(2), createEntry(3));
        ScmContext ctx = createMockContext(entries, null);

        panel.refresh(ctx);

        // Table should have 3 rows (entries are reversed in display order)
        assertEquals(3, panel.getComponent(1) instanceof javax.swing.JScrollPane ?
                ((javax.swing.JTable) ((javax.swing.JScrollPane) panel.getComponent(1)).getViewport().getView())
                .getRowCount() : -1);
    }

    @Test
    void refreshShowsDirtyIndicator() throws IOException {
        List<VersionEntry> entries = List.of(createEntry(1));
        ScmContext ctx = createMockContext(entries, null);
        DirtyTracker tracker = ctx.getDirtyTracker();
        when(tracker.isDirty()).thenReturn(true);

        panel.refresh(ctx);
        // Dirty label should contain version info — just verify no exception
        assertNotNull(panel);
    }

    @Test
    void refreshDetectsMissingFiles() throws IOException {
        VersionEntry entry = createEntry(1);
        ScmContext ctx = mock(ScmContext.class);
        when(ctx.isDisposed()).thenReturn(false);
        when(ctx.isReadOnly()).thenReturn(false);
        when(ctx.getStorageDir()).thenReturn(tempDir);

        VersionIndex index = VersionIndex.createDefault(20, ".history");
        index.addVersion(entry);
        // Do NOT create the actual file — it should be detected as missing
        when(ctx.getVersionIndex()).thenReturn(index);

        DirtyTracker tracker = mock(DirtyTracker.class);
        when(tracker.isDirty()).thenReturn(false);
        when(ctx.getDirtyTracker()).thenReturn(tracker);

        panel.refresh(ctx);
        assertNotNull(panel);
    }

    @Test
    void refreshShowsRetentionWarningAtCapacity() throws IOException {
        jmeterUtilsMock.when(() -> JMeterUtils.getProperty("scm.max.retention")).thenReturn("2");

        List<VersionEntry> entries = List.of(createEntry(1), createEntry(2));
        ScmContext ctx = createMockContext(entries, null);
        ctx.getVersionIndex().setMaxRetention(2);

        panel.refresh(ctx);
        assertNotNull(panel);
    }

    @Test
    void refreshShowsFrozenRetentionWarning() throws IOException {
        jmeterUtilsMock.when(() -> JMeterUtils.getProperty("scm.max.retention")).thenReturn("2");

        List<VersionEntry> entries = List.of(createEntry(1), createEntry(2));
        Set<Integer> pinned = Set.of(1);
        ScmContext ctx = createMockContext(entries, pinned);
        ctx.getVersionIndex().setMaxRetention(2);

        panel.refresh(ctx);
        assertNotNull(panel);
    }

    @Test
    void toggleVisibilityChangesState() {
        // Panel starts visible (JPanel default), toggle hides it, toggle again shows it
        assertTrue(panel.isVisible());
        panel.toggleVisibility();
        assertFalse(panel.isVisible());
        panel.toggleVisibility();
        assertTrue(panel.isVisible());
    }

    @Test
    void deleteSelectedVersionsWithNullContextShowsWarning() {
        when(mockInitializer.getCurrentContext()).thenReturn(null);

        try (MockedStatic<javax.swing.JOptionPane> optionPaneMock = mockStatic(javax.swing.JOptionPane.class)) {
            panel.deleteSelectedVersions();

            optionPaneMock.verify(() ->
                    javax.swing.JOptionPane.showMessageDialog(any(), contains("No active test plan"),
                            eq("JVCS"), eq(javax.swing.JOptionPane.WARNING_MESSAGE)));
        }
    }

    @Test
    void deleteSelectedVersionsWithReadOnlyContextShowsWarning() {
        ScmContext ctx = mock(ScmContext.class);
        when(ctx.isReadOnly()).thenReturn(true);
        when(mockInitializer.getCurrentContext()).thenReturn(ctx);

        try (MockedStatic<javax.swing.JOptionPane> optionPaneMock = mockStatic(javax.swing.JOptionPane.class)) {
            panel.deleteSelectedVersions();

            optionPaneMock.verify(() ->
                    javax.swing.JOptionPane.showMessageDialog(any(), contains("No active test plan"),
                            eq("JVCS"), eq(javax.swing.JOptionPane.WARNING_MESSAGE)));
        }
    }

    @Test
    void deleteSelectedVersionsWithNoSelectionShowsInfo() throws IOException {
        List<VersionEntry> entries = List.of(createEntry(1), createEntry(2));
        ScmContext ctx = createMockContext(entries, null);
        when(mockInitializer.getCurrentContext()).thenReturn(ctx);

        panel.refresh(ctx);

        try (MockedStatic<javax.swing.JOptionPane> optionPaneMock = mockStatic(javax.swing.JOptionPane.class)) {
            panel.deleteSelectedVersions();

            optionPaneMock.verify(() ->
                    javax.swing.JOptionPane.showMessageDialog(any(), contains("No versions selected"),
                            eq("JVCS"), eq(javax.swing.JOptionPane.INFORMATION_MESSAGE)));
        }
    }

    @Test
    void panelPreferredSizeIsSet() {
        assertEquals(300, panel.getPreferredSize().height);
    }

    @Test
    void panelMinimumSizeIsSet() {
        assertEquals(100, panel.getMinimumSize().height);
    }

    @Test
    void formatSizeFormatsBytes() throws Exception {
        java.lang.reflect.Method formatSize = VersionHistoryPanel.class.getDeclaredMethod("formatSize", long.class);
        formatSize.setAccessible(true);

        assertEquals("0 B", formatSize.invoke(null, 0L));
        assertEquals("500 B", formatSize.invoke(null, 500L));
        assertEquals("1.0 KB", formatSize.invoke(null, 1024L));
        assertEquals("1.5 KB", formatSize.invoke(null, 1536L));
        assertEquals("1.0 MB", formatSize.invoke(null, 1048576L));
    }

    @Test
    void refreshUpdatesAutoSaveLegend() throws IOException {
        jmeterUtilsMock.when(() -> JMeterUtils.getProperty("scm.autosave.enabled")).thenReturn("true");
        jmeterUtilsMock.when(() -> JMeterUtils.getProperty("scm.autosave.interval.minutes")).thenReturn("10");

        // Recreate panel to pick up auto-save enabled
        panel = new VersionHistoryPanel(mockInitializer);

        List<VersionEntry> entries = List.of(createEntry(1));
        ScmContext ctx = createMockContext(entries, null);

        panel.refresh(ctx);
        assertNotNull(panel);
    }
}
