package io.github.sagaraggarwal86.jmeter.scm.ui;

import io.github.sagaraggarwal86.jmeter.scm.core.DirtyTracker;
import io.github.sagaraggarwal86.jmeter.scm.core.ScmContext;
import org.junit.jupiter.api.Test;

import java.awt.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DirtyIndicatorTest {

    private static final Color GREEN = new Color(40, 167, 69);
    private static final Color AMBER = new Color(255, 191, 0);
    private static final Color RED = new Color(220, 53, 69);

    @Test
    void constructorSetsInitialState() {
        DirtyIndicator indicator = new DirtyIndicator();

        assertEquals("I", indicator.getText());
        assertEquals(GREEN, indicator.getForeground());
        assertTrue(indicator.getToolTipText().contains("Clean"));
        assertFalse(indicator.isFocusable());
    }

    @Test
    void refreshWithNullContextSetsGreen() {
        DirtyIndicator indicator = new DirtyIndicator();
        indicator.refresh(null);

        assertEquals(GREEN, indicator.getForeground());
        assertTrue(indicator.getToolTipText().contains("No active context"));
    }

    @Test
    void refreshWithDisposedContextSetsGreen() {
        DirtyIndicator indicator = new DirtyIndicator();
        ScmContext ctx = mock(ScmContext.class);
        when(ctx.isDisposed()).thenReturn(true);

        indicator.refresh(ctx);

        assertEquals(GREEN, indicator.getForeground());
        assertTrue(indicator.getToolTipText().contains("No active context"));
    }

    @Test
    void refreshWithReadOnlyContextSetsRed() {
        DirtyIndicator indicator = new DirtyIndicator();
        ScmContext ctx = mock(ScmContext.class);
        when(ctx.isDisposed()).thenReturn(false);
        when(ctx.isReadOnly()).thenReturn(true);

        indicator.refresh(ctx);

        assertEquals(RED, indicator.getForeground());
        assertTrue(indicator.getToolTipText().contains("Read-only"));
    }

    @Test
    void refreshWithDirtyContextSetsAmber() {
        DirtyIndicator indicator = new DirtyIndicator();
        ScmContext ctx = mock(ScmContext.class);
        DirtyTracker tracker = mock(DirtyTracker.class);
        when(ctx.isDisposed()).thenReturn(false);
        when(ctx.isReadOnly()).thenReturn(false);
        when(ctx.getDirtyTracker()).thenReturn(tracker);
        when(tracker.isDirty()).thenReturn(true);

        indicator.refresh(ctx);

        assertEquals(AMBER, indicator.getForeground());
        assertTrue(indicator.getToolTipText().contains("Modified"));
    }

    @Test
    void refreshWithCleanContextSetsGreen() {
        DirtyIndicator indicator = new DirtyIndicator();
        ScmContext ctx = mock(ScmContext.class);
        DirtyTracker tracker = mock(DirtyTracker.class);
        when(ctx.isDisposed()).thenReturn(false);
        when(ctx.isReadOnly()).thenReturn(false);
        when(ctx.getDirtyTracker()).thenReturn(tracker);
        when(tracker.isDirty()).thenReturn(false);

        indicator.refresh(ctx);

        assertEquals(GREEN, indicator.getForeground());
        assertTrue(indicator.getToolTipText().contains("Clean"));
    }

    @Test
    void fontIsBold13() {
        DirtyIndicator indicator = new DirtyIndicator();
        assertTrue(indicator.getFont().isBold());
        assertEquals(13f, indicator.getFont().getSize2D(), 0.1f);
    }
}
