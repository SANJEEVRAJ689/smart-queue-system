package com.smartqueue.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/**
 * Utility for formatting dates, times, and durations.
 * Supports both modern Instant (UTC) and legacy LocalDateTime.
 */
public class DateTimeUtil {
    private static final Logger logger = LoggerFactory.getLogger(DateTimeUtil.class);

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

    public static String formatDate(LocalDateTime ldt) {
        if (ldt == null) return "--";
        return ldt.format(DATE_FORMATTER);
    }

    public static String formatDate(Instant instant) {
        if (instant == null) return "--";
        return LocalDateTime.ofInstant(instant, ZoneId.systemDefault()).format(DATE_FORMATTER);
    }

    /**
     * Formats seconds into "Xm Ys" or "Xs".
     */
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
     * Formats elapsed duration from an Instant start time to now.
     * Logs a warning if elapsed time is negative instead of silently clamping.
     */
    public static String formatElapsed(Instant start) {
        if (start == null) return "0s";
        long seconds = Duration.between(start, Instant.now()).getSeconds();
        if (seconds < 0) {
            logger.warn("Negative elapsed time detected ({}s) between start {} and now {}. System clock or timezone skew detected.",
                    seconds, start, Instant.now());
            seconds = 0;
        }
        return formatDuration((int) seconds);
    }

    /**
     * Formats elapsed duration from a LocalDateTime start time to now.
     * Logs a warning if elapsed time is negative instead of silently clamping.
     */
    public static String formatElapsed(LocalDateTime start) {
        if (start == null) return "0s";
        long seconds = Duration.between(start, LocalDateTime.now()).getSeconds();
        if (seconds < 0) {
            logger.warn("Negative elapsed time detected ({}s) between start {} and now {}. System clock or timezone skew detected.",
                    seconds, start, LocalDateTime.now());
            seconds = 0;
        }
        return formatDuration((int) seconds);
    }
}
