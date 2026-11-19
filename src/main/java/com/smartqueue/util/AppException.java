package com.smartqueue.util;

/**
 * Custom checked exception for application domain errors.
 * Ensures consistent error handling and prevents raw internal exceptions
 * from leaking to user-facing controllers.
 */
public class AppException extends Exception {

    public AppException(String message) {
        super(message);
    }

    public AppException(String message, Throwable cause) {
        super(message, cause);
    }
}
