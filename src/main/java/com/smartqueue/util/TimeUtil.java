package com.smartqueue.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/**
 * Utility for formatting dates, times, and human-readable duration strings (MM:SS).
 * Supports both modern Instant (UTC) and legacy LocalDateTime.
 */
public class TimeUtil {
    private static final Logger logger = LoggerFactory.getLogger(TimeUtil.class);

    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("hh:mm a");
    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("dd MMM yyyy, hh:mm a");
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("dd-MMM-yyyy");

    public static String formatTime(LocalDateTime ldt) {
        if (ldt == null) return "--:--";
        return ldt.format(TIME_FORMATTER);
    }

    public static String formatTime(Instant instant) {
        if (instant == null) return "--:--";
        return LocalDateTime.ofInstant(instant, ZoneId.systemDefault()).format(TIME_FORMATTER);
    }

    public static String formatDateTime(LocalDateTime ldt) {
        if (ldt == null) return "--";
        return ldt.format(DATE_TIME_FORMATTER);
    }

    public static String formatDateTime(Instant instant) {
        if (instant == null) return "--";
        return LocalDateTime.ofInstant(instant, ZoneId.systemDefault()).format(DATE_TIME_FORMATTER);
    }

    public static String formatDate(LocalDate ld) {
        if (ld == null) return "--";
        return ld.format(DATE_FORMATTER);
    }

    public static String formatDuration(int totalSeconds) {
        if (totalSeconds <= 0) return "0s";
        int mins = totalSeconds / 60;
        int secs = totalSeconds % 60;
        if (mins > 0) {
            return mins + "m " + secs + "s";
        } else {
            return secs + "s";
        }
    }

    /**
     * Formats elapsed duration from an Instant start time to now as "MM:SS" (or "HH:MM:SS" if >= 1 hour).
     * Logs a warning if elapsed time is negative instead of silently clamping.
     */
    public static String formatElapsed(Instant start) {
        if (start == null) return "00:00";
        long totalSecs = Duration.between(start, Instant.now()).getSeconds();
        if (totalSecs < 0) {
            logger.warn("Negative elapsed time detected ({}s) between start {} and now {}. Clamping with warning.",
                    totalSecs, start, Instant.now());
            totalSecs = 0;
        }
        return formatSecondsToStopwatch(totalSecs);
    }

    /**
     * Formats elapsed duration from a LocalDateTime start time to now as "MM:SS" (or "HH:MM:SS" if >= 1 hour).
     * Logs a warning if elapsed time is negative instead of silently clamping.
     */
    public static String formatElapsed(LocalDateTime start) {
        if (start == null) return "00:00";
        long totalSecs = Duration.between(start, LocalDateTime.now()).getSeconds();
        if (totalSecs < 0) {
            logger.warn("Negative elapsed time detected ({}s) between start {} and now {}. Clamping with warning.",
                    totalSecs, start, LocalDateTime.now());
            totalSecs = 0;
        }
        return formatSecondsToStopwatch(totalSecs);
    }

    public static String formatSecondsToStopwatch(long totalSecs) {
        if (totalSecs < 0) totalSecs = 0;
        long hours = totalSecs / 3600;
        long mins = (totalSecs % 3600) / 60;
        long secs = totalSecs % 60;
        if (hours > 0) {
            return String.format("%02d:%02d:%02d", hours, mins, secs);
        } else {
            return String.format("%02d:%02d", mins, secs);
        }
    }
}
