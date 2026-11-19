package com.smartqueue.util;

import java.util.regex.Pattern;

/**
 * Utility for performing robust data and user input validation.
 */
public class Validator {

    // Phone regex: accepts 10-digit standard or international format with optional country code
    private static final Pattern PHONE_PATTERN = Pattern.compile("^(\\+?\\d{1,4}[-\\s]?)?\\d{7,14}$");

    /**
     * Validates customer or staff full name.
     */
    public static boolean isValidName(String name) {
        if (name == null) return false;
        String trimmed = name.trim();
        return trimmed.length() >= 2 && trimmed.length() <= 100;
    }

    /**
     * Validates phone number.
     */
    public static boolean isValidPhone(String phone) {
        if (phone == null) return false;
        String trimmed = phone.trim().replaceAll("\\s+", "");
        return PHONE_PATTERN.matcher(trimmed).matches();
    }

    /**
     * Validates username.
     */
    public static boolean isValidUsername(String username) {
        if (username == null) return false;
        String trimmed = username.trim();
        return trimmed.length() >= 3 && trimmed.length() <= 50;
    }

    /**
     * Validates password strength (minimum 6 characters).
     */
    public static boolean isValidPassword(String password) {
        return password != null && password.length() >= 6;
    }

    /**
     * Validates service code prefix (1 to 3 uppercase alphabetic characters).
     */
    public static boolean isValidPrefix(String prefix) {
        if (prefix == null) return false;
        String trimmed = prefix.trim();
        return trimmed.matches("^[A-Za-z]{1,3}$");
    }
}
