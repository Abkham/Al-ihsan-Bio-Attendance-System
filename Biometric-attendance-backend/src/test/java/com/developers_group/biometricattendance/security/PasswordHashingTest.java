package com.developers_group.biometricattendance.security;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PasswordHashingTest {

    private static final String HASH = "$2a$10$" + "a".repeat(53);

    @Test
    void recognisesBcryptHash() {
        assertTrue(PasswordHashing.isBcryptHash("$2a$10$" + "a".repeat(53)));
        assertTrue(PasswordHashing.isBcryptHash("$2b$12$" + "A1./".repeat(13) + "A"));
    }

    @Test
    void plainTextIsNotAHash() {
        assertFalse(PasswordHashing.isBcryptHash("admin123"));
        assertFalse(PasswordHashing.isBcryptHash(""));
        assertFalse(PasswordHashing.isBcryptHash(null));
        assertFalse(PasswordHashing.isBcryptHash("$2a$10$tooShort"));
        assertFalse(PasswordHashing.isBcryptHash(HASH + "extra"));
    }
}
