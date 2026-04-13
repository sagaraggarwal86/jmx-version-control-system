package io.github.sagaraggarwal86.jmeter.scm.ui;

import io.github.sagaraggarwal86.jmeter.scm.core.ScmContext;
import io.github.sagaraggarwal86.jmeter.scm.core.ScmInitializer;
import org.apache.jmeter.gui.GuiPackage;
import org.apache.jmeter.util.JMeterUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import javax.swing.*;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ScmMenuHandlerTest {

    private MockedStatic<GuiPackage> guiPackageMock;
    private MockedStatic<JMeterUtils> jmeterUtilsMock;
    private ScmInitializer mockInitializer;

    @BeforeEach
    void setUp() {
        guiPackageMock = mockStatic(GuiPackage.class);
        guiPackageMock.when(GuiPackage::getInstance).thenReturn(null);

        jmeterUtilsMock = mockStatic(JMeterUtils.class);
        jmeterUtilsMock.when(() -> JMeterUtils.getProperty(anyString())).thenReturn(null);
        jmeterUtilsMock.when(JMeterUtils::getJMeterVersion).thenReturn("5.6.3");

        mockInitializer = mock(ScmInitializer.class);
    }

    @AfterEach
    void tearDown() {
        jmeterUtilsMock.close();
        guiPackageMock.close();
    }

    @Test
    void createMenuItemsReturnsVersionControlSubmenu() {
        JMenuItem[] items = ScmMenuHandler.createMenuItems(mockInitializer);

        assertNotNull(items);
        assertEquals(1, items.length);
        assertTrue(items[0] instanceof JMenu);
        assertEquals("Version Control", items[0].getText());
    }

    @Test
    void versionControlMenuHasCorrectItems() {
        JMenuItem[] items = ScmMenuHandler.createMenuItems(mockInitializer);
        JMenu menu = (JMenu) items[0];

        // Expected: Show History, Save Checkpoint, Delete Versions, Lock, separator, Settings, About
        assertTrue(menu.getItemCount() >= 6);
        assertEquals("Show History", menu.getItem(0).getText());
        assertEquals("Save Checkpoint", menu.getItem(1).getText());
        assertEquals("Delete Versions", menu.getItem(2).getText());
        assertEquals("Lock", menu.getItem(3).getText());
        // index 4 is separator
        assertEquals("Settings", menu.getItem(5).getText());
        assertEquals("About", menu.getItem(6).getText());
    }

    @Test
    void showHistoryHasCtrlHAccelerator() {
        JMenuItem[] items = ScmMenuHandler.createMenuItems(mockInitializer);
        JMenu menu = (JMenu) items[0];
        JMenuItem showHistory = menu.getItem(0);

        KeyStroke expected = KeyStroke.getKeyStroke(KeyEvent.VK_H, InputEvent.CTRL_DOWN_MASK);
        assertEquals(expected, showHistory.getAccelerator());
    }

    @Test
    void saveCheckpointHasCtrlKAccelerator() {
        JMenuItem[] items = ScmMenuHandler.createMenuItems(mockInitializer);
        JMenu menu = (JMenu) items[0];
        JMenuItem saveCheckpoint = menu.getItem(1);

        KeyStroke expected = KeyStroke.getKeyStroke(KeyEvent.VK_K, InputEvent.CTRL_DOWN_MASK);
        assertEquals(expected, saveCheckpoint.getAccelerator());
    }

    @Test
    void triggerShowHistoryCallsToggleVisibility() {
        VersionHistoryPanel mockPanel = mock(VersionHistoryPanel.class);
        when(mockInitializer.getHistoryPanel()).thenReturn(mockPanel);

        ScmMenuHandler.triggerShowHistory(mockInitializer);

        verify(mockInitializer).ensureInitializedWithContext();
        verify(mockPanel).toggleVisibility();
    }

    @Test
    void triggerShowHistoryHandlesNullPanel() {
        when(mockInitializer.getHistoryPanel()).thenReturn(null);

        assertDoesNotThrow(() -> ScmMenuHandler.triggerShowHistory(mockInitializer));
    }

    @Test
    void triggerDeleteVersionsEnsuresInitialization() {
        when(mockInitializer.getHistoryPanel()).thenReturn(null);

        ScmMenuHandler.triggerDeleteVersions(mockInitializer);

        verify(mockInitializer).ensureInitializedWithContext();
    }

    @Test
    void triggerLockWithNullContextShowsWarning() {
        when(mockInitializer.getCurrentContext()).thenReturn(null);

        try (MockedStatic<JOptionPane> optionPaneMock = mockStatic(JOptionPane.class)) {
            ScmMenuHandler.triggerLock(mockInitializer);

            optionPaneMock.verify(() ->
                    JOptionPane.showMessageDialog(any(), contains("No active test plan"),
                            eq("JVCS"), eq(JOptionPane.WARNING_MESSAGE)));
        }
    }

    @Test
    void triggerLockWithDisposedContextShowsWarning() {
        ScmContext ctx = mock(ScmContext.class);
        when(ctx.isDisposed()).thenReturn(true);
        when(mockInitializer.getCurrentContext()).thenReturn(ctx);

        try (MockedStatic<JOptionPane> optionPaneMock = mockStatic(JOptionPane.class)) {
            ScmMenuHandler.triggerLock(mockInitializer);

            optionPaneMock.verify(() ->
                    JOptionPane.showMessageDialog(any(), contains("No active test plan"),
                            eq("JVCS"), eq(JOptionPane.WARNING_MESSAGE)));
        }
    }

    @Test
    void triggerLockWithOwnedLockShowsInfo() {
        ScmContext ctx = mock(ScmContext.class);
        when(ctx.isDisposed()).thenReturn(false);
        when(ctx.isReadOnly()).thenReturn(false);
        when(ctx.tryAcquireLock()).thenReturn(true);
        when(mockInitializer.getCurrentContext()).thenReturn(ctx);

        try (MockedStatic<JOptionPane> optionPaneMock = mockStatic(JOptionPane.class)) {
            ScmMenuHandler.triggerLock(mockInitializer);

            optionPaneMock.verify(() ->
                    JOptionPane.showMessageDialog(any(), contains("already hold the lock"),
                            eq("JVCS"), eq(JOptionPane.INFORMATION_MESSAGE)));
        }
    }

    @Test
    void triggerCheckpointWithNullContextDoesNothing() {
        when(mockInitializer.getCurrentContext()).thenReturn(null);

        try (MockedStatic<JOptionPane> optionPaneMock = mockStatic(JOptionPane.class)) {
            ScmMenuHandler.triggerCheckpoint(mockInitializer);

            optionPaneMock.verify(() ->
                    JOptionPane.showMessageDialog(any(), contains("No active test plan"),
                            eq("JVCS"), eq(JOptionPane.WARNING_MESSAGE)));
        }
    }

    @Test
    void triggerCheckpointAtUnprunableCapacityShowsWarning() {
        ScmContext ctx = mock(ScmContext.class);
        when(ctx.isDisposed()).thenReturn(false);
        when(ctx.isReadOnly()).thenReturn(false);
        when(ctx.isAtUnprunableCapacity()).thenReturn(true);
        when(mockInitializer.getCurrentContext()).thenReturn(ctx);

        try (MockedStatic<JOptionPane> optionPaneMock = mockStatic(JOptionPane.class)) {
            ScmMenuHandler.triggerCheckpoint(mockInitializer);

            optionPaneMock.verify(() ->
                    JOptionPane.showMessageDialog(any(), contains("Cannot create checkpoint"),
                            eq("JVCS"), eq(JOptionPane.WARNING_MESSAGE)));
        }
    }

    @Test
    void getParentWindowReturnsNullWhenGuiPackageNull() {
        assertNull(ScmMenuHandler.getParentWindow());
    }
}
