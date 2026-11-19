package com.smartqueue.model;

/**
 * Defines system roles for role-based access control.
 * - ADMIN: Manages services, counters, staff, and views analytical reports.
 * - STAFF: Operates service counters, calls tokens, and serves customers.
 */
public enum Role {
    ADMIN,
    STAFF;

    public static Role fromString(String roleStr) {
        if (roleStr == null) return STAFF;
        try {
            return Role.valueOf(roleStr.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return STAFF;
        }
    }
}
