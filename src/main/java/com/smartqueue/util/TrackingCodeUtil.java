package com.smartqueue.util;

import java.security.SecureRandom;

/**
 * Utility for generating cryptographically secure, unguessable tracking codes
 * for public token tracking without exposing phone numbers.
 */
public class TrackingCodeUtil {
    private static final String ALPHABET = "23456789ABCDEFGHJKLMNPQRSTUVWXYZ"; // Base32 without ambiguous 0/O, 1/I
    private static final SecureRandom RANDOM = new SecureRandom();

    /**
     * Generates a secure random tracking code e.g. "TRK-7B9F-4D2A".
     */
    public static String generate() {
        StringBuilder sb = new StringBuilder("TRK-");
        for (int i = 0; i < 4; i++) {
            sb.append(ALPHABET.charAt(RANDOM.nextInt(ALPHABET.length())));
        }
        sb.append('-');
        for (int i = 0; i < 4; i++) {
            sb.append(ALPHABET.charAt(RANDOM.nextInt(ALPHABET.length())));
        }
        return sb.toString();
    }
}
