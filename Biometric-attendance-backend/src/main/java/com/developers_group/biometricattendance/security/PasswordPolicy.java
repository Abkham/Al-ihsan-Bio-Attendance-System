package com.developers_group.biometricattendance.security;

import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Set;

/**
 * Password rules for every account (employees and administrators).
 *
 *  - at least 8 characters (BCrypt only uses the first 72 BYTES, so longer is rejected)
 *  - at least one letter and one digit
 *  - must not contain the account's username
 *  - must not be one of the most common passwords
 *
 * Returns a human readable error message, or null when the password is acceptable.
 * This class has no framework dependency so it can be unit-tested on its own.
 */
public final class PasswordPolicy {

    public static final int MIN_LENGTH = 8;
    public static final int MAX_BYTES = 72;

    private static final Set<String> COMMON = Set.of(
            "password", "password1", "password123", "12345678", "123456789",
            "1234567890", "qwerty123", "qwertyuiop", "iloveyou", "admin123",
            "admin1234", "welcome1", "welcome123", "letmein123", "abc12345",
            "11111111", "00000000", "passw0rd", "p@ssw0rd", "changeme1");

    private PasswordPolicy() {
    }

    public static String describe() {
        return "Password must be at least " + MIN_LENGTH
                + " characters and contain at least one letter and one number.";
    }

    /** @return null when valid, otherwise the message to show the user. */
    public static String validate(String password, String username) {

        if (password == null || password.isEmpty()) {
            return "Password is required.";
        }

        if (password.length() < MIN_LENGTH) {
            return describe();
        }

        if (password.getBytes(StandardCharsets.UTF_8).length > MAX_BYTES) {
            return "Password is too long (maximum " + MAX_BYTES + " bytes).";
        }

        boolean hasLetter = false;
        boolean hasDigit = false;

        for (int i = 0; i < password.length(); i++) {
            char c = password.charAt(i);
            if (Character.isLetter(c)) hasLetter = true;
            if (Character.isDigit(c)) hasDigit = true;
        }

        if (!hasLetter || !hasDigit) {
            return describe();
        }

        String lower = password.toLowerCase(Locale.ROOT);

        if (COMMON.contains(lower)) {
            return "This password is too common. Please choose a less predictable one.";
        }

        if (username != null && username.trim().length() >= 3
                && lower.contains(username.trim().toLowerCase(Locale.ROOT))) {
            return "Password must not contain your username.";
        }

        return null;
    }
}
