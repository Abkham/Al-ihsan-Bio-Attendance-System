package com.developers_group.biometricattendance.repository;

import com.developers_group.biometricattendance.entity.PasswordResetToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Optional;

@Repository
public interface PasswordResetTokenRepository extends JpaRepository<PasswordResetToken, Long> {

    Optional<PasswordResetToken> findByTokenHash(String tokenHash);

    long countByAccountTypeAndAccountRefAndCreatedAtAfter(
            String accountType, String accountRef, Instant after);

    @Modifying
    @Query("delete from PasswordResetToken t where t.accountType = :type and t.accountRef = :ref")
    int deleteForAccount(@Param("type") String type, @Param("ref") String ref);

    @Modifying
    @Query("delete from PasswordResetToken t where t.expiresAt < :cutoff")
    int deleteExpiredBefore(@Param("cutoff") Instant cutoff);

    /**
     * Atomically consumes a token. Returns 1 only for the single caller that
     * wins; a second use, an expired token or a parallel request returns 0.
     */
    @Modifying
    @Query("update PasswordResetToken t set t.usedAt = :now "
            + "where t.id = :id and t.usedAt is null and t.expiresAt > :now")
    int markUsed(@Param("id") Long id, @Param("now") Instant now);
}
