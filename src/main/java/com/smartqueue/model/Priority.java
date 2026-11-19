package com.smartqueue.model;

/**
 * Priority levels for token booking and queue ordering.
 * Ranks:
 * EMERGENCY = 0 (Highest priority, served first)
 * SENIOR    = 1 (Senior citizen / disability priority)
 * NORMAL    = 2 (Standard FIFO)
 *
 * Anti-starvation: NORMAL tokens waiting longer than the configured threshold
 * (e.g., 30 minutes) are elevated to effective rank 1.
 */
public enum Priority {
    EMERGENCY(0, "Emergency / Medical"),
    SENIOR(1, "Senior Citizen (60+)"),
    NORMAL(2, "Normal");

    private final int rank;
    private final String displayName;

    Priority(int rank, String displayName) {
        this.rank = rank;
        this.displayName = displayName;
    }

    public int getRank() {
        return rank;
    }

    public String getDisplayName() {
        return displayName;
    }

    public static Priority fromString(String str) {
        if (str == null) return NORMAL;
        try {
            return Priority.valueOf(str.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return NORMAL;
        }
    }

    @Override
    public String toString() {
        return displayName;
    }
}
