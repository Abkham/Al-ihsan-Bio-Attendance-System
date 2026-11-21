package com.developers_group.biometricattendance.security;

import com.developers_group.biometricattendance.exception.ApiException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;

import java.io.Serializable;
import java.time.Duration;
import java.time.Instant;

/**
 * Session proof that the employee entered the verification code the administrator gave them
 * after an emergency device revocation. Without it a new device cannot be registered, so a
 * person who only knows the password (or who reported a "stolen" phone) cannot enrol their own phone.
 */
public final class RecoveryProof {

    public static final String MESSAGE =
            "Identity verification is required before a new device can be registered. "
                    + "Ask the administrator for your verification code and enter it in Settings > Device.";

    static final String ATTRIBUTE = "DEVICE_RECOVERY_PROOF";

    static final Duration VALID_FOR = Duration.ofMinutes(30);

    record Proof(String username, Instant verifiedAt) implements Serializable {
    }

    private RecoveryProof() {
    }

    public static void mark(HttpServletRequest request, String username) {

        HttpSession session = request.getSession(false);

        if (session == null || username == null || username.isBlank()) return;

        session.setAttribute(ATTRIBUTE, new Proof(username, Instant.now()));
    }

    public static boolean isValid(HttpServletRequest request, String username) {

        HttpSession session = request.getSession(false);

        Object stored = session == null ? null : session.getAttribute(ATTRIBUTE);

        return accepts(stored instanceof Proof p ? p : null, username, Instant.now());
    }

    public static void require(HttpServletRequest request, String username) {
        if (!isValid(request, username)) throw ApiException.forbidden(MESSAGE);
    }

    public static void clear(HttpServletRequest request) {

        HttpSession session = request.getSession(false);

        if (session != null) session.removeAttribute(ATTRIBUTE);
    }

    // Visible for testing
    static boolean accepts(Proof proof, String username, Instant now) {

        if (proof == null || username == null || proof.username() == null || proof.verifiedAt() == null) {
            return false;
        }

        if (!proof.username().equalsIgnoreCase(username.trim())) return false;

        Duration age = Duration.between(proof.verifiedAt(), now);

        return !age.isNegative() && age.compareTo(VALID_FOR) <= 0;
    }
}
