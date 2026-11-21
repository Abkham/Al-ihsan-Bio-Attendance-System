package com.developers_group.biometricattendance.security;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;

/**
 * Password-reset token helpers.
 *
 * The raw token (256 random bits) is only ever sent to the user's e-mail
 * address. The database stores ONLY its SHA-256 hash, so a database leak does
 * not give an attacker usable reset links.
 */
public final class ResetTokens {

    private static final SecureRandom RANDOM = new SecureRandom();

    private ResetTokens() {
    }

    public static String newToken() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    public static String hash(String token) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(
                    digest.digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available", e);
        }
    }

    /** Cheap shape check so garbage input never reaches the database. */
    public static boolean looksValid(String token) {
        return token != null && token.length() == 43 && token.matches("[A-Za-z0-9_-]{43}");
    }
}
