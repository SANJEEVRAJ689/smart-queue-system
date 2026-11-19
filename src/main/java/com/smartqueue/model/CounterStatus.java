package com.smartqueue.model;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Operational status of a physical service counter.
 *
 * Rules:
 * - OPEN   : Staff is at the desk and ready to call/serve customers (idle).
 * - BUSY   : Staff is actively calling or serving a token (desk occupied).
 * - CLOSED : Desk is closed by staff or offline.
 */
public enum CounterStatus {
    OPEN,
    CLOSED,
    BUSY;

    private static final Logger logger = LoggerFactory.getLogger(CounterStatus.class);

    /**
     * Parses a string representation into CounterStatus.
     * Logs a warning and throws IllegalArgumentException on invalid input
     * to avoid silent state corruption.
     *
     * @param str the status name
     * @return the resolved CounterStatus
     * @throws IllegalArgumentException if str is invalid
     */
    public static CounterStatus fromString(String str) {
        if (str == null || str.trim().isEmpty()) {
            logger.warn("Null or empty counter status provided, defaulting to CLOSED");
            return CLOSED;
        }
        try {
            return CounterStatus.valueOf(str.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            logger.warn("Invalid counter status '{}' encountered! Valid values are OPEN, CLOSED, BUSY.", str);
            throw new IllegalArgumentException("Unknown CounterStatus: '" + str + "'. Allowed values: OPEN, CLOSED, BUSY.", e);
        }
    }
}
