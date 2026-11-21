package com.developers_group.biometricattendance.security;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class CodesTest {

    @Test
    void generatedCodeHasTwoGroupsOfFiveWithoutLookAlikes() {
        for (int i = 0; i < 200; i++) {
            String code = Codes.generate();
            assertTrue(code.matches("[A-HJKMNP-Z2-9]{5}-[A-HJKMNP-Z2-9]{5}"), code);
        }
    }

    @Test
    void typingStyleDoesNotMatter() {
        assertEquals("ABCDE12345", Codes.normalize("abcde-12345"));
        assertEquals("ABCDE12345", Codes.normalize(" abcde 12345 "));
        assertEquals("", Codes.normalize(null));
    }

    @Test
    void twoGeneratedCodesDiffer() {
        assertNotEquals(Codes.generate(), Codes.generate());
    }
}
