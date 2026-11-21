package com.developers_group.biometricattendance.service;

import com.developers_group.biometricattendance.exception.ApiException;

import java.util.regex.Pattern;

/** Validation shared by the employee and administrator forms. */
public final class NameRules {

    // letters (any language), combining marks, space, dot, apostrophe, hyphen; must start with a letter
    private static final Pattern PERSON_NAME = Pattern.compile("^\\p{L}[\\p{L}\\p{M} .'’-]{0,99}$");

    private static final Pattern USERNAME = Pattern.compile("^[A-Za-z0-9._-]{3,50}$");

    private static final Pattern EMAIL = Pattern.compile("^[^@\\s]{1,64}@[^@\\s]+\\.[^@\\s]{2,}$");

    private NameRules() {
    }

    public static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    /** Collapses inner spaces and trims. Returns null for blank input. */
    public static String tidy(String value) {
        if (isBlank(value)) return null;
        return value.trim().replaceAll("\\s+", " ");
    }

    public static String requiredName(String label, String value) {

        String tidy = tidy(value);

        if (tidy == null) {
            throw ApiException.badRequest(label + " is required");
        }

        if (!PERSON_NAME.matcher(tidy).matches()) {
            throw ApiException.badRequest(label + " may contain only letters, spaces, hyphens, apostrophes and dots (max 100).");
        }

        return tidy;
    }

    /** Optional name (middle name): blank becomes null, otherwise validated like the others. */
    public static String optionalName(String label, String value) {

        String tidy = tidy(value);

        if (tidy == null) return null;

        if (!PERSON_NAME.matcher(tidy).matches()) {
            throw ApiException.badRequest(label + " may contain only letters, spaces, hyphens, apostrophes and dots (max 100).");
        }

        return tidy;
    }

    public static String username(String value) {

        String tidy = value == null ? null : value.trim();

        if (tidy == null || !USERNAME.matcher(tidy).matches()) {
            throw ApiException.badRequest(
                    "Username must be 3-50 characters: letters, numbers, dot, dash or underscore (no spaces).");
        }

        return tidy;
    }

    public static String optionalEmail(String value) {

        String tidy = value == null ? null : value.trim();

        if (tidy == null || tidy.isEmpty()) return null;

        if (tidy.length() > 120 || !EMAIL.matcher(tidy).matches()) {
            throw ApiException.badRequest("Please enter a valid email address.");
        }

        return tidy;
    }
}
