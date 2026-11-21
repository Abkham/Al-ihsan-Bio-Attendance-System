package com.developers_group.biometricattendance.security;

import java.util.regex.Pattern;

/** Helpers for telling a BCrypt hash from a legacy plain-text password. */
public final class PasswordHashing {

    // $2a$10$ + 53 chars (22 salt + 31 hash). Also accepts $2b$ and $2y$.
    private static final Pattern BCRYPT = Pattern.compile("^\\$2[aby]\\$\\d{2}\\$[./A-Za-z0-9]{53}$");

    private PasswordHashing() {
    }

    public static boolean isBcryptHash(String stored) {
        return stored != null && BCRYPT.matcher(stored).matches();
    }
}
