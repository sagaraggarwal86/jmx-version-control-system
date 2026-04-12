package io.github.sagaraggarwal86.jmeter.scm.ui;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Shared relative-time formatting for UI components.
 */
final class TimeFormatUtils {

    static final DateTimeFormatter TIMESTAMP_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private TimeFormatUtils() {
    }

    /**
     * Formats a timestamp as relative time: "3m ago", "2h ago", etc.
     * Falls back to full timestamp after 30 days.
     */
    static String formatRelative(LocalDateTime timestamp) {
        long seconds = Duration.between(timestamp, LocalDateTime.now()).getSeconds();
        if (seconds < 0) return "just now";
        if (seconds < 60) return seconds + "s ago";
        long minutes = seconds / 60;
        if (minutes < 60) return minutes + "m ago";
        long hours = minutes / 60;
        if (hours < 24) return hours + "h ago";
        long days = hours / 24;
        if (days < 30) return days + "d ago";
        return timestamp.format(TIMESTAMP_FORMAT);
    }

    /**
     * Formats a timestamp as short relative time: "3m", "2h", etc.
     * No suffix, for compact display.
     */
    static String formatShort(LocalDateTime timestamp) {
        long seconds = Duration.between(timestamp, LocalDateTime.now()).getSeconds();
        if (seconds < 0) return "now";
        if (seconds < 60) return seconds + "s";
        long minutes = seconds / 60;
        if (minutes < 60) return minutes + "m";
        long hours = minutes / 60;
        if (hours < 24) return hours + "h";
        return (hours / 24) + "d";
    }
}
