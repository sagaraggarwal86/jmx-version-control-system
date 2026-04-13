package io.github.sagaraggarwal86.jmeter.scm.ui;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

class TimeFormatUtilsTest {

    @Test
    void formatRelativeSeconds() {
        LocalDateTime ts = LocalDateTime.now().minusSeconds(30);
        assertEquals("30s ago", TimeFormatUtils.formatRelative(ts));
    }

    @Test
    void formatRelativeMinutes() {
        LocalDateTime ts = LocalDateTime.now().minusMinutes(5);
        assertEquals("5m ago", TimeFormatUtils.formatRelative(ts));
    }

    @Test
    void formatRelativeHours() {
        LocalDateTime ts = LocalDateTime.now().minusHours(3);
        assertEquals("3h ago", TimeFormatUtils.formatRelative(ts));
    }

    @Test
    void formatRelativeDays() {
        LocalDateTime ts = LocalDateTime.now().minusDays(7);
        assertEquals("7d ago", TimeFormatUtils.formatRelative(ts));
    }

    @Test
    void formatRelativeFallsBackToTimestampAfter30Days() {
        LocalDateTime ts = LocalDateTime.now().minusDays(31);
        String result = TimeFormatUtils.formatRelative(ts);
        // Should be full timestamp, not "Xd ago"
        assertFalse(result.endsWith("ago"));
        assertTrue(result.matches("\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2}"));
    }

    @Test
    void formatRelativeFutureTimestamp() {
        LocalDateTime ts = LocalDateTime.now().plusMinutes(5);
        assertEquals("just now", TimeFormatUtils.formatRelative(ts));
    }

    @Test
    void formatRelativeZeroSeconds() {
        LocalDateTime ts = LocalDateTime.now();
        String result = TimeFormatUtils.formatRelative(ts);
        assertTrue(result.equals("0s ago") || result.equals("just now"));
    }

    @Test
    void formatShortSeconds() {
        LocalDateTime ts = LocalDateTime.now().minusSeconds(45);
        assertEquals("45s", TimeFormatUtils.formatShort(ts));
    }

    @Test
    void formatShortMinutes() {
        LocalDateTime ts = LocalDateTime.now().minusMinutes(12);
        assertEquals("12m", TimeFormatUtils.formatShort(ts));
    }

    @Test
    void formatShortHours() {
        LocalDateTime ts = LocalDateTime.now().minusHours(6);
        assertEquals("6h", TimeFormatUtils.formatShort(ts));
    }

    @Test
    void formatShortDays() {
        LocalDateTime ts = LocalDateTime.now().minusDays(3);
        assertEquals("3d", TimeFormatUtils.formatShort(ts));
    }

    @Test
    void formatShortFutureTimestamp() {
        LocalDateTime ts = LocalDateTime.now().plusMinutes(5);
        assertEquals("now", TimeFormatUtils.formatShort(ts));
    }

    @Test
    void formatRelativeBoundaryMinutes() {
        // 59 seconds → seconds format
        LocalDateTime ts59s = LocalDateTime.now().minusSeconds(59);
        assertTrue(TimeFormatUtils.formatRelative(ts59s).endsWith("s ago"));

        // 60 seconds → minutes format
        LocalDateTime ts60s = LocalDateTime.now().minusSeconds(60);
        assertEquals("1m ago", TimeFormatUtils.formatRelative(ts60s));
    }

    @Test
    void formatRelativeBoundaryHours() {
        // 59 minutes → minutes format
        LocalDateTime ts59m = LocalDateTime.now().minusMinutes(59);
        assertTrue(TimeFormatUtils.formatRelative(ts59m).endsWith("m ago"));

        // 60 minutes → hours format
        LocalDateTime ts60m = LocalDateTime.now().minusMinutes(60);
        assertEquals("1h ago", TimeFormatUtils.formatRelative(ts60m));
    }

    @Test
    void formatRelativeBoundaryDays() {
        // 23 hours → hours format
        LocalDateTime ts23h = LocalDateTime.now().minusHours(23);
        assertTrue(TimeFormatUtils.formatRelative(ts23h).endsWith("h ago"));

        // 24 hours → days format
        LocalDateTime ts24h = LocalDateTime.now().minusHours(24);
        assertEquals("1d ago", TimeFormatUtils.formatRelative(ts24h));
    }

    @Test
    void timestampFormatPatternValid() {
        LocalDateTime ts = LocalDateTime.of(2026, 1, 15, 14, 30, 45);
        assertEquals("2026-01-15 14:30:45", ts.format(TimeFormatUtils.TIMESTAMP_FORMAT));
    }
}
