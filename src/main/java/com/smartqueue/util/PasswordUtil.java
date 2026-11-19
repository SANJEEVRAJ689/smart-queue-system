package com.smartqueue.util;

import org.mindrot.jbcrypt.BCrypt;

/**
 * Utility for hashing and verifying passwords using BCrypt.
 * Uses a work factor (log rounds) of 10 for a good balance of security and speed.
 */
public class PasswordUtil {

    /**
     * Hashes a plain-text password using BCrypt with an auto-generated salt.
     *
     * @param plainPassword the raw password to hash
     * @return 60-character BCrypt hash string
     */
    public static String hashPassword(String plainPassword) {
        if (plainPassword == null || plainPassword.isEmpty()) {
            throw new IllegalArgumentException("Password cannot be null or empty.");
        }
        return BCrypt.hashpw(plainPassword, BCrypt.gensalt(10));
    }

    /**
     * Verifies a plain-text password against a stored BCrypt hash.
     *
     * @param plainPassword raw password to test
     * @param storedHash BCrypt hash from the database
     * @return true if the password matches, false otherwise
     */
    public static boolean checkPassword(String plainPassword, String storedHash) {
        if (plainPassword == null || storedHash == null || storedHash.isEmpty()) {
            return false;
        }
        try {
            return BCrypt.checkpw(plainPassword, storedHash);
        } catch (IllegalArgumentException e) {
            // Malformed hash
            return false;
        }
    }
}
