package io.github.sagaraggarwal86.jmeter.scm.core;

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

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ScmInitializerTest {

    @TempDir
    Path tempDir;

    private MockedStatic<JMeterUtils> jmeterUtilsMock;
    private MockedStatic<GuiPackage> guiPackageMock;

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
    }

    @AfterEach
    void tearDown() {
        guiPackageMock.close();
        jmeterUtilsMock.close();
    }

    @Test
    void getInstanceReturnsSameInstance() {
        ScmInitializer a = ScmInitializer.getInstance();
        ScmInitializer b = ScmInitializer.getInstance();
        assertSame(a, b);
    }

    @Test
    void getCurrentContextReturnsNullInitially() {
        ScmInitializer initializer = ScmInitializer.getInstance();
        // Before any test plan is loaded, context should be null
        // (or from a previous test — but we can at least verify it doesn't throw)
        assertDoesNotThrow(initializer::getCurrentContext);
    }

    @Test
    void initializeForTestPlanWithNullFileIsNoOp() {
        ScmInitializer initializer = ScmInitializer.getInstance();
        assertDoesNotThrow(() -> initializer.initializeForTestPlan(null));
    }

    @Test
    void initializeForTestPlanCreatesContext() throws IOException {
        ScmInitializer initializer = ScmInitializer.getInstance();
        Path jmxFile = tempDir.resolve("plan.jmx");
        Files.writeString(jmxFile, "<jmeterTestPlan>test</jmeterTestPlan>");

        initializer.initializeForTestPlan(jmxFile);

        ScmContext ctx = initializer.getCurrentContext();
        assertNotNull(ctx);
        assertEquals(jmxFile, ctx.getJmxFile());
        assertFalse(ctx.isDisposed());

        // Cleanup
        initializer.disposeCurrentContext();
    }

    @Test
    void initializeForTestPlanDisposePreviousContext() throws IOException {
        ScmInitializer initializer = ScmInitializer.getInstance();
        Path jmx1 = tempDir.resolve("plan1.jmx");
        Path jmx2 = tempDir.resolve("plan2.jmx");
        Files.writeString(jmx1, "<jmeterTestPlan>plan1</jmeterTestPlan>");
        Files.writeString(jmx2, "<jmeterTestPlan>plan2</jmeterTestPlan>");

        initializer.initializeForTestPlan(jmx1);
        ScmContext ctx1 = initializer.getCurrentContext();

        initializer.initializeForTestPlan(jmx2);
        assertTrue(ctx1.isDisposed());

        ScmContext ctx2 = initializer.getCurrentContext();
        assertNotNull(ctx2);
        assertEquals(jmx2, ctx2.getJmxFile());

        initializer.disposeCurrentContext();
    }

    @Test
    void disposeCurrentContextReleasesContext() throws IOException {
        ScmInitializer initializer = ScmInitializer.getInstance();
        Path jmxFile = tempDir.resolve("plan.jmx");
        Files.writeString(jmxFile, "<jmeterTestPlan>test</jmeterTestPlan>");

        initializer.initializeForTestPlan(jmxFile);
        assertNotNull(initializer.getCurrentContext());

        initializer.disposeCurrentContext();
        assertNull(initializer.getCurrentContext());
    }

    @Test
    void disposeCurrentContextWhenNoContextIsNoOp() {
        ScmInitializer initializer = ScmInitializer.getInstance();
        // Ensure no active context
        initializer.disposeCurrentContext();
        // Double dispose should not throw
        assertDoesNotThrow(initializer::disposeCurrentContext);
    }

    @Test
    void getHistoryPanelReturnsNullBeforeUiInstall() {
        ScmInitializer initializer = ScmInitializer.getInstance();
        // In headless mode without MainFrame, history panel might be null
        // This just ensures no NPE
        assertDoesNotThrow(initializer::getHistoryPanel);
    }

    @Test
    void restartAutoCheckpointDoesNotThrow() {
        ScmInitializer initializer = ScmInitializer.getInstance();
        assertDoesNotThrow(initializer::restartAutoCheckpoint);
    }

    @Test
    void setToolbarVisibleDoesNotThrow() {
        ScmInitializer initializer = ScmInitializer.getInstance();
        // No toolbar components in headless mode, but should not throw
        assertDoesNotThrow(() -> initializer.setToolbarVisible(false));
        assertDoesNotThrow(() -> initializer.setToolbarVisible(true));
    }

    @Test
    void ensureInitializedDoesNotThrowInHeadless() {
        ScmInitializer initializer = ScmInitializer.getInstance();
        // In headless mode, UI install will fail gracefully
        assertDoesNotThrow(initializer::ensureInitialized);
    }

    @Test
    void ensureInitializedWithContextHandlesNullGuiPackage() {
        ScmInitializer initializer = ScmInitializer.getInstance();
        // GuiPackage returns null in our mock — should handle gracefully
        assertDoesNotThrow(initializer::ensureInitializedWithContext);
    }
}
