package com.developers_group.biometricattendance.security;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PasswordPolicyTest {

    @Test
    void acceptsEightCharactersWithLetterAndDigit() {
        assertNull(PasswordPolicy.validate("Maji2026x", "amina"));
    }

    @Test
    void rejectsShortPasswords() {
        assertNotNull(PasswordPolicy.validate("ab1", "amina"));
        assertNotNull(PasswordPolicy.validate("abcde12", "amina")); // 7 characters
    }

    @Test
    void rejectsMissingLetterOrDigit() {
        assertNotNull(PasswordPolicy.validate("abcdefghij", "amina"));
        assertNotNull(PasswordPolicy.validate("1234567890", "amina"));
    }

    @Test
    void rejectsCommonPasswords() {
        assertNotNull(PasswordPolicy.validate("Password123", null)); // case-insensitive match
        assertNotNull(PasswordPolicy.validate("password123", null));
        assertNotNull(PasswordPolicy.validate("admin123", null));
    }

    @Test
    void rejectsPasswordContainingUsername() {
        String message = PasswordPolicy.validate("amina-2026x", "amina");
        assertNotNull(message);
        assertTrue(message.contains("username"));
    }

    @Test
    void rejectsPasswordsOverBcryptLimit() {
        assertNotNull(PasswordPolicy.validate("a1" + "x".repeat(80), "amina"));
    }

    @Test
    void rejectsNullAndEmpty() {
        assertNotNull(PasswordPolicy.validate(null, "amina"));
        assertNotNull(PasswordPolicy.validate("", "amina"));
    }
}
