package com.developers_group.biometricattendance.security;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

class RecoveryProofTest {

    private final Instant now = Instant.parse("2026-11-07T08:00:00Z");

    @Test
    void freshProofForSameUserIsAccepted() {
        var proof = new RecoveryProof.Proof("amina", now.minus(Duration.ofMinutes(5)));
        assertTrue(RecoveryProof.accepts(proof, "AMINA", now));
    }

    @Test
    void otherUserOldOrMissingProofIsRefused() {
        assertFalse(RecoveryProof.accepts(null, "amina", now));
        assertFalse(RecoveryProof.accepts(new RecoveryProof.Proof("amina", now), "zawadi", now));
        assertFalse(RecoveryProof.accepts(new RecoveryProof.Proof("amina", now.minus(Duration.ofMinutes(31))), "amina", now));
        assertFalse(RecoveryProof.accepts(new RecoveryProof.Proof("amina", now.plusSeconds(60)), "amina", now));
    }
}
