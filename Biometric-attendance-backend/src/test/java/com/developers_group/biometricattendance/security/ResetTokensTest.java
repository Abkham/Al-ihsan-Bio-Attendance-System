package com.developers_group.biometricattendance.security;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ResetTokensTest {

    @Test
    void tokensAreUrlSafeAndUnique() {
        Set<String> seen = new HashSet<>();
        for (int i = 0; i < 500; i++) {
            String token = ResetTokens.newToken();
            assertTrue(ResetTokens.looksValid(token), token);
            assertTrue(seen.add(token));
        }
    }

    @Test
    void hashIsStableAndDoesNotRevealToken() {
        String token = ResetTokens.newToken();
        String hash = ResetTokens.hash(token);

        assertEquals(hash, ResetTokens.hash(token));
        assertEquals(64, hash.length());
        assertFalse(hash.contains(token));
        assertNotEquals(hash, ResetTokens.hash(ResetTokens.newToken()));
    }

    @Test
    void rejectsMalformedTokens() {
        assertFalse(ResetTokens.looksValid(null));
        assertFalse(ResetTokens.looksValid(""));
        assertFalse(ResetTokens.looksValid("short"));
        assertFalse(ResetTokens.looksValid("a".repeat(42) + "!"));
        assertFalse(ResetTokens.looksValid("a".repeat(44)));
    }
}
