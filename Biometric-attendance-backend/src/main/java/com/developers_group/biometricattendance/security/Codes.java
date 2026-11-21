package com.developers_group.biometricattendance.security;

import java.security.SecureRandom;

/**
 * One-time codes the administrator hands to an employee (account activation, device recovery).
 * Ten characters from an alphabet without look-alikes (no 0/O, 1/I/L) shown as XXXXX-XXXXX.
 * Only a BCrypt hash is ever stored; the clear code is shown once to the administrator.
 */
public final class Codes {

    private static final String ALPHABET = "ABCDEFGHJKMNPQRSTUVWXYZ23456789";
    private static final SecureRandom RANDOM = new SecureRandom();

    private Codes() {
    }

    public static String generate() {

        StringBuilder code = new StringBuilder(11);

        for (int i = 0; i < 10; i++) {
            if (i == 5) code.append('-');
            code.append(ALPHABET.charAt(RANDOM.nextInt(ALPHABET.length())));
        }

        return code.toString();
    }

    /** "abcde 12345", "ABCDE-12345" and "abcde12345" are the same code. */
    public static String normalize(String typed) {

        if (typed == null) return "";

        return typed.replaceAll("[^A-Za-z0-9]", "").toUpperCase(java.util.Locale.ROOT);
    }

    /** What is hashed and compared: the normalised form of the code. */
    public static String forHashing(String typedOrGenerated) {
        return normalize(typedOrGenerated);
    }
}
