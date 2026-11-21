package com.developers_group.biometricattendance.security;

import com.developers_group.biometricattendance.exception.ApiException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;

import java.io.Serializable;
import java.time.Duration;
import java.time.Instant;

/**
 * Server-side proof that the employee JUST verified with the fingerprint / Face / screen lock
 * of their registered device.
 *
 * How it works:
 *  1. The browser completes the real WebAuthn ceremony (POST /login/webauthn). Spring Security
 *     checks the signature against the stored public key.
 *  2. FingerprintVerificationFilter sees that this request succeeded and stores a short-lived
 *     proof in the employee's HTTP session.
 *  3. Check-in and check-out refuse to run unless that proof is there, belongs to the same
 *     account and is fresh. A password alone never creates this proof.
 *
 * So a person who only knows someone's password cannot sign for them, not even by calling the
 * API directly.
 */
public final class FingerprintProof {

    public static final String MESSAGE =
            "Fingerprint verification is required. Please verify with your registered device and try again.";

    static final String ATTRIBUTE = "ATTENDANCE_FINGERPRINT_PROOF";

    /** How long one fingerprint check stays valid for ONE sign-in / sign-out. */
    static final Duration VALID_FOR = Duration.ofMinutes(2);

    /** Kept in the session, so it must be serializable. */
    record Proof(String username, Instant verifiedAt) implements Serializable {
    }

    private FingerprintProof() {
    }

    /** Called after a successful WebAuthn sign-in. */
    public static void mark(HttpServletRequest request, String username) {

        HttpSession session = request.getSession(false);

        if (session == null || username == null || username.isBlank()) return;

        session.setAttribute(ATTRIBUTE, new Proof(username, Instant.now()));
    }

    /** @throws ApiException 403 when there is no fresh fingerprint proof for this account. */
    public static void require(HttpServletRequest request, String username) {

        HttpSession session = request.getSession(false);

        Object stored = session == null ? null : session.getAttribute(ATTRIBUTE);

        Proof proof = stored instanceof Proof p ? p : null;

        if (!accepts(proof, username, Instant.now())) {
            throw ApiException.forbidden(MESSAGE);
        }
    }

    /** Each fingerprint check is used up by one successful attendance action. */
    public static void clear(HttpServletRequest request) {

        HttpSession session = request.getSession(false);

        if (session != null) session.removeAttribute(ATTRIBUTE);
    }

    // Visible for testing: the whole decision, without a servlet container.
    static boolean accepts(Proof proof, String username, Instant now) {

        if (proof == null || username == null || proof.username() == null || proof.verifiedAt() == null) {
            return false;
        }

        if (!proof.username().equalsIgnoreCase(username.trim())) return false;

        Duration age = Duration.between(proof.verifiedAt(), now);

        return !age.isNegative() && age.compareTo(VALID_FOR) <= 0;
    }
}
