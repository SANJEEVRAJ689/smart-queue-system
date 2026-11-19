package com.smartqueue.model;

/**
 * Represents the lifecycle stages of a queue token.
 * WAITING   -> Customer has booked a token and is in line.
 * CALLED    -> Counter staff called the token; customer should approach the counter.
 * SERVING   -> Customer is currently being served at the counter.
 * COMPLETED -> Service is successfully finished.
 * SKIPPED   -> Customer did not show up when called.
 * CANCELLED -> Token was cancelled by customer or admin.
 */
public enum TokenStatus {
    WAITING,
    CALLED,
    SERVING,
    COMPLETED,
    SKIPPED,
    CANCELLED;

    public static TokenStatus fromString(String statusStr) {
        if (statusStr == null) return WAITING;
        try {
            return TokenStatus.valueOf(statusStr.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return WAITING;
        }
    }
}
