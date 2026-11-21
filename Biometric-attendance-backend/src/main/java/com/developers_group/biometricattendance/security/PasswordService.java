package com.developers_group.biometricattendance.security;

import com.developers_group.biometricattendance.exception.ApiException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * The one place that turns a typed password into a stored BCrypt hash and checks it again.
 * Plain-text values are never stored, compared or logged.
 */
@Component
public class PasswordService {

    private final PasswordEncoder encoder;

    // A real BCrypt hash used when the account does not exist, so "unknown user" and
    // "wrong password" take the same time (prevents account enumeration by timing).
    private final String dummyHash;

    public PasswordService(PasswordEncoder encoder) {
        this.encoder = encoder;
        this.dummyHash = encoder.encode("not-a-real-account-password-1");
    }

    /** Validates against the policy and returns the hash; throws 400 with a readable message. */
    public String hashNew(String rawPassword, String username) {

        String problem = PasswordPolicy.validate(rawPassword, username);

        if (problem != null) {
            throw ApiException.badRequest(problem);
        }

        return encoder.encode(rawPassword);
    }

    /** True only for a real BCrypt hash that matches. A legacy plain-text value never matches. */
    public boolean matches(String rawPassword, String storedHash) {

        if (rawPassword == null || storedHash == null || !PasswordHashing.isBcryptHash(storedHash)) {
            return false;
        }

        return encoder.matches(rawPassword, storedHash);
    }

    /** Burns the same CPU time as a real check (call when the account was not found). */
    public void spendTimeLikeARealCheck(String rawPassword) {
        encoder.matches(rawPassword == null ? "" : rawPassword, dummyHash);
    }
}
