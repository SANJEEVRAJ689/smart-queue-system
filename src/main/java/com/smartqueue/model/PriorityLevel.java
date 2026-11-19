package com.smartqueue.model;

/**
 * Priority levels for token booking.
 * Senior citizens and emergency cases receive a higher weight in queue sorting,
 * moving them ahead of regular tokens.
 */
public enum PriorityLevel {
    REGULAR(0, "Regular"),
    SENIOR_CITIZEN(1, "Senior Citizen (60+)"),
    EMERGENCY(2, "Emergency / Medical");

    private final int weight;
    private final String displayName;

    PriorityLevel(int weight, String displayName) {
        this.weight = weight;
        this.displayName = displayName;
    }

    public int getWeight() {
        return weight;
    }

    public String getDisplayName() {
        return displayName;
    }

    @Override
    public String toString() {
        return displayName;
    }
}
