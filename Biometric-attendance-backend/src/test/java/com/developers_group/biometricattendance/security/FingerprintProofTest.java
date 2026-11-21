package com.developers_group.biometricattendance.security;

import com.developers_group.biometricattendance.security.FingerprintProof.Proof;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FingerprintProofTest {

    private static final Instant NOW = Instant.parse("2026-11-04T09:00:00Z");

    @Test
    void freshProofForTheSameAccountIsAccepted() {
        Proof proof = new Proof("amina", NOW.minusSeconds(30));
        assertTrue(FingerprintProof.accepts(proof, "amina", NOW));
        assertTrue(FingerprintProof.accepts(proof, "  AMINA ", NOW)); // case / spaces do not matter
    }

    @Test
    void noProofMeansNoAttendance() {
        assertFalse(FingerprintProof.accepts(null, "amina", NOW));
    }

    @Test
    void proofOfAnotherAccountIsRefused() {
        Proof proof = new Proof("zawadi", NOW.minusSeconds(5));
        assertFalse(FingerprintProof.accepts(proof, "amina", NOW));
    }

    @Test
    void oldProofIsRefused() {
        assertFalse(FingerprintProof.accepts(new Proof("amina", NOW.minusSeconds(121)), "amina", NOW));
        assertTrue(FingerprintProof.accepts(new Proof("amina", NOW.minusSeconds(120)), "amina", NOW));
    }

    @Test
    void proofFromTheFutureIsRefused() {
        assertFalse(FingerprintProof.accepts(new Proof("amina", NOW.plusSeconds(10)), "amina", NOW));
    }
}
